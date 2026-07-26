package com.fintracker.valuation.xirr

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import kotlin.math.abs
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

        fun npv(r: Double): Double {
            var sum = 0.0
            for (i in dates.indices) {
                val t = dates[i]
                val c = amounts[i]
                val factor = (1.0 + r).pow(t)
                if (factor != 0.0) {
                    sum += c / factor
                }
            }
            return sum
        }

        fun dNpv(r: Double): Double {
            var sum = 0.0
            for (i in dates.indices) {
                val t = dates[i]
                val c = amounts[i]
                val factor = (1.0 + r).pow(t + 1.0)
                if (factor != 0.0) {
                    sum -= t * c / factor
                }
            }
            return sum
        }

        // 1. Newton-Raphson starting at 10% guess
        var rate = 0.10
        for (iter in 0..100) {
            val f = npv(rate)
            val df = dNpv(rate)

            if (abs(df) > 1e-10) {
                val nextRate = rate - f / df
                if (abs(nextRate - rate) < 1e-7) {
                    return if (nextRate.isNaN() || nextRate.isInfinite()) 0.0 else nextRate * 100.0
                }
                rate = nextRate
            }
            if (rate <= -0.90) rate = -0.50
        }

        // 2. Bracketed Bisection Fallback in [-0.50, 3.0]
        var low = -0.50
        var high = 3.0
        var flow = npv(low)
        var fhigh = npv(high)

        if (flow * fhigh <= 0) {
            for (i in 0..100) {
                val mid = (low + high) / 2.0
                val fmid = npv(mid)
                if (abs(fmid) < 1e-7 || (high - low) < 1e-7) {
                    return mid * 100.0
                }
                if (flow * fmid < 0) {
                    high = mid
                    fhigh = fmid
                } else {
                    low = mid
                    flow = fmid
                }
            }
            return ((low + high) / 2.0) * 100.0
        }

        return if (rate.isNaN() || rate.isInfinite()) 0.0 else rate * 100.0
    }
}
