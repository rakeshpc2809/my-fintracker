package com.fintracker.tax.core.reporting

import com.fintracker.tax.core.model.MatchedLot
import com.fintracker.tax.core.model.TaxTerm
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class ExemptionStatus(
    val fiscalYear: String,
    val totalEquityLtcgRealized: String,
    val exemptionLimit: String = "125000.00",
    val exemptionUsed: String,
    val exemptionRemaining: String,
    val taxableLtcg: String
)

object ExemptionTracker {

    fun calculateExemptionStatus(matchedLots: List<MatchedLot>, fiscalYear: String): ExemptionStatus {
        val (startDate, endDate) = getFiscalYearBounds(fiscalYear)

        val fyLtcgLots = matchedLots.filter {
            it.taxTerm == TaxTerm.LONG_TERM &&
            it.disposalDate >= startDate &&
            it.disposalDate <= endDate
        }

        val totalEquityLtcg = fyLtcgLots.fold(BigDecimal.ZERO) { acc, m ->
            if (m.realizedGain > BigDecimal.ZERO) acc.add(m.realizedGain) else acc
        }

        val exemptionLimit = BigDecimal("125000.00")
        val exemptionUsed = totalEquityLtcg.min(exemptionLimit)
        val exemptionRemaining = exemptionLimit.subtract(exemptionUsed).max(BigDecimal.ZERO)
        val taxableLtcg = totalEquityLtcg.subtract(exemptionUsed).max(BigDecimal.ZERO)

        fun BigDecimal.fmt() = this.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

        return ExemptionStatus(
            fiscalYear = fiscalYear,
            totalEquityLtcgRealized = totalEquityLtcg.fmt(),
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
