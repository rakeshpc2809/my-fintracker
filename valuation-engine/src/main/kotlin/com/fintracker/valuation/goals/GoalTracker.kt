package com.fintracker.valuation.goals

import com.fintracker.tax.core.model.Lot
import com.fintracker.valuation.advisor.BucketEngine
import com.fintracker.valuation.advisor.Bucket
import java.math.BigDecimal
import java.math.RoundingMode

enum class GoalTag {
    EMERGENCY,
    BIKE,
    WEDDING,
    RETIREMENT,
    UNALLOCATED
}

data class GoalAllocation(
    val holdingId: String,
    val holdingName: String,
    val goalTag: GoalTag,
    val allocatedAmount: BigDecimal
)

data class GoalSummary(
    val totalLiquidHoldings: BigDecimal,
    val allocatedGoalsAmount: BigDecimal,
    val unallocatedCash: BigDecimal,
    val allocationsByGoal: Map<GoalTag, BigDecimal>,
    val goalAllocations: List<GoalAllocation>
)

object GoalTracker {

    private val defaultAllocations = listOf(
        GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.EMERGENCY, BigDecimal("150000.00")),
        GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.BIKE, BigDecimal("100000.00")),
        GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.WEDDING, BigDecimal("100000.00"))
    )

    fun calculateGoalSummary(
        openLots: List<Lot>,
        navMap: Map<String, BigDecimal>,
        customAllocations: List<GoalAllocation> = defaultAllocations,
        bankBalance: BigDecimal = BigDecimal.ZERO
    ): GoalSummary {

        // Total liquid holdings (Liquid/Buffer bucket + bank balances)
        val totalLiquidHoldings = openLots.fold(BigDecimal.ZERO) { acc, lot ->
            val bucket = BucketEngine.classifyAssetToBucket(lot.assetId, lot.assetName)
            if (bucket == Bucket.LIQUID_BUFFER) {
                val nav = navMap[lot.assetId] ?: lot.costPerUnit
                acc.add(lot.remainingUnits.multiply(nav))
            } else acc
        }.add(bankBalance) // Includes bank balance from current snapshot

        val allocatedMap = mutableMapOf<GoalTag, BigDecimal>()
        GoalTag.values().forEach { allocatedMap[it] = BigDecimal.ZERO }

        var totalAllocatedNonUnallocated = BigDecimal.ZERO

        for (alloc in customAllocations) {
            val cur = allocatedMap[alloc.goalTag] ?: BigDecimal.ZERO
            allocatedMap[alloc.goalTag] = cur.add(alloc.allocatedAmount)

            if (alloc.goalTag != GoalTag.UNALLOCATED) {
                totalAllocatedNonUnallocated = totalAllocatedNonUnallocated.add(alloc.allocatedAmount)
            }
        }

        val unallocatedCash = totalLiquidHoldings.subtract(totalAllocatedNonUnallocated).max(BigDecimal.ZERO)
        allocatedMap[GoalTag.UNALLOCATED] = unallocatedCash

        return GoalSummary(
            totalLiquidHoldings = totalLiquidHoldings.setScale(2, RoundingMode.HALF_UP),
            allocatedGoalsAmount = totalAllocatedNonUnallocated.setScale(2, RoundingMode.HALF_UP),
            unallocatedCash = unallocatedCash.setScale(2, RoundingMode.HALF_UP),
            allocationsByGoal = allocatedMap.mapValues { it.value.setScale(2, RoundingMode.HALF_UP) },
            goalAllocations = customAllocations
        )
    }
}
