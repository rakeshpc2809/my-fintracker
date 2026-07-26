package com.fintracker.tax.core.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class Lot(
    val lotId: String,
    val assetId: String,
    val assetName: String,
    val acquisitionDate: LocalDate,
    val originalUnits: BigDecimal,
    val remainingUnits: BigDecimal,
    val costPerUnit: BigDecimal,
    val totalCostBasis: BigDecimal,
    val isGrandfathered: Boolean = false,
    val fmv20180131: BigDecimal? = null
)
