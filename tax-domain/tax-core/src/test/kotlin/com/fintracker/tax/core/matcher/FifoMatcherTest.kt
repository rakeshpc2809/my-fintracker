package com.fintracker.tax.core.matcher

import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.model.TaxEvent
import com.fintracker.tax.core.reconciliation.ReconciliationGate
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FifoMatcherTest {

    private val matcher = FifoMatcher()

    @Test
    fun `test fifo matching on acquisitions and disposals`() {
        val now = Instant.parse("2026-07-25T00:00:00Z")
        val events = listOf(
            TaxEvent(
                id = "1", assetId = "FUND_A", assetName = "Equity Fund A",
                eventType = EventType.ACQUISITION, eventDate = LocalDate.parse("2024-01-10"),
                units = BigDecimal("100.00"), pricePerUnit = BigDecimal("10.00"), grossAmount = BigDecimal("1000.00"),
                sourceDocumentId = "DOC_1", ingestedAt = now
            ),
            TaxEvent(
                id = "2", assetId = "FUND_A", assetName = "Equity Fund A",
                eventType = EventType.ACQUISITION, eventDate = LocalDate.parse("2024-06-10"),
                units = BigDecimal("50.00"), pricePerUnit = BigDecimal("12.00"), grossAmount = BigDecimal("600.00"),
                sourceDocumentId = "DOC_1", ingestedAt = now
            ),
            TaxEvent(
                id = "3", assetId = "FUND_A", assetName = "Equity Fund A",
                eventType = EventType.DISPOSAL, eventDate = LocalDate.parse("2025-02-15"),
                units = BigDecimal("120.00"), pricePerUnit = BigDecimal("15.00"), grossAmount = BigDecimal("1800.00"),
                sourceDocumentId = "DOC_2", ingestedAt = now
            )
        )

        val recon = ReconciliationGate.validateStatement(events, BigDecimal("30.00"))
        assertTrue(recon.isMatched, "Reconciliation gate should pass")

        val (openLots, matchedLots) = matcher.processEvents(events)

        assertEquals(1, openLots.size)
        assertEquals(BigDecimal("30.00"), openLots[0].remainingUnits)
        assertEquals(2, matchedLots.size)

        // First slice: 100 units from lot 1 (acquired 2024-01-10, disposed 2025-02-15 => >365 days => Long term)
        val slice1 = matchedLots[0]
        assertEquals(0, BigDecimal("100.00").compareTo(slice1.unitsMatched))
        assertEquals(0, BigDecimal("1000.00").compareTo(slice1.costBasis))
        assertEquals(0, BigDecimal("1500.00").compareTo(slice1.saleProceeds))
        assertEquals(0, BigDecimal("500.00").compareTo(slice1.realizedGain))

        // Second slice: 20 units from lot 2 (acquired 2024-06-10, disposed 2025-02-15 => <365 days => Short term)
        val slice2 = matchedLots[1]
        assertEquals(0, BigDecimal("20.00").compareTo(slice2.unitsMatched))
        assertEquals(0, BigDecimal("240.00").compareTo(slice2.costBasis))
        assertEquals(0, BigDecimal("300.00").compareTo(slice2.saleProceeds))
        assertEquals(0, BigDecimal("60.00").compareTo(slice2.realizedGain))
    }

    @Test
    fun `test bonus event handling`() {
        val now = Instant.parse("2026-07-25T00:00:00Z")
        val events = listOf(
            TaxEvent(
                id = "1", assetId = "STOCK_B", assetName = "Stock B",
                eventType = EventType.ACQUISITION, eventDate = LocalDate.parse("2024-01-01"),
                units = BigDecimal("10.00"), pricePerUnit = BigDecimal("100.00"), grossAmount = BigDecimal("1000.00"),
                sourceDocumentId = "DOC_1", ingestedAt = now
            ),
            TaxEvent(
                id = "2", assetId = "STOCK_B", assetName = "Stock B",
                eventType = EventType.BONUS, eventDate = LocalDate.parse("2024-07-01"),
                units = BigDecimal("10.00"), pricePerUnit = BigDecimal.ZERO, grossAmount = BigDecimal.ZERO,
                sourceDocumentId = "DOC_1", ingestedAt = now
            )
        )

        val (openLots, _) = matcher.processEvents(events)
        assertEquals(2, openLots.size)
        assertEquals(BigDecimal.ZERO, openLots[1].costPerUnit)
        assertEquals(LocalDate.parse("2024-07-01"), openLots[1].acquisitionDate)
    }

    @Test
    fun `test sgb maturity closes lot as exempt`() {
        val now = Instant.parse("2026-07-25T00:00:00Z")
        val events = listOf(
            TaxEvent(
                id = "1", assetId = "SGB_2016_I", assetName = "Sovereign Gold Bond 2016 Series I",
                eventType = EventType.ACQUISITION, eventDate = LocalDate.parse("2016-03-01"),
                units = BigDecimal("10.00"), pricePerUnit = BigDecimal("2600.00"), grossAmount = BigDecimal("26000.00"),
                sourceDocumentId = "DOC_1", ingestedAt = now
            ),
            TaxEvent(
                id = "2", assetId = "SGB_2016_I", assetName = "Sovereign Gold Bond 2016 Series I",
                eventType = EventType.SGB_MATURITY, eventDate = LocalDate.parse("2024-03-01"),
                units = BigDecimal("10.00"), pricePerUnit = BigDecimal("6200.00"), grossAmount = BigDecimal("62000.00"),
                sourceDocumentId = "DOC_2", ingestedAt = now
            )
        )

        val (openLots, matchedLots) = matcher.processEvents(events)
        assertEquals(0, openLots.size, "SGB maturity should close all open lots")
        assertEquals(1, matchedLots.size)
        assertEquals(com.fintracker.tax.core.model.TaxTerm.EXEMPT, matchedLots[0].taxTerm, "SGB maturity gain is tax exempt")
    }
}
