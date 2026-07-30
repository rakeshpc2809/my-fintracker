package com.fintracker.tax.core.matcher

import com.fintracker.tax.core.model.TaxTerm
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TaxClassifierTest {

    @Test
    fun `test explicit ISIN registry detection taking precedence over keywords`() {
        // Register a custom ISIN to EQUITY even if its name contains "BOND"
        TaxClassifier.registerAssetCategory("INF123456789", AssetCategory.EQUITY)

        val category = TaxClassifier.detectCategory("INF123456789", "Special Bond Equity Hybrid")
        assertEquals(AssetCategory.EQUITY, category, "Explicit ISIN registry lookup must take precedence")
    }

    @Test
    fun `test pre-registered international ISINs`() {
        val cat1 = TaxClassifier.detectCategory("MAHKTECH", "Mirae Asset Hang Seng TECH ETF")
        assertEquals(AssetCategory.INTERNATIONAL, cat1)

        val cat2 = TaxClassifier.detectCategory("INF109KA1VY6", "Parag Parikh US Equity FoF")
        assertEquals(AssetCategory.INTERNATIONAL, cat2)
    }

    @Test
    fun `test keyword fallback detection`() {
        val gold = TaxClassifier.detectCategory("UNKNOWN_ID", "Nippon India Gold ETF")
        assertEquals(AssetCategory.GOLD_SILVER, gold)

        val sgb = TaxClassifier.detectCategory("UNKNOWN_SGB", "Sovereign Gold Bond 2016 Series I")
        assertEquals(AssetCategory.SGB, sgb)

        val debt = TaxClassifier.detectCategory("UNKNOWN_DEBT", "HDFC Liquid Direct Fund")
        assertEquals(AssetCategory.DEBT_SPECIFIED_50AA, debt)
    }

    @Test
    fun `test Finance Act 2024 listed vs unlisted ETF threshold classification`() {
        // Listed International ETF: 12-month (365d) threshold
        val listedTerm370 = TaxClassifier.classifyTaxTerm(AssetCategory.INTERNATIONAL, 370L, isListed = true)
        assertEquals(TaxTerm.LONG_TERM, listedTerm370)

        // Unlisted International FoF: 24-month (730d) threshold
        val unlistedTerm370 = TaxClassifier.classifyTaxTerm(AssetCategory.INTERNATIONAL, 370L, isListed = false)
        assertEquals(TaxTerm.SHORT_TERM, unlistedTerm370, "Unlisted FoF held <730 days should be SHORT_TERM")

        val unlistedTerm740 = TaxClassifier.classifyTaxTerm(AssetCategory.INTERNATIONAL, 740L, isListed = false)
        assertEquals(TaxTerm.LONG_TERM, unlistedTerm740)
    }
}
