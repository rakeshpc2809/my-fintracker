package com.fintracker.valuation.fire

import com.fintracker.tax.core.model.Lot
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FireTrackerTest {

    @Test
    fun `test FIRE tracker status and investable net worth calculation`() {
        val openLots = listOf(
            Lot(
                lotId = "1", assetId = "MF_1", assetName = "Parag Parikh Flexi Cap Fund",
                acquisitionDate = LocalDate.parse("2023-01-01"), originalUnits = BigDecimal("100.0"),
                remainingUnits = BigDecimal("100.0"), costPerUnit = BigDecimal("100.0"),
                totalCostBasis = BigDecimal("10000.00")
            )
        )
        val navMap = mapOf("MF_1" to BigDecimal("150.00")) // MF value = 15,000

        val profile = FireProfile(
            currentAge = 32,
            targetRetirementAge = 45,
            swrPercent = BigDecimal("3.0"),
            epfBalance = BigDecimal("228000.00"), // EPF excluded from FIRE investable
            nextReviewDate = LocalDate.parse("2026-01-01") // Review date passed flag true
        )

        val summary = FireTracker.calculateFireSummary(
            openLots = openLots,
            navMap = navMap,
            currentDate = LocalDate.parse("2026-07-28"),
            profile = profile,
            bankBalance = BigDecimal("425000.00")
        )

        // MF 15,000 + Bank 4,25,000 + EPF 2,28,000 = 6,68,000 total net worth
        assertEquals(BigDecimal("668000.00"), summary.totalNetWorth)
        assertEquals(13, summary.yearsRemaining)
        assertTrue(summary.reviewDatePassed)
        assertEquals(BigDecimal("24000000.00"), summary.requiredCorpus) // ₹60k/mo -> ₹7.2L/yr / 3% = 2.4 Cr
    }
}
