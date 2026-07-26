package com.fintracker.tax.core.reporting

import com.fintracker.tax.core.model.MatchedLot
import com.fintracker.tax.core.model.TaxTerm
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class Itr2ScheduleCgReport(
    val fiscalYear: String,
    val totalSaleProceeds: String,
    val totalCostBasis: String,
    val totalRealizedStcg: String,
    val totalRealizedLtcg: String,
    val ltcgExemptionUsed: String,
    val netTaxableLtcg: String,
    val matchedLotCount: Int
)

object TaxReportExporter {

    fun generateItr2Report(matchedLots: List<MatchedLot>, fiscalYear: String): Itr2ScheduleCgReport {
        val (startDate, endDate) = getFiscalYearBounds(fiscalYear)

        // Filter matched lots strictly by disposal date falling in the requested fiscal year
        val fyLots = matchedLots.filter { it.disposalDate >= startDate && it.disposalDate <= endDate }

        val totalSaleProceeds = fyLots.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.saleProceeds) }
        val totalCostBasis = fyLots.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.costBasis) }

        val stcgLots = fyLots.filter { it.taxTerm == TaxTerm.SHORT_TERM }
        val ltcgLots = fyLots.filter { it.taxTerm == TaxTerm.LONG_TERM }

        val totalStcg = stcgLots.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.realizedGain) }
        val totalLtcg = ltcgLots.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.realizedGain) }

        val exemptionStatus = ExemptionTracker.calculateExemptionStatus(fyLots, fiscalYear)

        fun BigDecimal.fmt() = this.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

        return Itr2ScheduleCgReport(
            fiscalYear = fiscalYear,
            totalSaleProceeds = totalSaleProceeds.fmt(),
            totalCostBasis = totalCostBasis.fmt(),
            totalRealizedStcg = totalStcg.fmt(),
            totalRealizedLtcg = totalLtcg.fmt(),
            ltcgExemptionUsed = exemptionStatus.exemptionUsed,
            netTaxableLtcg = exemptionStatus.taxableLtcg,
            matchedLotCount = fyLots.size
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
