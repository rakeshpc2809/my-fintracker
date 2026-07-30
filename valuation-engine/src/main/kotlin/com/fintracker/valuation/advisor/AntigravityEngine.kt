package com.fintracker.valuation.advisor

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt

data class AssetFactorScore(
    val assetId: String,
    val assetName: String,
    val beta: BigDecimal,
    val downsideBeta: BigDecimal,
    val zScore30d: BigDecimal,
    val twr30dPct: BigDecimal,
    val twr90dPct: BigDecimal,
    val isAntigravity: Boolean,
    val recommendation: String
)

data class AntigravitySummary(
    val marketBenchmarkName: String = "Nifty 500 Index",
    val marketDrawdownPct: BigDecimal,
    val isMarketCorrection: Boolean,
    val antigravityAssets: List<AssetFactorScore>,
    val allAssetScores: List<AssetFactorScore>
)

object AntigravityEngine {

    /**
     * Calculates standard beta against benchmark return series Rm:
     * beta = Cov(Ri, Rm) / Var(Rm)
     */
    fun calculateBeta(assetReturns: List<Double>, marketReturns: List<Double>): BigDecimal {
        if (assetReturns.size < 2 || assetReturns.size != marketReturns.size) {
            return BigDecimal.ONE
        }
        val n = assetReturns.size
        val meanAsset = assetReturns.average()
        val meanMarket = marketReturns.average()

        var cov = 0.0
        var varMarket = 0.0

        for (i in 0 until n) {
            val devAsset = assetReturns[i] - meanAsset
            val devMarket = marketReturns[i] - meanMarket
            cov += devAsset * devMarket
            varMarket += devMarket * devMarket
        }

        if (varMarket == 0.0) return BigDecimal.ONE

        val betaVal = cov / varMarket
        return BigDecimal(betaVal).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Calculates downside beta strictly over days where market returns are negative:
     * betaDown = Cov(Ri, Rm | Rm < 0) / Var(Rm | Rm < 0)
     */
    fun calculateDownsideBeta(assetReturns: List<Double>, marketReturns: List<Double>): BigDecimal {
        if (assetReturns.size != marketReturns.size) return BigDecimal.ONE

        val downsidePairs = assetReturns.zip(marketReturns).filter { it.second < 0.0 }
        if (downsidePairs.size < 2) return calculateBeta(assetReturns, marketReturns)

        val meanAsset = downsidePairs.map { it.first }.average()
        val meanMarket = downsidePairs.map { it.second }.average()

        var cov = 0.0
        var varMarket = 0.0

        for ((rAsset, rMarket) in downsidePairs) {
            cov += (rAsset - meanAsset) * (rMarket - meanMarket)
            varMarket += (rMarket - meanMarket) * (rMarket - meanMarket)
        }

        if (varMarket == 0.0) return BigDecimal.ONE
        return BigDecimal(cov / varMarket).setScale(2, RoundingMode.HALF_UP)
    }

    fun analyzePortfolioFactors(
        assetReturnsMap: Map<String, List<Double>>,
        assetNamesMap: Map<String, String>,
        marketReturns: List<Double>,
        marketDrawdownPct: BigDecimal
    ): AntigravitySummary {
        val isCorrection = marketDrawdownPct >= BigDecimal("5.0")

        // Pre-compute 30d TWR for all assets to compute Z-Scores
        val twr30dMap = assetReturnsMap.mapValues { (_, returns) ->
            if (returns.isNotEmpty()) {
                returns.takeLast(30).fold(1.0) { acc, r -> acc * (1.0 + r) } - 1.0
            } else 0.0
        }

        val allTwr30d = twr30dMap.values.toList()
        val meanTwr30d = if (allTwr30d.isNotEmpty()) allTwr30d.average() else 0.0
        val stdDevTwr30d = if (allTwr30d.size > 1) {
            val variance = allTwr30d.sumOf { (it - meanTwr30d) * (it - meanTwr30d) } / (allTwr30d.size - 1)
            sqrt(variance)
        } else 1.0

        val scores = mutableListOf<AssetFactorScore>()

        assetReturnsMap.forEach { (assetId, returns) ->
            val beta = calculateBeta(returns, marketReturns)
            val downsideBeta = calculateDownsideBeta(returns, marketReturns)

            val twr30d = twr30dMap[assetId] ?: 0.0
            val zScore = if (stdDevTwr30d > 0.0001) (twr30d - meanTwr30d) / stdDevTwr30d else 0.0

            val twr90d = if (returns.isNotEmpty()) {
                returns.takeLast(90).fold(1.0) { acc, r -> acc * (1.0 + r) } - 1.0
            } else 0.0

            val twr30dBd = BigDecimal(twr30d * 100.0).setScale(2, RoundingMode.HALF_UP)
            val twr90dBd = BigDecimal(twr90d * 100.0).setScale(2, RoundingMode.HALF_UP)
            val zScoreBd = BigDecimal(zScore).setScale(2, RoundingMode.HALF_UP)

            // Enhanced Antigravity condition:
            // Downside Beta < 0.75 AND 30d TWR Z-Score > +0.50 AND Market Correction >= 5%
            val isAntigravity = downsideBeta < BigDecimal("0.75") && zScoreBd > BigDecimal("0.50") && isCorrection

            val recommendation = when {
                isAntigravity -> "🚀 QUANT ANTIGRAVITY — Downside beta ${downsideBeta} & Z-score +${zScoreBd}. Deploy dry powder here."
                downsideBeta < BigDecimal("0.75") -> "Downside Cushion — Beta-minus ${downsideBeta}."
                zScoreBd > BigDecimal("0.50") -> "Momentum Outperformer — Z-score +${zScoreBd}."
                else -> "Standard Market Beta."
            }

            scores.add(
                AssetFactorScore(
                    assetId = assetId,
                    assetName = assetNamesMap[assetId] ?: assetId,
                    beta = beta,
                    downsideBeta = downsideBeta,
                    zScore30d = zScoreBd,
                    twr30dPct = twr30dBd,
                    twr90dPct = twr90dBd,
                    isAntigravity = isAntigravity,
                    recommendation = recommendation
                )
            )
        }

        val antigravityList = scores.filter { it.isAntigravity }

        return AntigravitySummary(
            marketBenchmarkName = "Nifty 500 Index",
            marketDrawdownPct = marketDrawdownPct,
            isMarketCorrection = isCorrection,
            antigravityAssets = antigravityList,
            allAssetScores = scores
        )
    }
}
