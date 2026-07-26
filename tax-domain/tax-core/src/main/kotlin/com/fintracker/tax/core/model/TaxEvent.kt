package com.fintracker.tax.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class TaxEvent(
    val id: String,
    val assetId: String,
    val assetName: String,
    val isin: String? = null,
    val eventType: EventType,
    val eventDate: LocalDate,
    val units: BigDecimal,
    val pricePerUnit: BigDecimal,
    val grossAmount: BigDecimal,
    val sourceDocumentId: String,
    val ingestedAt: Instant
) {
    /**
     * Signed unit delta model for reconciliation.
     * DISPOSAL reduces units (-units).
     * SGB_INTEREST is a cash event with zero unit effect (0).
     * All acquisition/adjustment events add units (+units).
     */
    fun unitDelta(): BigDecimal = when (eventType) {
        EventType.DISPOSAL -> -units
        EventType.SGB_INTEREST -> BigDecimal.ZERO
        else -> units
    }
}
