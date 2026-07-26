package com.fintracker.valuation.xirr

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import kotlin.math.pow

data class CashFlow(
    val date: LocalDate,
    val amount: BigDecimal // negative for investments, positive for disposals / current valuation
)

class XirrEngine {

    fun calculateXirr(cashFlows: List<CashFlow>): Double {
        if (cashFlows.size < 2) return 0.0

        val sorted = cashFlows.sortedBy { it.date }
        val startDate = sorted.first().date

        val dates = sorted.map { startDate.daysUntil(it.date).toDouble() / 365.0 }
        val amounts = sorted.map { it.amount.toDouble() }

        var rate = 0.10 // Initial guess: 10%

        for (iter in 0..100) {
            var npv = 0.0
            var dNpv = 0.0

            for (i in dates.indices) {
                val t = dates[i]
                val c = amounts[i]
                val factor = (1.0 + rate).pow(t)

                if (factor != 0.0) {
                    npv += c / factor
                    dNpv -= t * c / (1.0 + rate).pow(t + 1.0)
                }
            }

            if (kotlin.math.abs(dNpv) < 1e-10) break

            val nextRate = rate - npv / dNpv
            if (kotlin.math.abs(nextRate - rate) < 1e-6) {
                rate = nextRate
                break
            }
            rate = nextRate

            if (rate <= -0.99) rate = -0.90
        }

        return if (rate.isNaN() || rate.isInfinite()) 0.0 else rate * 100.0
    }
}
