package com.fintracker.valuation.nav

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.net.URI
import java.io.BufferedReader
import java.io.InputStreamReader

data class NavEntry(
    val schemeCode: String,
    val isin: String?,
    val schemeName: String,
    val nav: BigDecimal,
    val date: LocalDate
)

class AmfiNavSync {

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
        return try {
            val url = URI.create("https://www.amfiindia.com/spages/NAVAll.txt").toURL()
            val content = url.openStream().bufferedReader().use(BufferedReader::readText)
            parseAmfiFeed(content)
        } catch (e: Exception) {
            // Graceful degradation: Return empty list if external feed is unreachable
            emptyList()
        }
    }
}
