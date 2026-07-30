package com.fintracker.tax.core.reporting

import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.model.TaxEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class Itr2CsvExporterTest {

    @Test
    fun `test schedule fa csv export explicitly flags peak value for broker verification`() {
        val now = Instant.parse("2026-07-25T00:00:00Z")
        val events = listOf(
            TaxEvent(
                id = "e1", assetId = "MAHKTECH", assetName = "Mirae Asset Hang Seng TECH ETF",
                eventType = EventType.ACQUISITION, eventDate = LocalDate.parse("2025-01-10"),
                units = BigDecimal("100.00"), pricePerUnit = BigDecimal("100.00"), grossAmount = BigDecimal("10000.00"),
                sourceDocumentId = "DOC_1", ingestedAt = now
            )
        )

        val csv = Itr2CsvExporter.generateScheduleFaCsv(events)

        assertTrue(csv.contains("Peak Value INR (Requires Statement Verification)"), "CSV should include explicit verification requirement header")
        assertTrue(csv.contains("\"VERIFY_PEAK_NAV\""), "Peak value column must be explicitly flagged for intra-year peak NAV verification")
        assertFalse(csv.contains("11500.00"), "Must NOT contain arbitrary 1.15 multiplied value")
    }
}
