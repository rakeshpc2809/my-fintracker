package com.fintracker.tax.core.matcher

import com.fintracker.tax.core.model.TaxTerm

enum class AssetCategory {
    EQUITY,
    DEBT_SPECIFIED_50AA,
    GOLD_SILVER,
    INTERNATIONAL,
    SGB
}

object TaxClassifier {

    fun detectCategory(assetId: String, assetName: String): AssetCategory {
        val nameUpper = assetName.uppercase()
        val idUpper = assetId.uppercase()

        if (nameUpper.contains("SGB") || nameUpper.contains("SOVEREIGN GOLD")) {
            return AssetCategory.SGB
        }

        if (nameUpper.contains("GILT") || nameUpper.contains("BOND") || nameUpper.contains("DEBT") ||
            nameUpper.contains("LIQUID") || nameUpper.contains("OVERNIGHT") || nameUpper.contains("TREASURY")
        ) {
            return AssetCategory.DEBT_SPECIFIED_50AA
        }

        if (nameUpper.contains("GOLD") || nameUpper.contains("SILVER")) {
            return AssetCategory.GOLD_SILVER
        }

        if (nameUpper.contains("NASDAQ") || nameUpper.contains("S&P") || nameUpper.contains("INTERNATIONAL") ||
            nameUpper.contains("GLOBAL") || nameUpper.contains("US EQUITIES")
        ) {
            return AssetCategory.INTERNATIONAL
        }

        return AssetCategory.EQUITY
    }

    fun classifyTaxTerm(category: AssetCategory, holdingDays: Long): TaxTerm {
        return when (category) {
            AssetCategory.DEBT_SPECIFIED_50AA -> TaxTerm.SHORT_TERM // Section 50AA: Always Short-Term
            AssetCategory.GOLD_SILVER, AssetCategory.INTERNATIONAL -> {
                if (holdingDays >= 730) TaxTerm.LONG_TERM else TaxTerm.SHORT_TERM // 24 Months
            }
            AssetCategory.SGB -> {
                if (holdingDays >= 730) TaxTerm.LONG_TERM else TaxTerm.SHORT_TERM // 24 Months / Tax-free on 8y maturity
            }
            AssetCategory.EQUITY -> {
                if (holdingDays >= 365) TaxTerm.LONG_TERM else TaxTerm.SHORT_TERM // 12 Months
            }
        }
    }
}
