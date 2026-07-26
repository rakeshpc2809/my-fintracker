package com.fintracker.valuation.advisor

import java.math.BigDecimal

data class HarvestOpportunity(
    val assetId: String,
    val lotId: String,
    val unrealizedLoss: BigDecimal,
    val units: BigDecimal
)

class HarvestAdvisor {
    fun findHarvestOpportunities(): List<HarvestOpportunity> {
        return emptyList()
    }
}
