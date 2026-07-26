package com.fintracker.tax.persistence

import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.model.TaxEvent
import com.fintracker.tax.core.ports.EventStorePort
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DuckDbEventStore(private val dbPath: String = System.getenv("DUCKDB_PATH") ?: "tax_ledger.duckdb") : EventStorePort {

    private val jdbcUrl: String
    private val hmacSecret: String = System.getenv("LEDGER_HMAC_SECRET") ?: "fintracker-cachyos-default-key-2026"

    init {
        Class.forName("org.duckdb.DuckDBDriver")
        if (dbPath == ":memory:") {
            jdbcUrl = "jdbc:duckdb:"
        } else {
            val file = java.io.File(dbPath)
            file.parentFile?.mkdirs()
            jdbcUrl = "jdbc:duckdb:${file.absolutePath}"
        }
        initSchema()
    }

    private fun getConnection(): Connection {
        return DriverManager.getConnection(jdbcUrl)
    }

    private fun initSchema() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS tax_events (
                        id VARCHAR PRIMARY KEY,
                        asset_id VARCHAR NOT NULL,
                        asset_name VARCHAR NOT NULL,
                        isin VARCHAR,
                        event_type VARCHAR NOT NULL,
                        event_date VARCHAR NOT NULL,
                        units VARCHAR NOT NULL,
                        price_per_unit VARCHAR NOT NULL,
                        gross_amount VARCHAR NOT NULL,
                        source_document_id VARCHAR NOT NULL,
                        ingested_at VARCHAR NOT NULL,
                        previous_hash VARCHAR NOT NULL,
                        event_hash VARCHAR NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    override fun getLatestEventHash(): String? {
        getConnection().use { conn ->
            val sql = "SELECT event_hash FROM tax_events ORDER BY ingested_at DESC, id DESC LIMIT 1"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getString("event_hash") else "GENESIS"
            }
        }
    }

    private fun computeHash(prevHash: String, event: TaxEvent): String {
        val raw = "$prevHash|${event.id}|${event.assetId}|${event.eventType}|${event.eventDate}|${event.units.toPlainString()}|${event.grossAmount.toPlainString()}|${event.sourceDocumentId}"
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(hmacSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val bytes = mac.doFinal(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun appendEvent(event: TaxEvent): String {
        val prevHash = getLatestEventHash() ?: "GENESIS"
        val eventHash = computeHash(prevHash, event)

        getConnection().use { conn ->
            val checkSql = "SELECT event_hash FROM tax_events WHERE asset_id = ? AND event_date = ? AND units = ? AND gross_amount = ? LIMIT 1"
            conn.prepareStatement(checkSql).use { stmt ->
                stmt.setString(1, event.assetId)
                stmt.setString(2, event.eventDate.toString())
                stmt.setString(3, event.units.toPlainString())
                stmt.setString(4, event.grossAmount.toPlainString())
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.getString("event_hash")
                }
            }

            val sql = """
                INSERT INTO tax_events (
                    id, asset_id, asset_name, isin, event_type, event_date,
                    units, price_per_unit, gross_amount, source_document_id,
                    ingested_at, previous_hash, event_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, event.id)
                stmt.setString(2, event.assetId)
                stmt.setString(3, event.assetName)
                stmt.setString(4, event.isin)
                stmt.setString(5, event.eventType.name)
                stmt.setString(6, event.eventDate.toString())
                stmt.setString(7, event.units.toPlainString())
                stmt.setString(8, event.pricePerUnit.toPlainString())
                stmt.setString(9, event.grossAmount.toPlainString())
                stmt.setString(10, event.sourceDocumentId)
                stmt.setString(11, event.ingestedAt.toString())
                stmt.setString(12, prevHash)
                stmt.setString(13, eventHash)
                stmt.executeUpdate()
            }
        }
        return eventHash
    }

    override fun appendEvents(events: List<TaxEvent>): List<String> {
        val hashes = mutableListOf<String>()
        for (event in events) {
            hashes.add(appendEvent(event))
        }
        return hashes
    }

    override fun getEventsForAsset(assetId: String): List<TaxEvent> {
        return getAllEvents().filter { it.assetId == assetId }
    }

    override fun getAllEvents(): List<TaxEvent> {
        val events = mutableListOf<TaxEvent>()
        getConnection().use { conn ->
            val sql = "SELECT * FROM tax_events ORDER BY event_date ASC, ingested_at ASC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    events.add(
                        TaxEvent(
                            id = rs.getString("id"),
                            assetId = rs.getString("asset_id"),
                            assetName = rs.getString("asset_name"),
                            isin = rs.getString("isin"),
                            eventType = EventType.valueOf(rs.getString("event_type")),
                            eventDate = LocalDate.parse(rs.getString("event_date")),
                            units = BigDecimal(rs.getString("units")),
                            pricePerUnit = BigDecimal(rs.getString("price_per_unit")),
                            grossAmount = BigDecimal(rs.getString("gross_amount")),
                            sourceDocumentId = rs.getString("source_document_id"),
                            ingestedAt = Instant.parse(rs.getString("ingested_at"))
                        )
                    )
                }
            }
        }
        return events
    }

    override fun verifyLedgerIntegrity(): Boolean {
        getConnection().use { conn ->
            val sql = "SELECT * FROM tax_events ORDER BY ingested_at ASC, id ASC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                var expectedPrevHash = "GENESIS"
                while (rs.next()) {
                    val actualPrevHash = rs.getString("previous_hash")
                    val actualEventHash = rs.getString("event_hash")

                    if (actualPrevHash != expectedPrevHash) return false

                    val event = TaxEvent(
                        id = rs.getString("id"),
                        assetId = rs.getString("asset_id"),
                        assetName = rs.getString("asset_name"),
                        isin = rs.getString("isin"),
                        eventType = EventType.valueOf(rs.getString("event_type")),
                        eventDate = LocalDate.parse(rs.getString("event_date")),
                        units = BigDecimal(rs.getString("units")),
                        pricePerUnit = BigDecimal(rs.getString("price_per_unit")),
                        grossAmount = BigDecimal(rs.getString("gross_amount")),
                        sourceDocumentId = rs.getString("source_document_id"),
                        ingestedAt = Instant.parse(rs.getString("ingested_at"))
                    )

                    val recomputedHash = computeHash(expectedPrevHash, event)
                    if (recomputedHash != actualEventHash) return false

                    expectedPrevHash = actualEventHash
                }
            }
        }
        return true
    }

    override fun clearAllEvents() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM tax_events")
            }
        }
    }
}
