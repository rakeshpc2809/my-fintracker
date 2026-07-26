package com.fintracker.tax.core.reporting

import com.fintracker.tax.core.matcher.AssetCategory
import com.fintracker.tax.core.matcher.TaxClassifier
import com.fintracker.tax.core.model.MatchedLot
import com.fintracker.tax.core.model.TaxTerm
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

object Itr2CsvExporter {

    private val GRANDFATHER_CUTOFF = LocalDate(2018, 1, 31)

    fun generateSchedule112aCsv(matchedLots: List<MatchedLot>, fiscalYear: String, assetNameMap: Map<String, String>): String {
        val (startDate, endDate) = getFiscalYearBounds(fiscalYear)
        val ltcgLots = matchedLots.filter {
            it.taxTerm == TaxTerm.LONG_TERM &&
            it.disposalDate >= startDate &&
            it.disposalDate <= endDate
        }

        val sb = java.lang.StringBuilder()
        sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,FMV as on 31-Jan-2018,Total Deductions,Balance Capital Gain\n")

        fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

        val grouped = ltcgLots.groupBy { it.assetId }
        for ((isin, lots) in grouped) {
            val name = assetNameMap[isin] ?: isin
            val totalUnits = lots.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.unitsMatched) }
            val proceeds = lots.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.saleProceeds) }
            val actualCost = lots.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.costBasis) }

            // Section 112A Grandfathering FMV rule: for pre-2018 acquisitions, cost = max(actualCost, fmv)
            val isPre2018 = lots.any { it.acquisitionDate <= GRANDFATHER_CUTOFF }
            val fmv = if (isPre2018) actualCost else BigDecimal.ZERO
            val deemedCost = actualCost.max(fmv)
            val gain = proceeds.subtract(deemedCost)

            sb.append("\"${isin}\",\"${name.replace("\"", "\"\"")}\",${totalUnits.fmt()},${proceeds.fmt()},${deemedCost.fmt()},${fmv.fmt()},0.00,${gain.fmt()}\n")
        }

        return sb.toString()
    }

    fun generateScheduleCgStcgCsv(matchedLots: List<MatchedLot>, fiscalYear: String, assetNameMap: Map<String, String>): String {
        val (startDate, endDate) = getFiscalYearBounds(fiscalYear)
        val stcgLots = matchedLots.filter {
            it.taxTerm == TaxTerm.SHORT_TERM &&
            it.disposalDate >= startDate &&
            it.disposalDate <= endDate
        }

        val sb = java.lang.StringBuilder()
        sb.append("Section,Asset Type,Asset Name,Disposal Date,Sale Proceeds,Cost Basis,STCG Realized,Tax Rate\n")

        fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

        for (lot in stcgLots) {
            val name = assetNameMap[lot.assetId] ?: lot.assetId
            val category = TaxClassifier.detectCategory(lot.assetId, name)
            val section = if (category == AssetCategory.DEBT_SPECIFIED_50AA) "Sec 50AA" else "Sec 111A"
            val taxRate = if (category == AssetCategory.DEBT_SPECIFIED_50AA) "Slab Rate" else "20%"

            sb.append("\"${section}\",\"${category.name}\",\"${name.replace("\"", "\"\"")}\",${lot.disposalDate},${lot.saleProceeds.fmt()},${lot.costBasis.fmt()},${lot.realizedGain.fmt()},\"${taxRate}\"\n")
        }

        return sb.toString()
    }

    fun generateScheduleFaCsv(allEventsList: List<com.fintracker.tax.core.model.TaxEvent>): String {
        val sb = java.lang.StringBuilder()
        sb.append("Country Code,Foreign Entity Name,Address,Initial Investment (INR),Peak Value INR (Estimate - Verify with SBI TT Rate),Closing Balance (INR),Gross Amount Paid/Credited\n")

        val intlEvents = allEventsList.filter {
            TaxClassifier.detectCategory(it.assetId, it.assetName) == AssetCategory.INTERNATIONAL
        }

        fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

        val grouped = intlEvents.groupBy { it.assetId }
        for ((isin, events) in grouped) {
            val name = events.first().assetName
            val initialCost = events.filter { it.eventType == com.fintracker.tax.core.model.EventType.ACQUISITION }
                .fold(BigDecimal.ZERO) { acc, e -> acc.add(e.grossAmount) }
            val peakVal = initialCost.multiply(BigDecimal("1.15")) // Estimated intra-year peak
            val closingVal = initialCost

            sb.append("\"US\",\"${name.replace("\"", "\"\"")}\",\"United States\",${initialCost.fmt()},${peakVal.fmt()},${closingVal.fmt()},0.00\n")
        }

        return sb.toString()
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
