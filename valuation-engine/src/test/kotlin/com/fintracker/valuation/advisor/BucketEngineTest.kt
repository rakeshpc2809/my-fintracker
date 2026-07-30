package com.fintracker.valuation.advisor

import com.fintracker.tax.core.model.Lot
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BucketEngineTest {

    @Test
    fun `test asset classification into 4 buckets`() {
        assertEquals(Bucket.EQUITY_SATELLITE, BucketEngine.classifyAssetToBucket("ISIN1", "Nippon India Small Cap Fund"))
        assertEquals(Bucket.LIQUID_BUFFER, BucketEngine.classifyAssetToBucket("ISIN2", "Kotak Arbitrage Fund Direct Growth"))
        assertEquals(Bucket.GOLD_SILVER, BucketEngine.classifyAssetToBucket("ISIN3", "Nippon India Silver ETF"))
        assertEquals(Bucket.EQUITY_CORE, BucketEngine.classifyAssetToBucket("ISIN4", "Parag Parikh Flexi Cap Fund"))
    }

    @Test
    fun `test market drawdown ladder trigger 15 percent down`() {
        val openLots = listOf(
            Lot(
                lotId = "1", assetId = "ARBITRAGE_1", assetName = "Kotak Arbitrage Fund",
                acquisitionDate = LocalDate.parse("2025-01-01"), originalUnits = BigDecimal("1000.0"),
                remainingUnits = BigDecimal("1000.0"), costPerUnit = BigDecimal("100.0"),
                totalCostBasis = BigDecimal("100000.00")
            )
        )
        val navMap = mapOf("ARBITRAGE_1" to BigDecimal("100.00"))

        val result = BucketEngine.evaluateRebalance(
            openLots = openLots,
            navMap = navMap,
            currentDate = LocalDate.parse("2026-07-28"),
            benchmarkCurrent = BigDecimal("21250.00"), // 15% down from 25000
            benchmarkRollingHigh = BigDecimal("25000.00")
        )

        assertTrue(result.drawdownTriggerFired)
        assertEquals(listOf(10, 15), result.drawdownStatus.activeRungsFired)
        assertEquals(BigDecimal("50.0"), result.drawdownStatus.recommendedBufferDeployPct)

        val drawdownRec = result.recommendations.first { it.triggerType == "MARKET_DRAWDOWN" }
        assertEquals("BUY", drawdownRec.action)
        assertEquals(0, BigDecimal("50000.00").compareTo(drawdownRec.amount))
    }

    @Test
    fun `test calendar trigger review date and drift detection`() {
        val openLots = listOf(
            Lot(
                lotId = "1", assetId = "CORE_1", assetName = "LargeMidcap 250 Index Fund",
                acquisitionDate = LocalDate.parse("2024-01-01"), originalUnits = BigDecimal("1000.0"),
                remainingUnits = BigDecimal("1000.0"), costPerUnit = BigDecimal("100.0"),
                totalCostBasis = BigDecimal("100000.00")
            )
        )
        val navMap = mapOf("CORE_1" to BigDecimal("100.00"))

        // March 15 review date: Core is 100% (target 50%), drift +50% > 5% band -> fires
        val result = BucketEngine.evaluateRebalance(
            openLots = openLots,
            navMap = navMap,
            currentDate = LocalDate.parse("2026-03-15"),
            benchmarkCurrent = BigDecimal("25000.00"),
            benchmarkRollingHigh = BigDecimal("25000.00")
        )

        assertTrue(result.calendarTriggerFired)
        assertTrue(result.recommendations.any { it.action == "SELL" && it.triggerType == "CALENDAR" })
    }
}
