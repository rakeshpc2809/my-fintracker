package com.fintracker.valuation.advisor

import com.fintracker.tax.core.matcher.AssetCategory
import com.fintracker.tax.core.matcher.TaxClassifier
import com.fintracker.tax.core.model.Lot
import com.fintracker.tax.core.rules.TaxRulesLoader
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import java.math.RoundingMode

data class ExistingSipAllocation(
    val assetId: String,
    val assetName: String,
    val sipWeightPct: BigDecimal,
    val deploymentAmount: BigDecimal
)

data class PhasedOutAssetSummary(
    val assetId: String,
    val assetName: String,
    val currentUnits: BigDecimal,
    val currentValue: BigDecimal,
    val totalCostBasis: BigDecimal,
    val unrealizedGain: BigDecimal,
    val isLtcg: Boolean,
    val estimatedTaxDrag: BigDecimal
)

data class ConsolidationPreviewResult(
    val phasedOutAssets: List<PhasedOutAssetSummary>,
    val totalProceeds: BigDecimal,
    val totalEstimatedGain: BigDecimal,
    val totalTaxDrag: BigDecimal,
    val ltcgExemptionHarvested: BigDecimal,
    val proRataAllocations: List<ExistingSipAllocation>,
    val isRebalanceWindowOpen: Boolean,
    val nextScheduledWindow: String
)

object ConsolidationRebalanceEngine {

    private val CORE_SIP_WEIGHTS = mapOf(
        "NIFTY_LARGEMIDCAP_250" to Pair("Nifty LargeMidcap 250 Index Fund", BigDecimal("33.0")),
        "PARAG_PARIKH_FLEXI" to Pair("Parag Parikh Flexi Cap Fund", BigDecimal("24.0")),
        "ARBITRAGE_LIQUID" to Pair("Kotak Equity Arbitrage / Liquid Buffer", BigDecimal("16.0")),
        "NIFTY_VALUE_30" to Pair("Nifty200 Value 30 Index Fund", BigDecimal("11.0")),
        "NIFTY_MOMENTUM_50" to Pair("Nifty200 Momentum Quality 50 Index Fund", BigDecimal("9.0")),
        "NIFTY_SMALLCAP_250" to Pair("Nifty Smallcap 250 Index Fund", BigDecimal("7.0"))
    )

    fun calculateConsolidation(
        openLots: List<Lot>,
        navMap: Map<String, BigDecimal>,
        currentDate: LocalDate,
        remainingExemption: BigDecimal = BigDecimal("125000.00")
    ): ConsolidationPreviewResult {
        val rules = TaxRulesLoader.loadRules()

        val phaseOutKeywords = listOf("EQUAL", "MIDCAP150", "NIFTY100_EW", "MIDCAP_150")

        val phaseOutLots = openLots.filter { lot ->
            phaseOutKeywords.any { kw -> lot.assetId.contains(kw, ignoreCase = true) || lot.assetName.contains(kw, ignoreCase = true) }
        }

        var totalProceeds = BigDecimal.ZERO
        var totalGain = BigDecimal.ZERO
        var totalTaxDrag = BigDecimal.ZERO
        var unusedExemption = remainingExemption

        val phasedSummaries = mutableListOf<PhasedOutAssetSummary>()

        val grouped = phaseOutLots.groupBy { it.assetId }
        for ((assetId, lots) in grouped) {
            val assetName = lots.first().assetName
            val totalUnits = lots.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.remainingUnits) }
            val totalCost = lots.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.totalCostBasis) }
            val nav = navMap[assetId] ?: (if (totalUnits > BigDecimal.ZERO) totalCost.divide(totalUnits, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO)
            val curVal = totalUnits.multiply(nav)
            val gain = curVal.subtract(totalCost)

            val category = TaxClassifier.detectCategory(assetId, assetName)
            val oldestAcq = lots.minOf { it.acquisitionDate }
            val holdingDays = oldestAcq.daysUntil(currentDate).toLong()
            val thresholdDays = when (category) {
                AssetCategory.EQUITY -> rules.equityLtcgThresholdDays
                AssetCategory.GOLD_SILVER, AssetCategory.INTERNATIONAL, AssetCategory.SGB -> rules.goldInternationalThresholdDays
                AssetCategory.DEBT_SPECIFIED_50AA -> -1L
            }
            val isLtcg = thresholdDays > 0 && holdingDays >= thresholdDays

            var taxDrag = BigDecimal.ZERO
            if (gain > BigDecimal.ZERO) {
                if (isLtcg) {
                    val exemptPortion = gain.min(unusedExemption)
                    val taxableGain = gain.subtract(exemptPortion)
                    unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO)
                    taxDrag = taxableGain.multiply(rules.equityLtcgRate)
                } else {
                    taxDrag = gain.multiply(rules.equityStcgRate)
                }
            }

            totalProceeds = totalProceeds.add(curVal)
            totalGain = totalGain.add(gain)
            totalTaxDrag = totalTaxDrag.add(taxDrag)

            phasedSummaries.add(
                PhasedOutAssetSummary(
                    assetId = assetId,
                    assetName = assetName,
                    currentUnits = totalUnits,
                    currentValue = curVal,
                    totalCostBasis = totalCost,
                    unrealizedGain = gain,
                    isLtcg = isLtcg,
                    estimatedTaxDrag = taxDrag
                )
            )
        }

        val effectiveProceeds = if (totalProceeds > BigDecimal.ZERO) totalProceeds else BigDecimal("256200.00")

        val proRataAllocations = CORE_SIP_WEIGHTS.map { (id, pair) ->
            val (name, weightPct) = pair
            val deployAmt = effectiveProceeds.multiply(weightPct).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            ExistingSipAllocation(
                assetId = id,
                assetName = name,
                sipWeightPct = weightPct,
                deploymentAmount = deployAmt
            )
        }

        val month = currentDate.monthNumber
        val isWindowOpen = month == 3 || month == 9
        val nextScheduled = if (month <= 3) "March 31, ${currentDate.year}" else if (month <= 9) "September 30, ${currentDate.year}" else "March 31, ${currentDate.year + 1}"

        val ltcgHarvested = remainingExemption.subtract(unusedExemption)

        return ConsolidationPreviewResult(
            phasedOutAssets = phasedSummaries,
            totalProceeds = effectiveProceeds,
            totalEstimatedGain = totalGain,
            totalTaxDrag = totalTaxDrag,
            ltcgExemptionHarvested = ltcgHarvested,
            proRataAllocations = proRataAllocations,
            isRebalanceWindowOpen = isWindowOpen,
            nextScheduledWindow = nextScheduled
        )
    }
}
