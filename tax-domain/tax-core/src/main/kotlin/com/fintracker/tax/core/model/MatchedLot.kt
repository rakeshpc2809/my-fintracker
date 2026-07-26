package com.fintracker.tax.core.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class TaxTerm {
    SHORT_TERM,
    LONG_TERM,
    EXEMPT
}

data class MatchedLot(
    val matchId: String,
    val disposalEventId: String,
    val lotId: String,
    val assetId: String,
    val acquisitionDate: LocalDate,
    val disposalDate: LocalDate,
    val unitsMatched: BigDecimal,
    val costBasis: BigDecimal,
    val saleProceeds: BigDecimal,
    val realizedGain: BigDecimal,
    val holdingPeriodDays: Long,
    val taxTerm: TaxTerm
)
