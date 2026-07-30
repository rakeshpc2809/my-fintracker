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
    fun `test schedule fa csv export does not apply fabricated 1 point 15 multiplier`() {
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

        assertTrue(csv.contains("COMPLIANCE DISCLAIMER"), "Csv should include compliance disclaimer header")
        assertTrue(csv.contains("10000.00,10000.00,10000.00"), "Peak value must strictly match recorded cost basis without dummy multiplication")
        assertFalse(csv.contains("11500.00"), "Must NOT contain fabricated 1.15 multiplied value")
    }
}
