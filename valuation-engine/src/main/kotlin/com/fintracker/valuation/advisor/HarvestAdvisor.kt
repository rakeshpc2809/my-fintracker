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

data class TaxHarvestRecommendation(
    val assetId: String,
    val assetName: String,
    val lotId: String,
    val unitsToHarvest: BigDecimal,
    val redemptionProceeds: BigDecimal,
    val unrealizedLtcgGain: BigDecimal,
    val exemptionHeadroomConsumed: BigDecimal,
    val recommendationText: String
)

data class TaxHarvestResult(
    val fiscalYear: String,
    val exemptionLimit: BigDecimal,
    val exemptionUsedSoFar: BigDecimal,
    val exemptionRemaining: BigDecimal,
    val totalUnrealizedLtcgAvailable: BigDecimal,
    val harvestableLtcgGain: BigDecimal,
    val recommendations: List<TaxHarvestRecommendation>
)

object HarvestAdvisor {

    fun generateHarvestPlan(
        openLots: List<Lot>,
        navMap: Map<String, BigDecimal>,
        exemptionUsedThisFy: BigDecimal = BigDecimal.ZERO,
        fiscalYear: String = "2026-27"
    ): TaxHarvestResult {
        val rules = TaxRulesLoader.loadRules()
        val limit = rules.equityExemptionLimit
        val remainingExemption = limit.subtract(exemptionUsedThisFy).coerceAtLeast(BigDecimal.ZERO)

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val ltcgLots = mutableListOf<Triple<Lot, BigDecimal, BigDecimal>>()

        var totalUnrealizedLtcg = BigDecimal.ZERO

        for (lot in openLots) {
            val category = TaxClassifier.detectCategory(lot.assetId, lot.assetName)
            if (category != AssetCategory.EQUITY) continue

            val holdingDays = lot.acquisitionDate.daysUntil(today).toLong()
            if (holdingDays >= rules.equityLtcgThresholdDays) {
                val nav = navMap[lot.assetId] ?: lot.costPerUnit
                val currentVal = lot.remainingUnits.multiply(nav)
                val gain = currentVal.subtract(lot.totalCostBasis)

                if (gain > BigDecimal.ZERO) {
                    totalUnrealizedLtcg = totalUnrealizedLtcg.add(gain)
                    ltcgLots.add(Triple(lot, nav, gain))
                }
            }
        }

        var headroomLeft = remainingExemption
        var totalHarvestedGain = BigDecimal.ZERO
        val recommendations = mutableListOf<TaxHarvestRecommendation>()

        for ((lot, nav, gain) in ltcgLots.sortedByDescending { it.third }) {
            if (headroomLeft.compareTo(BigDecimal.ZERO) <= 0) break

            val harvestableGain = gain.min(headroomLeft)
            val totalLotGain = gain

            val proportionToSell = if (totalLotGain > BigDecimal.ZERO) {
                harvestableGain.divide(totalLotGain, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE)
            } else BigDecimal.ONE

            val unitsToSell = lot.remainingUnits.multiply(proportionToSell).setScale(4, RoundingMode.HALF_UP)
            val proceeds = unitsToSell.multiply(nav).setScale(2, RoundingMode.HALF_UP)

            headroomLeft = headroomLeft.subtract(harvestableGain).coerceAtLeast(BigDecimal.ZERO)
            totalHarvestedGain = totalHarvestedGain.add(harvestableGain)

            recommendations.add(
                TaxHarvestRecommendation(
                    assetId = lot.assetId,
                    assetName = lot.assetName,
                    lotId = lot.lotId,
                    unitsToHarvest = unitsToSell,
                    redemptionProceeds = proceeds,
                    unrealizedLtcgGain = harvestableGain.setScale(2, RoundingMode.HALF_UP),
                    exemptionHeadroomConsumed = harvestableGain.setScale(2, RoundingMode.HALF_UP),
                    recommendationText = "Sell ${unitsToSell} units of ${lot.assetName} to harvest ₹${harvestableGain.setScale(0, RoundingMode.HALF_UP)} tax-free LTCG gain, then same-day rebuy."
                )
            )
        }

        return TaxHarvestResult(
            fiscalYear = fiscalYear,
            exemptionLimit = limit,
            exemptionUsedSoFar = exemptionUsedThisFy,
            exemptionRemaining = remainingExemption,
            totalUnrealizedLtcgAvailable = totalUnrealizedLtcg.setScale(2, RoundingMode.HALF_UP),
            harvestableLtcgGain = totalHarvestedGain.setScale(2, RoundingMode.HALF_UP),
            recommendations = recommendations
        )
    }
}
