package com.fintracker.tax.core.reporting

import com.fintracker.tax.core.model.MatchedLot
import com.fintracker.tax.core.model.TaxTerm
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TaxReportExporterTest {

    @Test
    fun `test itr2 report generation and exemption deduction`() {
        val matchedLots = listOf(
            MatchedLot(
                matchId = "M1", disposalEventId = "D1", lotId = "L1", assetId = "STOCK_A",
                acquisitionDate = LocalDate.parse("2023-01-01"), disposalDate = LocalDate.parse("2026-05-10"),
                unitsMatched = BigDecimal("100"), costBasis = BigDecimal("50000.00"),
                saleProceeds = BigDecimal("200000.00"), realizedGain = BigDecimal("150000.00"),
                holdingPeriodDays = 1225, taxTerm = TaxTerm.LONG_TERM
            ),
            MatchedLot(
                matchId = "M2", disposalEventId = "D2", lotId = "L2", assetId = "STOCK_B",
                acquisitionDate = LocalDate.parse("2026-01-01"), disposalDate = LocalDate.parse("2026-06-10"),
                unitsMatched = BigDecimal("50"), costBasis = BigDecimal("20000.00"),
                saleProceeds = BigDecimal("30000.00"), realizedGain = BigDecimal("10000.00"),
                holdingPeriodDays = 160, taxTerm = TaxTerm.SHORT_TERM
            )
        )

        val report = TaxReportExporter.generateItr2Report(matchedLots, "2026-27")

        assertEquals("10000.00", report.totalRealizedStcg)
        assertEquals("150000.00", report.totalRealizedLtcg)
        assertEquals("125000.00", report.ltcgExemptionUsed)
        assertEquals("25000.00", report.netTaxableLtcg)
    }
}
