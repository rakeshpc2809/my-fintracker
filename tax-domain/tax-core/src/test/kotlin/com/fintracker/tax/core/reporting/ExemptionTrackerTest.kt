package com.fintracker.tax.core.reporting

import com.fintracker.tax.core.matcher.AssetCategory
import com.fintracker.tax.core.model.MatchedLot
import com.fintracker.tax.core.model.TaxTerm
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ExemptionTrackerTest {

    @Test
    fun `test Section 112A exemption applies strictly to equity long term gains`() {
        val matchedLots = listOf(
            MatchedLot(
                matchId = "m1", disposalEventId = "d1", lotId = "l1", assetId = "EQUITY_1",
                acquisitionDate = LocalDate.parse("2024-01-01"), disposalDate = LocalDate.parse("2026-05-01"),
                unitsMatched = BigDecimal("100"), costBasis = BigDecimal("10000.00"), saleProceeds = BigDecimal("60000.00"),
                realizedGain = BigDecimal("50000.00"), holdingPeriodDays = 486, taxTerm = TaxTerm.LONG_TERM,
                assetCategory = AssetCategory.EQUITY
            ),
            MatchedLot(
                matchId = "m2", disposalEventId = "d2", lotId = "l2", assetId = "GOLD_1",
                acquisitionDate = LocalDate.parse("2023-01-01"), disposalDate = LocalDate.parse("2026-05-01"),
                unitsMatched = BigDecimal("50"), costBasis = BigDecimal("20000.00"), saleProceeds = BigDecimal("50000.00"),
                realizedGain = BigDecimal("30000.00"), holdingPeriodDays = 1200, taxTerm = TaxTerm.LONG_TERM,
                assetCategory = AssetCategory.GOLD_SILVER
            )
        )

        val status = ExemptionTracker.calculateExemptionStatus(matchedLots, "2026-27")

        // Gross LTCG in exemption status should count ONLY the equity gain (50,000.00), excluding gold (30,000.00)
        assertEquals("50000.00", status.grossLtcg, "ExemptionTracker must only pool EQUITY LTCG for Section 112A")
        assertEquals("50000.00", status.exemptionUsed)
        assertEquals("75000.00", status.exemptionRemaining)
        assertEquals("0.00", status.taxableLtcg)
    }
}
