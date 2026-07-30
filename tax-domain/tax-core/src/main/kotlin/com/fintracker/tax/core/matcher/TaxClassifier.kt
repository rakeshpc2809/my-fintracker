package com.fintracker.tax.core.matcher

import com.fintracker.tax.core.model.TaxTerm
import java.util.concurrent.ConcurrentHashMap

enum class AssetCategory {
    EQUITY,
    DEBT_SPECIFIED_50AA,
    GOLD_SILVER,
    INTERNATIONAL,
    SGB
}

object TaxClassifier {

    private val isinCategoryRegistry = ConcurrentHashMap<String, AssetCategory>().apply {
        // Pre-registered ISINs and Ticker Symbols
        put("MAHKTECH", AssetCategory.INTERNATIONAL)
        put("MON100", AssetCategory.INTERNATIONAL)
        put("MASPTOP50", AssetCategory.INTERNATIONAL)
        put("INF109KA1VY6", AssetCategory.INTERNATIONAL)
        put("INF247L01793", AssetCategory.INTERNATIONAL)
        put("GOLDBEES", AssetCategory.GOLD_SILVER)
        put("SILVERBEES", AssetCategory.GOLD_SILVER)
    }

    private val sgbRegex = Regex("(?:SGB|SOVEREIGN GOLD)")
    private val debtRegex = Regex("(?:GILT|BOND|DEBT|LIQUID|OVERNIGHT|TREASURY)")
    private val goldSilverRegex = Regex("(?:GOLD|SILVER)")
    private val intlRegex = Regex("(?:NASDAQ|S&P|INTERNATIONAL|GLOBAL|US EQUITIES|MAHKTECH|HANG SENG|MON100|MASPTOP50|ASIA|EMERGING|CHINA)")
    private val listedRegex = Regex("(?:ETF|BEES|MON100|MASPTOP50|MAHKTECH|NIFTY|SENSEX)")

    fun registerAssetCategory(isinOrAssetId: String, category: AssetCategory) {
        isinCategoryRegistry[isinOrAssetId.uppercase()] = category
    }

    fun registerAssetCategories(mappings: Map<String, AssetCategory>) {
        mappings.forEach { (key, cat) -> isinCategoryRegistry[key.uppercase()] = cat }
    }

    fun detectCategory(assetId: String, assetName: String): AssetCategory {
        val idUpper = assetId.uppercase()
        val nameUpper = assetName.uppercase()

        // 1. Primary path: Explicit ISIN / Asset ID registry lookup
        isinCategoryRegistry[idUpper]?.let { return it }
        isinCategoryRegistry[nameUpper]?.let { return it }

        // 2. Secondary fallback path: Compiled Regex heuristics
        if (sgbRegex.containsMatchIn(nameUpper)) return AssetCategory.SGB
        if (debtRegex.containsMatchIn(nameUpper)) return AssetCategory.DEBT_SPECIFIED_50AA
        if (goldSilverRegex.containsMatchIn(nameUpper)) return AssetCategory.GOLD_SILVER
        if (intlRegex.containsMatchIn(nameUpper)) return AssetCategory.INTERNATIONAL

        return AssetCategory.EQUITY
    }

    fun isListed(assetId: String, assetName: String): Boolean {
        val upper = "$assetId $assetName".uppercase()
        return listedRegex.containsMatchIn(upper)
    }

    fun classifyTaxTerm(
        category: AssetCategory,
        holdingDays: Long,
        fiscalYear: String = "2026-27",
        isListed: Boolean = true
    ): TaxTerm {
        val rules = com.fintracker.tax.core.rules.TaxRulesLoader.loadRules(fiscalYear)
        return when (category) {
            AssetCategory.DEBT_SPECIFIED_50AA -> TaxTerm.SHORT_TERM // Section 50AA: Always Short-Term
            AssetCategory.EQUITY -> {
                if (holdingDays >= rules.equityLtcgThresholdDays) TaxTerm.LONG_TERM else TaxTerm.SHORT_TERM
            }
            AssetCategory.GOLD_SILVER, AssetCategory.INTERNATIONAL -> {
                // Per Finance Act 2024: Listed ETFs get 12-month (365d) threshold; unlisted FoFs get 24-month (730d) threshold
                val threshold = if (isListed) rules.equityLtcgThresholdDays else rules.goldInternationalThresholdDays
                if (holdingDays >= threshold) TaxTerm.LONG_TERM else TaxTerm.SHORT_TERM
            }
            AssetCategory.SGB -> {
                if (holdingDays >= rules.goldInternationalThresholdDays) TaxTerm.LONG_TERM else TaxTerm.SHORT_TERM
            }
        }
    }
}
