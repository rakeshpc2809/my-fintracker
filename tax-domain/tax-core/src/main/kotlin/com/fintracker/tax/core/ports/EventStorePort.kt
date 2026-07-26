package com.fintracker.tax.core.ports

import com.fintracker.tax.core.model.TaxEvent

interface EventStorePort {
    fun appendEvent(event: TaxEvent): String // Returns SHA-256 hash of event
    fun appendEvents(events: List<TaxEvent>): List<String>
    fun getEventsForAsset(assetId: String): List<TaxEvent>
    fun getAllEvents(): List<TaxEvent>
    fun getLatestEventHash(): String?
    fun verifyLedgerIntegrity(): Boolean
    fun clearAllEvents()
}
