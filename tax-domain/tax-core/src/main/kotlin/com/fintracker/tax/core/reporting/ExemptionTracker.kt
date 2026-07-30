package com.fintracker.tax.core.reporting

import com.fintracker.tax.core.matcher.AssetCategory
import com.fintracker.tax.core.model.MatchedLot
import com.fintracker.tax.core.model.TaxTerm
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
data class ExemptionStatus(
    val fiscalYear: String,
    val grossLtcg: String,
    val grossLtcl: String,
    val grossStcg: String,
    val grossStcl: String,
    val netStcg: String,
    val netLtcgBeforeExemption: String,
    val exemptionLimit: String = "125000.00",
    val exemptionUsed: String,
    val exemptionRemaining: String,
    val taxableLtcg: String
)

object ExemptionTracker {

    fun calculateExemptionStatus(matchedLots: List<MatchedLot>, fiscalYear: String): ExemptionStatus {
        val (startDate, endDate) = getFiscalYearBounds(fiscalYear)

        val stgLots = matchedLots.filter {
            it.taxTerm == TaxTerm.SHORT_TERM &&
            it.disposalDate >= startDate &&
            it.disposalDate <= endDate
        }

        // Section 112A exemption applies ONLY to equity assets
        val equityLtgLots = matchedLots.filter {
            it.taxTerm == TaxTerm.LONG_TERM &&
            it.assetCategory == AssetCategory.EQUITY &&
            it.disposalDate >= startDate &&
            it.disposalDate <= endDate
        }

        // Section 70 & 74 Statutory Losses
        val gST = stgLots.filter { it.realizedGain > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, m -> acc.add(m.realizedGain) }
        val lST = stgLots.filter { it.realizedGain < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, m -> acc.add(m.realizedGain.abs()) }

        val gLT = equityLtgLots.filter { it.realizedGain > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, m -> acc.add(m.realizedGain) }
        val lLT = equityLtgLots.filter { it.realizedGain < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, m -> acc.add(m.realizedGain.abs()) }

        // STCL offsets STCG first
        val netStcg = gST.subtract(lST).max(BigDecimal.ZERO)
        val remainingStcl = lST.subtract(gST).max(BigDecimal.ZERO)

        // LTCL offsets LTCG, remaining STCL offsets LTCG
        val netLtcgBeforeExemption = gLT.subtract(lLT).subtract(remainingStcl).max(BigDecimal.ZERO)

        val rules = com.fintracker.tax.core.rules.TaxRulesLoader.loadRules(fiscalYear)
        val exemptionLimit = rules.equityExemptionLimit
        val exemptionUsed = netLtcgBeforeExemption.min(exemptionLimit)
        val exemptionRemaining = exemptionLimit.subtract(exemptionUsed).max(BigDecimal.ZERO)
        val taxableLtcg = netLtcgBeforeExemption.subtract(exemptionUsed).max(BigDecimal.ZERO)

        fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

        return ExemptionStatus(
            fiscalYear = fiscalYear,
            grossLtcg = gLT.fmt(),
            grossLtcl = lLT.fmt(),
            grossStcg = gST.fmt(),
            grossStcl = lST.fmt(),
            netStcg = netStcg.fmt(),
            netLtcgBeforeExemption = netLtcgBeforeExemption.fmt(),
            exemptionLimit = exemptionLimit.fmt(),
            exemptionUsed = exemptionUsed.fmt(),
            exemptionRemaining = exemptionRemaining.fmt(),
            taxableLtcg = taxableLtcg.fmt()
        )
    }

    private fun getFiscalYearBounds(fiscalYear: String): Pair<LocalDate, LocalDate> {
        val parts = fiscalYear.split("-")
        val startYear = parts[0].trim().toIntOrNull() ?: 2026
        val endYear = if (parts.size > 1 && parts[1].trim().length == 2) {
            (startYear / 100) * 100 + parts[1].trim().toInt()
        } else {
            startYear + 1
        }
        return Pair(LocalDate(startYear, 4, 1), LocalDate(endYear, 3, 31))
    }
}
