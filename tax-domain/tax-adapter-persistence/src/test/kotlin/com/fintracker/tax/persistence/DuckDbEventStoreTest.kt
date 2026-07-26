package com.fintracker.tax.persistence

import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.model.TaxEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DuckDbEventStoreTest {

    @Test
    fun `test duckdb event persistence and sha-256 ledger integrity`() {
        val eventStore = DuckDbEventStore(":memory:")
        val now = Instant.parse("2026-07-25T12:00:00Z")

        val event1 = TaxEvent(
            id = "EVT-101", assetId = "INFY", assetName = "Infosys Limited",
            isin = "INE009A01021", eventType = EventType.ACQUISITION, eventDate = LocalDate.parse("2024-01-15"),
            units = BigDecimal("10.00"), pricePerUnit = BigDecimal("1500.00"), grossAmount = BigDecimal("15000.00"),
            sourceDocumentId = "ZERODHA-CSV-01", ingestedAt = now
        )

        val event2 = TaxEvent(
            id = "EVT-102", assetId = "INFY", assetName = "Infosys Limited",
            isin = "INE009A01021", eventType = EventType.DISPOSAL, eventDate = LocalDate.parse("2025-03-20"),
            units = BigDecimal("5.00"), pricePerUnit = BigDecimal("1800.00"), grossAmount = BigDecimal("9000.00"),
            sourceDocumentId = "ZERODHA-CSV-02", ingestedAt = now
        )

        val hash1 = eventStore.appendEvent(event1)
        val hash2 = eventStore.appendEvent(event2)

        assertNotNull(hash1)
        assertNotNull(hash2)
        assertNotEquals(hash1, hash2)

        val events = eventStore.getAllEvents()
        assertEquals(2, events.size)
        assertEquals("INFY", events[0].assetId)

        // Verify SHA-256 chain integrity
        val isIntegrityValid = eventStore.verifyLedgerIntegrity()
        assertTrue(isIntegrityValid, "DuckDB ledger SHA-256 hash chain should be valid")
    }
}
