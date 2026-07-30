package com.fintracker.valuation.advisor

import com.fintracker.tax.core.matcher.AssetCategory
import com.fintracker.tax.core.matcher.TaxClassifier
import com.fintracker.tax.core.model.Lot
import com.fintracker.tax.core.rules.TaxRulesLoader
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

enum class Bucket {
    EQUITY_CORE,
    EQUITY_SATELLITE,
    GOLD_SILVER,
    LIQUID_BUFFER
}

data class BucketTarget(
    val bucket: Bucket,
    val targetPct: BigDecimal,
    val bandPct: BigDecimal = BigDecimal("5.0")
)

data class BucketStatus(
    val bucket: Bucket,
    val currentValue: BigDecimal,
    val currentPct: BigDecimal,
    val targetPct: BigDecimal,
    val driftPct: BigDecimal,
    val isDrifted: Boolean
)

data class RebalanceRecommendation(
    val assetId: String,
    val assetName: String,
    val bucket: Bucket,
    val action: String, // "BUY" or "SELL"
    val amount: BigDecimal,
    val triggerType: String, // "CALENDAR" or "MARKET_DRAWDOWN"
    val estimatedTaxDrag: BigDecimal = BigDecimal.ZERO,
    val taxTermSummary: String = ""
)

data class DrawdownStatus(
    val benchmarkName: String = "Nifty 500",
    val currentLevel: BigDecimal,
    val rollingHigh: BigDecimal,
    val drawdownPct: BigDecimal,
    val activeRungsFired: List<Int>,
    val recommendedBufferDeployPct: BigDecimal
)

data class RebalanceEngineResult(
    val bucketStatuses: List<BucketStatus>,
    val recommendations: List<RebalanceRecommendation>,
    val drawdownStatus: DrawdownStatus,
    val calendarTriggerFired: Boolean,
    val drawdownTriggerFired: Boolean
)

object BucketEngine {

    val DEFAULT_TARGETS = listOf(
        BucketTarget(Bucket.EQUITY_CORE, BigDecimal("50.0"), BigDecimal("5.0")),
        BucketTarget(Bucket.EQUITY_SATELLITE, BigDecimal("20.0"), BigDecimal("5.0")),
        BucketTarget(Bucket.GOLD_SILVER, BigDecimal("15.0"), BigDecimal("5.0")),
        BucketTarget(Bucket.LIQUID_BUFFER, BigDecimal("15.0"), BigDecimal("5.0"))
    )

    fun classifyAssetToBucket(assetId: String, assetName: String): Bucket {
        val nameUpper = assetName.uppercase()
        val category = TaxClassifier.detectCategory(assetId, assetName)

        if (category == AssetCategory.GOLD_SILVER || category == AssetCategory.SGB) {
            return Bucket.GOLD_SILVER
        }

        if (nameUpper.contains("ARBITRAGE") || nameUpper.contains("LIQUID") ||
            nameUpper.contains("OVERNIGHT") || nameUpper.contains("TREASURY") ||
            category == AssetCategory.DEBT_SPECIFIED_50AA
        ) {
            return Bucket.LIQUID_BUFFER
        }

        if (nameUpper.contains("SMALL") || nameUpper.contains("MICRO") || nameUpper.contains("SMALLCAP")) {
            return Bucket.EQUITY_SATELLITE
        }

        return Bucket.EQUITY_CORE
    }

