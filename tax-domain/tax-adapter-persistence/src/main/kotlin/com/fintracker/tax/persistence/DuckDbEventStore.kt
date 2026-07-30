package com.fintracker.tax.persistence

import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.model.TaxEvent
import com.fintracker.tax.core.ports.EventStorePort
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DuckDbEventStore(private val dbPath: String = System.getenv("DUCKDB_PATH") ?: "data/tax_ledger.duckdb") : EventStorePort {

    private val jdbcUrl: String
    private val connection: Connection
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
        connection = DriverManager.getConnection(jdbcUrl)
        initSchema()
    }

    private fun getConnection(): Connection {
        if (connection.isClosed) {
            return DriverManager.getConnection(jdbcUrl)
        }
        return connection
    }

    private fun initSchema() {
        val conn = getConnection()
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

    override fun getLatestEventHash(): String? {
        val conn = getConnection()
        val sql = "SELECT event_hash FROM tax_events ORDER BY ingested_at DESC, id DESC LIMIT 1"
        conn.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getString("event_hash") else "GENESIS"
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
        return appendEvents(listOf(event)).first()
    }

    override fun appendEvents(events: List<TaxEvent>): List<String> {
        if (events.isEmpty()) return emptyList()

        val conn = getConnection()
        val hashes = mutableListOf<String>()
        val wasAutoCommit = conn.autoCommit

        try {
            conn.autoCommit = false
            var prevHash = getLatestEventHash() ?: "GENESIS"

            val checkSql = "SELECT event_hash FROM tax_events WHERE asset_id = ? AND event_date = ? AND units = ? AND gross_amount = ? LIMIT 1"
            val checkStmt = conn.prepareStatement(checkSql)

            val insertSql = """
                INSERT INTO tax_events (
                    id, asset_id, asset_name, isin, event_type, event_date,
                    units, price_per_unit, gross_amount, source_document_id,
                    ingested_at, previous_hash, event_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            val insertStmt = conn.prepareStatement(insertSql)

            for (event in events) {
                checkStmt.setString(1, event.assetId)
                checkStmt.setString(2, event.eventDate.toString())
                checkStmt.setString(3, event.units.toPlainString())
                checkStmt.setString(4, event.grossAmount.toPlainString())
                val rs = checkStmt.executeQuery()

                if (rs.next()) {
                    hashes.add(rs.getString("event_hash"))
                    rs.close()
                    continue
                }
                rs.close()

                val eventHash = computeHash(prevHash, event)

                insertStmt.setString(1, event.id)
                insertStmt.setString(2, event.assetId)
                insertStmt.setString(3, event.assetName)
                insertStmt.setString(4, event.isin)
                insertStmt.setString(5, event.eventType.name)
                insertStmt.setString(6, event.eventDate.toString())
                insertStmt.setString(7, event.units.toPlainString())
                insertStmt.setString(8, event.pricePerUnit.toPlainString())
                insertStmt.setString(9, event.grossAmount.toPlainString())
                insertStmt.setString(10, event.sourceDocumentId)
                insertStmt.setString(11, event.ingestedAt.toString())
                insertStmt.setString(12, prevHash)
                insertStmt.setString(13, eventHash)
                insertStmt.executeUpdate()

                hashes.add(eventHash)
                prevHash = eventHash
            }

            checkStmt.close()
            insertStmt.close()

            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = wasAutoCommit
        }

        return hashes
    }

    override fun getEventsForAsset(assetId: String): List<TaxEvent> {
        val events = mutableListOf<TaxEvent>()
        val conn = getConnection()
        val sql = "SELECT * FROM tax_events WHERE asset_id = ? ORDER BY event_date ASC, ingested_at ASC"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, assetId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                events.add(mapResultSetToTaxEvent(rs))
            }
        }
        return events
    }

    override fun getAllEvents(): List<TaxEvent> {
        val events = mutableListOf<TaxEvent>()
        val conn = getConnection()
        val sql = "SELECT * FROM tax_events ORDER BY event_date ASC, ingested_at ASC"
        conn.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) {
                events.add(mapResultSetToTaxEvent(rs))
            }
        }
        return events
    }

    private fun computeHashFromRow(
        prevHash: String,
        id: String,
        assetId: String,
        eventType: String,
        eventDate: String,
        unitsStr: String,
        grossAmountStr: String,
        sourceDocumentId: String
    ): String {
        val raw = "$prevHash|$id|$assetId|$eventType|$eventDate|$unitsStr|$grossAmountStr|$sourceDocumentId"
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(hmacSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val bytes = mac.doFinal(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun verifyLedgerIntegrity(): Boolean {
        val conn = getConnection()
        val sql = "SELECT previous_hash, event_hash, id, asset_id, event_type, event_date, units, gross_amount, source_document_id FROM tax_events ORDER BY ingested_at ASC, id ASC"
        conn.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            var expectedPrevHash = "GENESIS"
            while (rs.next()) {
                val actualPrevHash = rs.getString("previous_hash")
                val actualEventHash = rs.getString("event_hash")

                if (actualPrevHash != expectedPrevHash) return false

                val recomputedHash = computeHashFromRow(
                    expectedPrevHash,
                    rs.getString("id"),
                    rs.getString("asset_id"),
                    rs.getString("event_type"),
                    rs.getString("event_date"),
                    rs.getBigDecimal("units").toPlainString(),
                    rs.getBigDecimal("gross_amount").toPlainString(),
                    rs.getString("source_document_id")
                )
                if (recomputedHash != actualEventHash) return false

                expectedPrevHash = actualEventHash
            }
        }
        return true
    }

    override fun clearAllEvents() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            stmt.execute("DELETE FROM tax_events")
        }
    }

    private fun mapResultSetToTaxEvent(rs: ResultSet): TaxEvent {
        return TaxEvent(
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
    }
}
