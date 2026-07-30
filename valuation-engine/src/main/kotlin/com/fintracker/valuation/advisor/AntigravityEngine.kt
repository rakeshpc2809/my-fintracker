package com.fintracker.valuation.advisor

import java.math.BigDecimal
import java.math.RoundingMode

data class AssetFactorScore(
    val assetId: String,
    val assetName: String,
    val beta: BigDecimal,
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
     * Calculates beta against benchmark return series Rm:
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

    fun analyzePortfolioFactors(
        assetReturnsMap: Map<String, List<Double>>,
        assetNamesMap: Map<String, String>,
        marketReturns: List<Double>,
        marketDrawdownPct: BigDecimal
    ): AntigravitySummary {
        val isCorrection = marketDrawdownPct >= BigDecimal("5.0")
        val scores = mutableListOf<AssetFactorScore>()

        assetReturnsMap.forEach { (assetId, returns) ->
            val beta = calculateBeta(returns, marketReturns)
            
            // Calculate 30d and 90d compounding TWR
            val twr30d = if (returns.isNotEmpty()) {
                returns.takeLast(30).fold(1.0) { acc, r -> acc * (1.0 + r) } - 1.0
            } else 0.0

            val twr90d = if (returns.isNotEmpty()) {
                returns.takeLast(90).fold(1.0) { acc, r -> acc * (1.0 + r) } - 1.0
            } else 0.0

            val twr30dBd = BigDecimal(twr30d * 100.0).setScale(2, RoundingMode.HALF_UP)
            val twr90dBd = BigDecimal(twr90d * 100.0).setScale(2, RoundingMode.HALF_UP)

            // Antigravity condition: Beta < 0.8 AND 30d TWR > 0% during drawdown >= 5%
            val isAntigravity = beta < BigDecimal("0.8") && twr30dBd > BigDecimal.ZERO && isCorrection

            val recommendation = when {
                isAntigravity -> "🚀 ANTIGRAVITY DETECTED — High momentum resilience during drawdown. Route SIP / liquid dry powder here."
                beta < BigDecimal("0.8") -> "Low-Beta Stabilizer — Good defense."
                twr30dBd > BigDecimal.ZERO -> "Positive Momentum."
                else -> "Standard Market Beta."
            }

            scores.add(
                AssetFactorScore(
                    assetId = assetId,
                    assetName = assetNamesMap[assetId] ?: assetId,
                    beta = beta,
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
