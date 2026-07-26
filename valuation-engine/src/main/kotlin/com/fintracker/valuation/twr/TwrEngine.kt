package com.fintracker.valuation.twr

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

data class SubPeriodValuation(
    val date: LocalDate,
    val startValuation: BigDecimal,
    val netCashFlow: BigDecimal,
    val endValuation: BigDecimal
)

object TwrEngine {

    fun calculateTwr(subPeriods: List<SubPeriodValuation>): Double {
        if (subPeriods.isEmpty()) return 0.0

        var compoundFactor = 1.0

        for (p in subPeriods) {
            val base = p.startValuation.add(p.netCashFlow)
            if (base > BigDecimal.ZERO) {
                val periodReturn = p.endValuation.toDouble() / base.toDouble()
                compoundFactor *= periodReturn
            }
        }

        val twrPercentage = (compoundFactor - 1.0) * 100.0
        return if (twrPercentage.isNaN() || twrPercentage.isInfinite()) 0.0 else twrPercentage
    }
}
