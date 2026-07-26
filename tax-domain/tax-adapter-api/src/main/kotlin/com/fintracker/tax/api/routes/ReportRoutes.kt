package com.fintracker.tax.api.routes

import com.fintracker.tax.core.matcher.FifoMatcher
import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.ports.EventStorePort
import com.fintracker.tax.core.reporting.ExemptionTracker
import com.fintracker.tax.core.reporting.TaxReportExporter
import com.fintracker.valuation.nav.AmfiNavSync
import com.fintracker.valuation.xirr.CashFlow
import com.fintracker.valuation.xirr.XirrEngine
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
data class PortfolioSummaryResponse(
    val totalInvested: String,
    val totalCurrentValue: String,
    val totalUnrealizedGain: String,
    val xirrPercentage: String,
    val activeHoldingCount: Int
)

@Serializable
data class AssetAllocationEntry(
    val assetId: String,
    val assetName: String,
    val investedValue: String,
    val currentValue: String,
    val percentage: String
)

@Serializable
data class HarvestOpportunityDto(
    val assetId: String,
    val assetName: String,
    val lotId: String,
    val acquisitionDate: String,
    val remainingUnits: String,
    val costPerUnit: String,
    val currentNav: String,
    val potentialHarvestableLoss: String
)

@Serializable
data class PerformancePoint(
    val date: String,
    val invested: String,
    val valuation: String
)

fun Route.reportRoutes(eventStore: EventStorePort) {
    val fifoMatcher = FifoMatcher()
    val amfiSync = AmfiNavSync()
    val xirrEngine = XirrEngine()

    route("/api/v1/tax") {
        get("/reports/itr2") {
            val fy = call.request.queryParameters["fy"] ?: "2026-27"
            val allEvents = eventStore.getAllEvents()
            val (_, matchedLots) = fifoMatcher.processEvents(allEvents)
            val report = TaxReportExporter.generateItr2Report(matchedLots, fy)

            call.respond(report)
        }

        get("/exemption-status") {
            val fy = call.request.queryParameters["fy"] ?: "2026-27"
            val allEvents = eventStore.getAllEvents()
            val (_, matchedLots) = fifoMatcher.processEvents(allEvents)
            val status = ExemptionTracker.calculateExemptionStatus(matchedLots, fy)

            call.respond(status)
        }

        get("/harvest-opportunities") {
            val allEvents = eventStore.getAllEvents()
            val (openLots, _) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.associateBy { it.isin }

            val opportunities = mutableListOf<HarvestOpportunityDto>()
            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            for (lot in openLots) {
                val navEntry = navMap[lot.assetId]
                val currentNav = navEntry?.nav ?: lot.costPerUnit

                if (currentNav < lot.costPerUnit) {
                    val lossPerUnit = lot.costPerUnit.subtract(currentNav)
                    val potentialLoss = lot.remainingUnits.multiply(lossPerUnit)

                    opportunities.add(
                        HarvestOpportunityDto(
                            assetId = lot.assetId,
                            assetName = lot.assetName,
                            lotId = lot.lotId,
                            acquisitionDate = lot.acquisitionDate.toString(),
                            remainingUnits = lot.remainingUnits.fmt(),
                            costPerUnit = lot.costPerUnit.fmt(),
                            currentNav = currentNav.fmt(),
                            potentialHarvestableLoss = potentialLoss.fmt()
                        )
                    )
                }
            }

            call.respond(opportunities)
        }
    }

    route("/api/v1/portfolio") {
        get("/summary") {
            val allEvents = eventStore.getAllEvents()
            val (openLots, matchedLots) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.associateBy { it.isin }

            val totalInvested = openLots.fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.totalCostBasis) }
            val activeHoldingCount = openLots.map { it.assetId }.distinct().size

            val totalCurrentValue = openLots.fold(BigDecimal.ZERO) { acc, lot ->
                val nav = navMap[lot.assetId]?.nav ?: lot.costPerUnit
                acc.add(lot.remainingUnits.multiply(nav))
            }
            val totalUnrealizedGain = totalCurrentValue.subtract(totalInvested)

            // Construct XIRR cashflows
            val cashflows = mutableListOf<CashFlow>()
            for (event in allEvents) {
                when (event.eventType) {
                    EventType.ACQUISITION, EventType.SIP_INSTALMENT -> {
                        cashflows.add(CashFlow(event.eventDate, event.grossAmount.negate()))
                    }
                    EventType.DISPOSAL -> {
                        cashflows.add(CashFlow(event.eventDate, event.grossAmount))
                    }
                    else -> {}
                }
            }
            // Add terminal valuation cashflow as of today
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            cashflows.add(CashFlow(today, totalCurrentValue))

            val xirr = xirrEngine.calculateXirr(cashflows)

            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            call.respond(
                PortfolioSummaryResponse(
                    totalInvested = totalInvested.fmt(),
                    totalCurrentValue = totalCurrentValue.fmt(),
                    totalUnrealizedGain = totalUnrealizedGain.fmt(),
                    xirrPercentage = String.format("%.2f%%", xirr),
                    activeHoldingCount = activeHoldingCount
                )
            )
        }

        get("/allocation") {
            val allEvents = eventStore.getAllEvents()
            val (openLots, _) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.associateBy { it.isin }

            val totalVal = openLots.fold(BigDecimal.ZERO) { acc, lot ->
                val nav = navMap[lot.assetId]?.nav ?: lot.costPerUnit
                acc.add(lot.remainingUnits.multiply(nav))
            }

            val grouped = openLots.groupBy { it.assetId }
            val allocations = mutableListOf<AssetAllocationEntry>()

            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            for ((assetId, lots) in grouped) {
                val invested = lots.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.totalCostBasis) }
                val currentVal = lots.fold(BigDecimal.ZERO) { acc, l ->
                    val nav = navMap[assetId]?.nav ?: l.costPerUnit
                    acc.add(l.remainingUnits.multiply(nav))
                }
                val pct = if (totalVal > BigDecimal.ZERO) {
                    currentVal.multiply(BigDecimal("100")).divide(totalVal, 2, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                allocations.add(
                    AssetAllocationEntry(
                        assetId = assetId,
                        assetName = lots.first().assetName,
                        investedValue = invested.fmt(),
                        currentValue = currentVal.fmt(),
                        percentage = pct.fmt()
                    )
                )
            }

            call.respond(allocations.sortedByDescending { BigDecimal(it.currentValue) })
        }

        get("/history") {
            val allEvents = eventStore.getAllEvents().sortedBy { it.eventDate }
            val points = mutableListOf<PerformancePoint>()

            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            var runningInvested = BigDecimal.ZERO
            for (event in allEvents) {
                if (event.eventType == EventType.ACQUISITION || event.eventType == EventType.SIP_INSTALMENT) {
                    runningInvested = runningInvested.add(event.grossAmount)
                } else if (event.eventType == EventType.DISPOSAL) {
                    runningInvested = runningInvested.subtract(event.grossAmount).max(BigDecimal.ZERO)
                }

                points.add(
                    PerformancePoint(
                        date = event.eventDate.toString(),
                        invested = runningInvested.fmt(),
                        valuation = runningInvested.fmt()
                    )
                )
            }

            call.respond(points)
        }
    }
}
