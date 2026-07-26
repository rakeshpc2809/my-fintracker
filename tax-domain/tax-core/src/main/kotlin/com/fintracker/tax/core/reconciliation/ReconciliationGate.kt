package com.fintracker.tax.core.reconciliation

import com.fintracker.tax.core.model.TaxEvent
import java.math.BigDecimal

object ReconciliationGate {
    data class ReconciliationResult(
        val isMatched: Boolean,
        val calculatedClosingUnits: BigDecimal,
        val declaredClosingUnits: BigDecimal,
        val delta: BigDecimal,
        val errorMessage: String? = null
    )

    fun validateStatement(events: List<TaxEvent>, declaredClosingUnits: BigDecimal): ReconciliationResult {
        val calculatedClosingUnits = events.fold(BigDecimal.ZERO) { acc, event ->
            acc.add(event.unitDelta())
        }
        
        val delta = calculatedClosingUnits.subtract(declaredClosingUnits).abs()
        val isMatched = delta.compareTo(BigDecimal("0.0001")) < 0

        return ReconciliationResult(
            isMatched = isMatched,
            calculatedClosingUnits = calculatedClosingUnits,
            declaredClosingUnits = declaredClosingUnits,
            delta = delta,
            errorMessage = if (!isMatched) {
                "Reconciliation Gate Failure: Calculated closing units ($calculatedClosingUnits) does not match declared closing units ($declaredClosingUnits). Delta: $delta"
            } else null
        )
    }
}
