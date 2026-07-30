package com.fintracker.valuation.fire

import com.fintracker.tax.core.model.Lot
import com.fintracker.valuation.goals.GoalTracker
import com.fintracker.valuation.goals.GoalTag
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

data class FireScenario(
    val id: String,
    val label: String,
    val monthlyExpenseToday: BigDecimal,
    val active: Boolean
)

data class FireProfile(
    val currentAge: Int = 32,
    val targetRetirementAge: Int = 45,
    val swrPercent: BigDecimal = BigDecimal("3.0"),
    val epfBalance: BigDecimal = BigDecimal("228000.00"),
    val epfUnlockAge: Int = 58,
    val realReturnRatePct: BigDecimal = BigDecimal("6.0"),
    val monthlyContribution: BigDecimal = BigDecimal("75000.00"),
    val nextReviewDate: LocalDate = LocalDate.parse("2027-03-31"),
    val scenarios: List<FireScenario> = listOf(
        FireScenario("scen_1", "With parents, no kid", BigDecimal("60000.00"), true),
        FireScenario("scen_2", "Renting, with kid", BigDecimal("90000.00"), false)
    )
)

data class FireSummary(
    val activeScenarioLabel: String,
    val monthlyExpenseToday: BigDecimal,
    val annualExpense: BigDecimal,
    val requiredCorpus: BigDecimal,
    val totalNetWorth: BigDecimal,
    val epfBalance: BigDecimal,
    val nonRetirementGoalAllocations: BigDecimal,
    val fireInvestableNetWorth: BigDecimal,
    val projectedCorpusAtTargetAge: BigDecimal,
    val yearsRemaining: Int,
    val status: String, // "ON_TRACK" or "SHORT"
    val shortageOrSurplusAmount: BigDecimal,
    val reviewDatePassed: Boolean,
    val scenarios: List<FireScenario>
)

object FireTracker {

    fun calculateFireSummary(
        openLots: List<Lot>,
        navMap: Map<String, BigDecimal>,
        currentDate: LocalDate,
        profile: FireProfile = FireProfile(),
        bankBalance: BigDecimal = BigDecimal.ZERO
    ): FireSummary {
        val totalMFValue = openLots.fold(BigDecimal.ZERO) { acc, lot ->
            val nav = navMap[lot.assetId] ?: lot.costPerUnit
            acc.add(lot.remainingUnits.multiply(nav))
        }

        val totalNetWorth = totalMFValue.add(bankBalance).add(profile.epfBalance) // MF + Bank Cash + EPF

        val goalSummary = GoalTracker.calculateGoalSummary(openLots, navMap, bankBalance = bankBalance)
        val nonRetirementGoals = goalSummary.allocatedGoalsAmount // Emergency + Bike + Wedding

        val fireInvestableNetWorth = totalNetWorth.subtract(profile.epfBalance).subtract(nonRetirementGoals).max(BigDecimal.ZERO)

        val activeScenario = profile.scenarios.firstOrNull { it.active } ?: profile.scenarios.first()
        val annualExpense = activeScenario.monthlyExpenseToday.multiply(BigDecimal("12"))

        val swrFraction = profile.swrPercent.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)
        val requiredCorpus = if (swrFraction > BigDecimal.ZERO) {
            annualExpense.divide(swrFraction, 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val yearsRemaining = (profile.targetRetirementAge - profile.currentAge).coerceAtLeast(0)
        val realRate = profile.realReturnRatePct.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP).toDouble()

        val compoundFactor = (1.0 + realRate).pow(yearsRemaining.toDouble())

        val fvInvestable = fireInvestableNetWorth.multiply(BigDecimal(compoundFactor))

        val fvSips = if (realRate > 0.0) {
            val annualContribution = profile.monthlyContribution.multiply(BigDecimal("12")).toDouble()
            val fvAnnuity = annualContribution * ((compoundFactor - 1.0) / realRate)
            BigDecimal(fvAnnuity)
        } else {
            profile.monthlyContribution.multiply(BigDecimal("12")).multiply(BigDecimal(yearsRemaining))
        }

        val projectedCorpus = fvInvestable.add(fvSips).setScale(2, RoundingMode.HALF_UP)

        val diff = projectedCorpus.subtract(requiredCorpus)
        val isOnTrack = diff >= BigDecimal.ZERO
        val status = if (isOnTrack) "ON_TRACK" else "SHORT"

        val reviewDatePassed = currentDate >= profile.nextReviewDate

        return FireSummary(
            activeScenarioLabel = activeScenario.label,
            monthlyExpenseToday = activeScenario.monthlyExpenseToday,
            annualExpense = annualExpense,
            requiredCorpus = requiredCorpus,
            totalNetWorth = totalNetWorth.setScale(2, RoundingMode.HALF_UP),
            epfBalance = profile.epfBalance,
            nonRetirementGoalAllocations = nonRetirementGoals,
            fireInvestableNetWorth = fireInvestableNetWorth.setScale(2, RoundingMode.HALF_UP),
            projectedCorpusAtTargetAge = projectedCorpus,
            yearsRemaining = yearsRemaining,
            status = status,
            shortageOrSurplusAmount = diff.abs().setScale(2, RoundingMode.HALF_UP),
            reviewDatePassed = reviewDatePassed,
            scenarios = profile.scenarios
        )
    }
}