    fun evaluateRebalance(
        openLots: List<Lot>,
        navMap: Map<String, BigDecimal>,
        currentDate: LocalDate,
        benchmarkCurrent: BigDecimal = BigDecimal("24000.00"),
        benchmarkRollingHigh: BigDecimal = BigDecimal("25000.00"),
        targets: List<BucketTarget> = DEFAULT_TARGETS
    ): RebalanceEngineResult {

        val totalPortfolioValue = openLots.fold(BigDecimal.ZERO) { acc, lot ->
            val nav = navMap[lot.assetId] ?: lot.costPerUnit
            acc.add(lot.remainingUnits.multiply(nav))
        }

        val bucketValues = mutableMapOf<Bucket, BigDecimal>()
        Bucket.values().forEach { bucketValues[it] = BigDecimal.ZERO }

        val bucketAssetLots = mutableMapOf<Bucket, MutableMap<String, MutableList<Lot>>>()
        Bucket.values().forEach { bucketAssetLots[it] = mutableMapOf() }

        for (lot in openLots) {
            val bucket = classifyAssetToBucket(lot.assetId, lot.assetName)
            val nav = navMap[lot.assetId] ?: lot.costPerUnit
            val lotValue = lot.remainingUnits.multiply(nav)
            bucketValues[bucket] = (bucketValues[bucket] ?: BigDecimal.ZERO).add(lotValue)

            val assetMap = bucketAssetLots[bucket]!!
            assetMap.computeIfAbsent(lot.assetId) { mutableListOf() }.add(lot)
        }

        val targetMap = targets.associateBy { it.bucket }
        val bucketStatuses = mutableListOf<BucketStatus>()
        var calendarTriggerFired = false

        val month = currentDate.monthNumber
        val day = currentDate.dayOfMonth
        val isCalendarReviewDate = (month == 3 && day >= 10 && day <= 20) || (month == 9 && day >= 10 && day <= 20)

        for (bucket in Bucket.values()) {
            val curVal = bucketValues[bucket] ?: BigDecimal.ZERO
            val curPct = if (totalPortfolioValue > BigDecimal.ZERO) {
                curVal.multiply(BigDecimal("100")).divide(totalPortfolioValue, 2, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            val tgt = targetMap[bucket] ?: BucketTarget(bucket, BigDecimal("25.0"))
            val drift = curPct.subtract(tgt.targetPct)
            val isDrifted = drift.abs() > tgt.bandPct

            if (isCalendarReviewDate && isDrifted) {
                calendarTriggerFired = true
            }

            bucketStatuses.add(
                BucketStatus(
                    bucket = bucket,
                    currentValue = curVal,
                    currentPct = curPct,
                    targetPct = tgt.targetPct,
                    driftPct = drift,
                    isDrifted = isDrifted
                )
            )
        }

        // Drawdown Trigger Logic
        val drawdownPct = if (benchmarkRollingHigh > BigDecimal.ZERO) {
            benchmarkRollingHigh.subtract(benchmarkCurrent)
                .multiply(BigDecimal("100"))
                .divide(benchmarkRollingHigh, 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val activeRungs = mutableListOf<Int>()
        var deployPct = BigDecimal.ZERO

        if (drawdownPct >= BigDecimal("20.0")) {
            activeRungs.addAll(listOf(10, 15, 20))
            deployPct = BigDecimal("100.0")
        } else if (drawdownPct >= BigDecimal("15.0")) {
            activeRungs.addAll(listOf(10, 15))
            deployPct = BigDecimal("50.0")
        } else if (drawdownPct >= BigDecimal("10.0")) {
            activeRungs.add(10)
            deployPct = BigDecimal("25.0")
        }

        val drawdownTriggerFired = activeRungs.isNotEmpty()
        val drawdownStatus = DrawdownStatus(
            benchmarkName = "Nifty 500",
            currentLevel = benchmarkCurrent,
            rollingHigh = benchmarkRollingHigh,
            drawdownPct = drawdownPct,
            activeRungsFired = activeRungs,
            recommendedBufferDeployPct = deployPct
        )

        // Shared Recommendations
        val recommendations = mutableListOf<RebalanceRecommendation>()
        val rules = TaxRulesLoader.loadRules()

        if (drawdownTriggerFired) {
            val liquidVal = bucketValues[Bucket.LIQUID_BUFFER] ?: BigDecimal.ZERO
            val deployAmount = liquidVal.multiply(deployPct).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)

            if (deployAmount > BigDecimal.ZERO) {
                val coreAssets = bucketAssetLots[Bucket.EQUITY_CORE]!!
                val targetAsset = if (coreAssets.isNotEmpty()) coreAssets.keys.first() else "EQUITY_CORE_INDEX"
                val assetName = if (coreAssets.isNotEmpty()) coreAssets[targetAsset]!!.first().assetName else "LargeMidcap 250 Index Fund"

                recommendations.add(
                    RebalanceRecommendation(
                        assetId = targetAsset,
                        assetName = assetName,
                        bucket = Bucket.EQUITY_CORE,
                        action = "BUY",
                        amount = deployAmount,
                        triggerType = "MARKET_DRAWDOWN",
                        estimatedTaxDrag = BigDecimal.ZERO,
                        taxTermSummary = "Deploy buffer during ${drawdownPct}% market drawdown (Rungs: ${activeRungs.joinToString("% ,")}% )"
                    )
                )
            }
        }

        for (status in bucketStatuses) {
            if (status.isDrifted) {
                val targetValue = totalPortfolioValue.multiply(status.targetPct).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
                val diffValue = status.currentValue.subtract(targetValue)

                if (diffValue > BigDecimal.ZERO) {
                    val bucketLots = bucketAssetLots[status.bucket]!!
                    val firstAssetId = bucketLots.keys.firstOrNull() ?: continue
                    val firstLots = bucketLots[firstAssetId]!!
                    val assetName = firstLots.first().assetName

                    var estTaxDrag = BigDecimal.ZERO
                    val taxTerms = mutableListOf<String>()
                    val nav = navMap[firstAssetId] ?: firstLots.first().costPerUnit

                    for (lot in firstLots) {
                        val category = TaxClassifier.detectCategory(lot.assetId, lot.assetName)
                        val isLtcg = TaxClassifier.classifyTaxTerm(category, 365) == com.fintracker.tax.core.model.TaxTerm.LONG_TERM
                        val gain = nav.subtract(lot.costPerUnit).multiply(lot.remainingUnits).max(BigDecimal.ZERO)

                        if (gain > BigDecimal.ZERO) {
                            val rate = if (isLtcg) rules.equityLtcgRate else rules.equityStcgRate
                            estTaxDrag = estTaxDrag.add(gain.multiply(rate))
                            taxTerms.add(if (isLtcg) "LTCG @ ${rules.equityLtcgRate.multiply(BigDecimal("100"))}%" else "STCG @ ${rules.equityStcgRate.multiply(BigDecimal("100"))}%")
                        }
                    }

                    recommendations.add(
                        RebalanceRecommendation(
                            assetId = firstAssetId,
                            assetName = assetName,
                            bucket = status.bucket,
                            action = "SELL",
                            amount = diffValue.abs(),
                            triggerType = if (isCalendarReviewDate) "CALENDAR" else "DRIFT_ALERT",
                            estimatedTaxDrag = estTaxDrag.setScale(2, RoundingMode.HALF_UP),
                            taxTermSummary = taxTerms.distinct().joinToString(", ")
                        )
                    )
                } else if (diffValue < BigDecimal.ZERO) {
                    val bucketLots = bucketAssetLots[status.bucket]!!
                    val firstAssetId = bucketLots.keys.firstOrNull() ?: "BUY_${status.bucket.name}"
                    val assetName = bucketLots[firstAssetId]?.firstOrNull()?.assetName ?: "Core Holding for ${status.bucket.name}"

                    recommendations.add(
                        RebalanceRecommendation(
                            assetId = firstAssetId,
                            assetName = assetName,
                            bucket = status.bucket,
                            action = "BUY",
                            amount = diffValue.abs(),
                            triggerType = if (isCalendarReviewDate) "CALENDAR" else "DRIFT_ALERT",
                            estimatedTaxDrag = BigDecimal.ZERO,
                            taxTermSummary = "No tax on purchases"
                        )
                    )
                }
            }
        }

        return RebalanceEngineResult(
            bucketStatuses = bucketStatuses,
            recommendations = recommendations,
            drawdownStatus = drawdownStatus,
            calendarTriggerFired = calendarTriggerFired,
            drawdownTriggerFired = drawdownTriggerFired
        )
    }
}
