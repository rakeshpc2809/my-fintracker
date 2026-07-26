package com.fintracker.tax.core.rules

import com.fintracker.tax.core.matcher.AssetCategory
import java.io.File
import java.math.BigDecimal

data class TaxRulesConfig(
    val fiscalYear: String = "2026-27",
    val equityLtcgThresholdDays: Long = 365L,
    val equityLtcgRate: BigDecimal = BigDecimal("0.125"),
    val equityStcgRate: BigDecimal = BigDecimal("0.20"),
    val equityExemptionLimit: BigDecimal = BigDecimal("125000"),
    val goldInternationalThresholdDays: Long = 730L,
    val goldInternationalLtcgRate: BigDecimal = BigDecimal("0.125"),
    val debtAlwaysShortTerm: Boolean = true
)

object TaxRulesLoader {

    private var cachedConfig: TaxRulesConfig? = null

    fun loadRules(fiscalYear: String = "2026-27"): TaxRulesConfig {
        if (cachedConfig != null && cachedConfig!!.fiscalYear == fiscalYear) {
            return cachedConfig!!
        }

        val fileLocations = listOf(
            File("rules/FY$fiscalYear.yaml"),
            File("/app/rules/FY$fiscalYear.yaml"),
            File("rules/FY2026-27.yaml"),
            File("/app/rules/FY2026-27.yaml")
        )

        val ruleFile = fileLocations.firstOrNull { it.exists() }
        if (ruleFile == null) {
            val defaultConfig = TaxRulesConfig(fiscalYear = fiscalYear)
            cachedConfig = defaultConfig
            return defaultConfig
        }

        try {
            val content = ruleFile.readText()
            var eqMonths = 12L
            var eqExemption = BigDecimal("125000")
            var eqLtcgRate = BigDecimal("0.125")
            var eqStcgRate = BigDecimal("0.20")
            var goldMonths = 24L

            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("annual_exemption:")) {
                    val valStr = trimmed.substringAfter("annual_exemption:").trim()
                    valStr.toBigDecimalOrNull()?.let { eqExemption = it }
                } else if (trimmed.startsWith("ltcg_rate:")) {
                    val valStr = trimmed.substringAfter("ltcg_rate:").trim()
                    valStr.toBigDecimalOrNull()?.let { eqLtcgRate = it }
                } else if (trimmed.startsWith("stcg_rate:")) {
                    val valStr = trimmed.substringAfter("stcg_rate:").trim()
                    valStr.toBigDecimalOrNull()?.let { eqStcgRate = it }
                } else if (trimmed.startsWith("ltcg_threshold_months:")) {
                    val valStr = trimmed.substringAfter("ltcg_threshold_months:").trim()
                    valStr.toLongOrNull()?.let {
                        if (eqMonths == 12L && it != 12L) goldMonths = it else eqMonths = it
                    }
                }
            }

            val config = TaxRulesConfig(
                fiscalYear = fiscalYear,
                equityLtcgThresholdDays = eqMonths * 30L, // ~365L
                equityLtcgRate = eqLtcgRate,
                equityStcgRate = eqStcgRate,
                equityExemptionLimit = eqExemption,
                goldInternationalThresholdDays = goldMonths * 30L, // ~730L
                goldInternationalLtcgRate = eqLtcgRate,
                debtAlwaysShortTerm = true
            )
            cachedConfig = config
            return config
        } catch (e: Exception) {
            val defaultConfig = TaxRulesConfig(fiscalYear = fiscalYear)
            cachedConfig = defaultConfig
            return defaultConfig
        }
    }
}
