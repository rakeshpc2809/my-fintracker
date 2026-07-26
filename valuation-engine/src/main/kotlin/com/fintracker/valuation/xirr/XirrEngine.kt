package com.fintracker.valuation.xirr

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.min
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

        fun npv(rate: Double): Double {
            var sum = 0.0
            for (i in dates.indices) {
                val t = dates[i]
                val c = amounts[i]
                val factor = (1.0 + rate).pow(t)
                if (factor != 0.0) {
                    sum += c / factor
                }
            }
            return sum
        }

        // Brent's Method Root Finder strictly bracketed within [-0.9999, 10.0]
        var a = -0.9999
        var b = 10.0
        var fa = npv(a)
        var fb = npv(b)

        if (fa * fb >= 0) {
            var bracketFound = false
            var step = a
            while (step <= b) {
                val fstep = npv(step)
                if (fa * fstep < 0) {
                    b = step
                    fb = fstep
                    bracketFound = true
                    break
                }
                step += 0.05
            }
            if (!bracketFound) return 0.0
        }

        var c = a
        var fc = fa
        var d = b - a
        var e = d

        for (iter in 0..100) {
            if (abs(fc) < abs(fb)) {
                a = b; b = c; c = a
                fa = fb; fb = fc; fc = fa
            }

            val tol = 1e-7
            val m = 0.5 * (c - b)

            if (abs(m) <= tol || fb == 0.0) {
                return if (b.isNaN() || b.isInfinite()) 0.0 else b * 100.0
            }

            if (abs(e) < tol || abs(fa) <= abs(fb)) {
                d = m; e = m
            } else {
                var s = fb / fa
                var p: Double
                var q: Double
                if (a == c) {
                    p = 2.0 * m * s
                    q = 1.0 - s
                } else {
                    val q0 = fa / fc
                    val r = fb / fc
                    p = s * (2.0 * m * q0 * (q0 - r) - (b - a) * (r - 1.0))
                    q = (q0 - 1.0) * (r - 1.0) * (s - 1.0)
                }
                if (p > 0) q = -q
                p = abs(p)

                if (2.0 * p < min(3.0 * m * q - abs(tol * q), abs(e * q))) {
                    e = d; d = p / q
                } else {
                    d = m; e = m
                }
            }

            a = b; fa = fb
            if (abs(d) > tol) {
                b += d
            } else {
                b += if (m > 0) tol else -tol
            }
            fb = npv(b)
        }

        return if (b.isNaN() || b.isInfinite()) 0.0 else b * 100.0
    }
}
