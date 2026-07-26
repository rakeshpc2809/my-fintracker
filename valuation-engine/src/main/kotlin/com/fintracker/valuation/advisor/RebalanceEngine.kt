package com.fintracker.valuation.advisor

import com.fintracker.tax.core.matcher.AssetCategory
import com.fintracker.tax.core.matcher.TaxClassifier
import com.fintracker.tax.core.model.Lot
import com.fintracker.tax.core.rules.TaxRulesLoader
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.math.RoundingMode

data class RebalanceLotSelection(
    val lotId: String,
    val assetId: String,
    val assetName: String,
    val unitsToSell: BigDecimal,
    val redemptionProceeds: BigDecimal,
    val costBasis: BigDecimal,
    val estimatedGain: BigDecimal,
    val taxTerm: String,
    val estimatedTaxDrag: BigDecimal
)

data class RebalancePreviewResult(
    val targetRedemptionAmount: BigDecimal,
    val actualRedemptionAmount: BigDecimal,
    val totalEstimatedGain: BigDecimal,
    val totalTaxDrag: BigDecimal,
    val effectiveTaxRatePct: BigDecimal,
    val ltcgExemptionHarvested: BigDecimal,
    val selectedLots: List<RebalanceLotSelection>
)

object RebalanceEngine {

    fun calculateRebalancePreview(
        openLots: List<Lot>,
        navMap: Map<String, BigDecimal>,
        targetAmount: BigDecimal,
        remainingExemption: BigDecimal = BigDecimal("125000.00")
    ): RebalancePreviewResult {
        val rules = TaxRulesLoader.loadRules()
        var remainingTarget = targetAmount
        var unusedExemption = remainingExemption
        var totalGain = BigDecimal.ZERO
        var totalTaxDrag = BigDecimal.ZERO
        var actualRedemption = BigDecimal.ZERO

        val selected = mutableListOf<RebalanceLotSelection>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val sortedLots = openLots.sortedWith(
            compareBy<Lot> { lot ->
                val nav = navMap[lot.assetId] ?: lot.costPerUnit
                val gainPerUnit = nav.subtract(lot.costPerUnit)
                val category = TaxClassifier.detectCategory(lot.assetId, lot.assetName)
                val holdingDays = lot.acquisitionDate.daysUntil(today).toLong()
                val thresholdDays = when (category) {
                    AssetCategory.EQUITY -> rules.equityLtcgThresholdDays
                    AssetCategory.GOLD_SILVER, AssetCategory.INTERNATIONAL, AssetCategory.SGB -> rules.goldInternationalThresholdDays
                    AssetCategory.DEBT_SPECIFIED_50AA -> -1L
                }
                val isLtcg = thresholdDays > 0 && holdingDays >= thresholdDays

                if (gainPerUnit < BigDecimal.ZERO) 0
                else if (isLtcg) 1
                else 2
            }
        )

        for (lot in sortedLots) {
            if (remainingTarget <= BigDecimal.ZERO) break

            val nav = navMap[lot.assetId] ?: lot.costPerUnit
            val lotValue = lot.remainingUnits.multiply(nav)
            val redemptionFromLot = lotValue.min(remainingTarget)

            val unitsToSell = if (nav > BigDecimal.ZERO) redemptionFromLot.divide(nav, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
            val costBasisSlice = unitsToSell.multiply(lot.costPerUnit)
            val gainSlice = redemptionFromLot.subtract(costBasisSlice)

            val category = TaxClassifier.detectCategory(lot.assetId, lot.assetName)
            val holdingDays = lot.acquisitionDate.daysUntil(today).toLong()
            val thresholdDays = when (category) {
                AssetCategory.EQUITY -> rules.equityLtcgThresholdDays
                AssetCategory.GOLD_SILVER, AssetCategory.INTERNATIONAL, AssetCategory.SGB -> rules.goldInternationalThresholdDays
                AssetCategory.DEBT_SPECIFIED_50AA -> -1L
            }
            val isLtcg = thresholdDays > 0 && holdingDays >= thresholdDays

            var taxDrag = BigDecimal.ZERO
            if (gainSlice > BigDecimal.ZERO) {
                if (isLtcg) {
                    val exemptPortion = gainSlice.min(unusedExemption)
                    val taxableGain = gainSlice.subtract(exemptPortion)
                    unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO)
                    taxDrag = taxableGain.multiply(rules.equityLtcgRate)
                } else {
                    val stcgRate = when (category) {
                        AssetCategory.EQUITY -> rules.equityStcgRate
                        else -> BigDecimal("0.30") // Slab rate default estimation
                    }
                    taxDrag = gainSlice.multiply(stcgRate)
                }
            }

            selected.add(
                RebalanceLotSelection(
                    lotId = lot.lotId,
                    assetId = lot.assetId,
                    assetName = lot.assetName,
                    unitsToSell = unitsToSell,
                    redemptionProceeds = redemptionFromLot,
                    costBasis = costBasisSlice,
                    estimatedGain = gainSlice,
                    taxTerm = if (isLtcg) "LONG_TERM" else "SHORT_TERM",
                    estimatedTaxDrag = taxDrag
                )
            )

            actualRedemption = actualRedemption.add(redemptionFromLot)
            totalGain = totalGain.add(gainSlice)
            totalTaxDrag = totalTaxDrag.add(taxDrag)
            remainingTarget = remainingTarget.subtract(redemptionFromLot)
        }

        val ltcgHarvested = remainingExemption.subtract(unusedExemption)
        val effTaxRate = if (actualRedemption > BigDecimal.ZERO) {
            totalTaxDrag.multiply(BigDecimal("100")).divide(actualRedemption, 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return RebalancePreviewResult(
            targetRedemptionAmount = targetAmount,
            actualRedemptionAmount = actualRedemption,
            totalEstimatedGain = totalGain,
            totalTaxDrag = totalTaxDrag,
            effectiveTaxRatePct = effTaxRate,
            ltcgExemptionHarvested = ltcgHarvested,
            selectedLots = selected
        )
    }
}
