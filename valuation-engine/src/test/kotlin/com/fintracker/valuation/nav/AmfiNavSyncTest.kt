package com.fintracker.valuation.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AmfiNavSyncTest {

    @Test
    fun `test parsing amfi text feed`() {
        val sampleFeed = """
            Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date
            100033;INF200K01140;INF200K01157;ADITYA BIRLA SUN LIFE EQUITY SAVINGS FUND - DIRECT - GROWTH;58.4210;25-Jul-2026
            100034;INF200K01124;-;ADITYA BIRLA SUN LIFE EQUITY SAVINGS FUND - REGULAR - GROWTH;52.1930;25-Jul-2026
        """.trimIndent()

        val sync = AmfiNavSync()
        val entries = sync.parseAmfiFeed(sampleFeed)

        assertEquals(2, entries.size)
        assertEquals("100033", entries[0].schemeCode)
        assertEquals("INF200K01140", entries[0].isin)
        assertEquals(BigDecimal("58.4210"), entries[0].nav)
    }
}
