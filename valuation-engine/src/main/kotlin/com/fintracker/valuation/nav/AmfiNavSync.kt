package com.fintracker.valuation.nav

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.net.URI
import java.io.BufferedReader

data class NavEntry(
    val schemeCode: String,
    val isin: String?,
    val schemeName: String,
    val nav: BigDecimal,
    val date: LocalDate
)

class AmfiNavSync {

    companion object {
        @Volatile
        private var cachedNavs: List<NavEntry>? = null
        @Volatile
        private var lastFetchTimeMs: Long = 0L
        private const val CACHE_TTL_MS = 6 * 3600 * 1000L // 6 Hours TTL
    }

    /**
     * Parses official AMFI EOD NAV text feed (Format: Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date)
     */
    fun parseAmfiFeed(feedContent: String): List<NavEntry> {
        val entries = mutableListOf<NavEntry>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        feedContent.lineSequence().forEach { line ->
            val parts = line.split(";")
            if (parts.size >= 6) {
                val schemeCode = parts[0].trim()
                val isinGrowth = parts[1].trim().ifEmpty { null }
                val schemeName = parts[3].trim()
                val navStr = parts[4].trim()

                try {
                    val nav = BigDecimal(navStr)
                    entries.add(
                        NavEntry(
                            schemeCode = schemeCode,
                            isin = isinGrowth,
                            schemeName = schemeName,
                            nav = nav,
                            date = today
                        )
                    )
                } catch (_: Exception) {
                    // Skip headers or invalid lines
                }
            }
        }
        return entries
    }

    fun fetchLatestNavsFromAmfi(): List<NavEntry> {
        val now = System.currentTimeMillis()
        val currentCache = cachedNavs

        if (currentCache != null && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
            return currentCache
        }

        return try {
            val url = URI.create("https://www.amfiindia.com/spages/NAVAll.txt").toURL()
            val content = url.openStream().bufferedReader().use(BufferedReader::readText)
            val parsed = parseAmfiFeed(content)

            if (parsed.isNotEmpty()) {
                cachedNavs = parsed
                lastFetchTimeMs = now
            }
            parsed
        } catch (e: Exception) {
            // Graceful degradation: Return cached entries if fetch fails, or empty list
            currentCache ?: emptyList()
        }
    }
}
