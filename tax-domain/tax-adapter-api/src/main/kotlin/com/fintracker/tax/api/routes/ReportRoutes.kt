package com.fintracker.tax.api.routes

import com.fintracker.tax.core.matcher.FifoMatcher
import com.fintracker.tax.core.matcher.TaxClassifier
import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.ports.EventStorePort
import com.fintracker.tax.core.reporting.ExemptionTracker
import com.fintracker.tax.core.reporting.Itr2CsvExporter
import com.fintracker.tax.core.reporting.TaxReportExporter
import com.fintracker.valuation.advisor.RebalanceEngine
import com.fintracker.valuation.nav.AmfiNavSync
import com.fintracker.valuation.xirr.CashFlow
import com.fintracker.valuation.xirr.XirrEngine
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class PortfolioSummaryResponse(
    val totalInvested: String,
    val totalCurrentValue: String,
    val totalUnrealizedGain: String,
    val xirrPercentage: String,
    val activeHoldingCount: Int,
    val staleNavCount: Int
)

@Serializable
data class AssetAllocationEntry(
    val assetId: String,
    val assetName: String,
    val investedValue: String,
    val currentValue: String,
    val percentage: String,
    val navStale: Boolean
)

@Serializable
data class CategoryAllocationEntry(
    val category: String,
    val categoryName: String,
    val investedValue: String,
    val currentValue: String,
    val percentage: String
)

@Serializable
data class OpenLotDto(
    val lotId: String,
    val acquisitionDate: String,
    val remainingUnits: String,
    val costPerUnit: String,
    val totalCostBasis: String,
    val currentNav: String,
    val currentValue: String,
    val unrealizedGain: String,
    val holdingDays: Long,
    val daysToLtcg: Long,
    val isLtcg: Boolean
)

@Serializable
data class HoldingDetailDto(
    val assetId: String,
    val assetName: String,
    val category: String,
    val investedValue: String,
    val currentValue: String,
    val unrealizedGain: String,
    val unrealizedGainPct: String,
    val allocationPct: String,
    val navStale: Boolean,
    val lots: List<OpenLotDto>
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
data class MaturationLadderDto(
    val assetId: String,
    val assetName: String,
    val lotId: String,
    val acquisitionDate: String,
    val remainingUnits: String,
    val totalCostBasis: String,
    val currentValue: String,
    val unrealizedGain: String,
    val holdingDays: Long,
    val daysRemainingToLtcg: Long,
    val targetLtcgDate: String
)

@Serializable
data class RealizedLogDto(
    val matchId: String,
    val disposalDate: String,
    val acquisitionDate: String,
    val assetId: String,
    val assetName: String,
    val unitsMatched: String,
    val saleProceeds: String,
    val costBasis: String,
    val realizedGain: String,
    val taxTerm: String,
    val holdingPeriodDays: Long
)

@Serializable
data class PerformancePoint(
    val date: String,
    val invested: String,
    val valuation: String
)

@Serializable
data class RebalanceLotDto(
    val assetName: String,
    val unitsToSell: String,
    val redemptionProceeds: String,
    val estimatedGain: String,
    val taxTerm: String,
    val estimatedTaxDrag: String
)

@Serializable
data class RebalancePreviewDto(
    val targetRedemptionAmount: String,
    val actualRedemptionAmount: String,
    val totalEstimatedGain: String,
    val totalTaxDrag: String,
    val effectiveTaxRatePct: String,
    val ltcgExemptionHarvested: String,
    val selectedLots: List<RebalanceLotDto>
)

@Serializable
data class MobileSyncSnapshotDto(
    val generatedAt: String,
    val fiscalYear: String,
    val totalInvested: String,
    val totalCurrentValue: String,
    val totalUnrealizedGain: String,
    val holdings: List<HoldingDetailDto>,
    val realizedLog: List<RealizedLogDto>
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

        get("/export/itr2/zip") {
            val fy = call.request.queryParameters["fy"] ?: "2026-27"
            val allEvents = eventStore.getAllEvents()
            val (_, matchedLots) = fifoMatcher.processEvents(allEvents)
            val assetNameMap = allEvents.associate { it.assetId to it.assetName }

            val csv112a = Itr2CsvExporter.generateSchedule112aCsv(matchedLots, fy, assetNameMap)
            val csvStcg = Itr2CsvExporter.generateScheduleCgStcgCsv(matchedLots, fy, assetNameMap)
            val csvFa = Itr2CsvExporter.generateScheduleFaCsv(allEvents)

            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("schedule_112a.csv"))
                zos.write(csv112a.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("schedule_cg_stcg.csv"))
                zos.write(csvStcg.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("schedule_fa.csv"))
                zos.write(csvFa.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "itr2_exports_$fy.zip").toString()
            )
            call.respondBytes(baos.toByteArray(), io.ktor.http.ContentType.Application.Zip)
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

        get("/maturation-ladder") {
            val allEvents = eventStore.getAllEvents()
            val (openLots, _) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.associateBy { it.isin }
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            val ladder = mutableListOf<MaturationLadderDto>()
            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            for (lot in openLots) {
                val category = TaxClassifier.detectCategory(lot.assetId, lot.assetName)
                val thresholdDays = when (category) {
                    com.fintracker.tax.core.matcher.AssetCategory.EQUITY -> 365L
                    com.fintracker.tax.core.matcher.AssetCategory.GOLD_SILVER, com.fintracker.tax.core.matcher.AssetCategory.INTERNATIONAL, com.fintracker.tax.core.matcher.AssetCategory.SGB -> 730L
                    com.fintracker.tax.core.matcher.AssetCategory.DEBT_SPECIFIED_50AA -> -1L
                }

                if (thresholdDays > 0) {
                    val holdingDays = lot.acquisitionDate.daysUntil(today).toLong()
                    val daysRemaining = thresholdDays - holdingDays

                    if (daysRemaining in 1..90) {
                        val nav = navMap[lot.assetId]?.nav ?: lot.costPerUnit
                        val curVal = lot.remainingUnits.multiply(nav)
                        val gain = curVal.subtract(lot.totalCostBasis)
                        val targetDate = java.time.LocalDate.parse(lot.acquisitionDate.toString()).plusDays(thresholdDays).toString()

                        ladder.add(
                            MaturationLadderDto(
                                assetId = lot.assetId,
                                assetName = lot.assetName,
                                lotId = lot.lotId,
                                acquisitionDate = lot.acquisitionDate.toString(),
                                remainingUnits = lot.remainingUnits.fmt(),
                                totalCostBasis = lot.totalCostBasis.fmt(),
                                currentValue = curVal.fmt(),
                                unrealizedGain = gain.fmt(),
                                holdingDays = holdingDays,
                                daysRemainingToLtcg = daysRemaining,
                                targetLtcgDate = targetDate
                            )
                        )
                    }
                }
            }

            call.respond(ladder.sortedBy { it.daysRemainingToLtcg })
        }

        get("/realized-log") {
            val fy = call.request.queryParameters["fy"] ?: "2026-27"
            val allEvents = eventStore.getAllEvents()
            val (_, matchedLots) = fifoMatcher.processEvents(allEvents)

            val (startDate, endDate) = getFiscalYearBounds(fy)
            val fyLots = matchedLots.filter { it.disposalDate >= startDate && it.disposalDate <= endDate }
            val assetNameMap = allEvents.associate { it.assetId to it.assetName }

            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            val logs = fyLots.map { m ->
                RealizedLogDto(
                    matchId = m.matchId,
                    disposalDate = m.disposalDate.toString(),
                    acquisitionDate = m.acquisitionDate.toString(),
                    assetId = m.assetId,
                    assetName = assetNameMap[m.assetId] ?: m.assetId,
                    unitsMatched = m.unitsMatched.fmt(),
                    saleProceeds = m.saleProceeds.fmt(),
                    costBasis = m.costBasis.fmt(),
                    realizedGain = m.realizedGain.fmt(),
                    taxTerm = m.taxTerm.name,
                    holdingPeriodDays = m.holdingPeriodDays
                )
            }.sortedByDescending { it.disposalDate }

            call.respond(logs)
        }
    }

    route("/api/v1/portfolio") {
        get("/snapshot") {
            val fy = call.request.queryParameters["fy"] ?: "2026-27"
            val allEvents = eventStore.getAllEvents()
            val (openLots, matchedLots) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.filter { it.isin != null }.associateBy { it.isin!! }
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            val totalInvested = openLots.fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.totalCostBasis) }
            val totalCurrentValue = openLots.fold(BigDecimal.ZERO) { acc, lot ->
                val nav = navMap[lot.assetId]?.nav ?: lot.costPerUnit
                acc.add(lot.remainingUnits.multiply(nav))
            }
            val totalGain = totalCurrentValue.subtract(totalInvested)

            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            val holdings = getHoldingsList(openLots, navMap, today)
            val (startDate, endDate) = getFiscalYearBounds(fy)
            val fyLots = matchedLots.filter { it.disposalDate >= startDate && it.disposalDate <= endDate }
            val assetNameMap = allEvents.associate { it.assetId to it.assetName }

            val realizedLogs = fyLots.map { m ->
                RealizedLogDto(
                    matchId = m.matchId,
                    disposalDate = m.disposalDate.toString(),
                    acquisitionDate = m.acquisitionDate.toString(),
                    assetId = m.assetId,
                    assetName = assetNameMap[m.assetId] ?: m.assetId,
                    unitsMatched = m.unitsMatched.fmt(),
                    saleProceeds = m.saleProceeds.fmt(),
                    costBasis = m.costBasis.fmt(),
                    realizedGain = m.realizedGain.fmt(),
                    taxTerm = m.taxTerm.name,
                    holdingPeriodDays = m.holdingPeriodDays
                )
            }.sortedByDescending { it.disposalDate }

            call.respond(
                MobileSyncSnapshotDto(
                    generatedAt = Clock.System.now().toString(),
                    fiscalYear = fy,
                    totalInvested = totalInvested.fmt(),
                    totalCurrentValue = totalCurrentValue.fmt(),
                    totalUnrealizedGain = totalGain.fmt(),
                    holdings = holdings,
                    realizedLog = realizedLogs
                )
            )
        }

        get("/rebalance-preview") {
            val amountStr = call.request.queryParameters["amount"] ?: "100000"
            val targetAmount = BigDecimal(amountStr)

            val allEvents = eventStore.getAllEvents()
            val (openLots, matchedLots) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.filter { it.isin != null }.associateBy({ it.isin!! }, { it.nav })

            val fy = call.request.queryParameters["fy"] ?: "2026-27"
            val status = ExemptionTracker.calculateExemptionStatus(matchedLots, fy)
            val remExemption = BigDecimal(status.exemptionRemaining)

            val result = RebalanceEngine.calculateRebalancePreview(openLots, navMap, targetAmount, remExemption)

            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            val selectedDtos = result.selectedLots.map { s ->
                RebalanceLotDto(
                    assetName = s.assetName,
                    unitsToSell = s.unitsToSell.fmt(),
                    redemptionProceeds = s.redemptionProceeds.fmt(),
                    estimatedGain = s.estimatedGain.fmt(),
                    taxTerm = s.taxTerm,
                    estimatedTaxDrag = s.estimatedTaxDrag.fmt()
                )
            }

            call.respond(
                RebalancePreviewDto(
                    targetRedemptionAmount = result.targetRedemptionAmount.fmt(),
                    actualRedemptionAmount = result.actualRedemptionAmount.fmt(),
                    totalEstimatedGain = result.totalEstimatedGain.fmt(),
                    totalTaxDrag = result.totalTaxDrag.fmt(),
                    effectiveTaxRatePct = "${result.effectiveTaxRatePct}%",
                    ltcgExemptionHarvested = result.ltcgExemptionHarvested.fmt(),
                    selectedLots = selectedDtos
                )
            )
        }

        get("/summary") {
            val allEvents = eventStore.getAllEvents()
            val (openLots, _) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.filter { it.isin != null }.associateBy { it.isin!! }

            val totalInvested = openLots.fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.totalCostBasis) }
            val activeHoldingCount = openLots.map { it.assetId }.distinct().size
            val staleNavCount = openLots.map { it.assetId }.distinct().count { navMap[it] == null }

            val totalCurrentValue = openLots.fold(BigDecimal.ZERO) { acc, lot ->
                val nav = navMap[lot.assetId]?.nav ?: lot.costPerUnit
                acc.add(lot.remainingUnits.multiply(nav))
            }
            val totalUnrealizedGain = totalCurrentValue.subtract(totalInvested)

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
                    activeHoldingCount = activeHoldingCount,
                    staleNavCount = staleNavCount
                )
            )
        }

        get("/holdings") {
            val allEvents = eventStore.getAllEvents()
            val (openLots, _) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.filter { it.isin != null }.associateBy { it.isin!! }
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            val holdings = getHoldingsList(openLots, navMap, today)
            call.respond(holdings)
        }

        get("/category-allocation") {
            val allEvents = eventStore.getAllEvents()
            val (openLots, _) = fifoMatcher.processEvents(allEvents)
            val navEntries = amfiSync.fetchLatestNavsFromAmfi()
            val navMap = navEntries.associateBy { it.isin }

            val totalVal = openLots.fold(BigDecimal.ZERO) { acc, lot ->
                val nav = navMap[lot.assetId]?.nav ?: lot.costPerUnit
                acc.add(lot.remainingUnits.multiply(nav))
            }

            val grouped = openLots.groupBy { TaxClassifier.detectCategory(it.assetId, it.assetName) }
            val allocations = mutableListOf<CategoryAllocationEntry>()

            fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

            val nameMap = mapOf(
                com.fintracker.tax.core.matcher.AssetCategory.EQUITY to "Domestic Equity",
                com.fintracker.tax.core.matcher.AssetCategory.DEBT_SPECIFIED_50AA to "Debt (Sec 50AA)",
                com.fintracker.tax.core.matcher.AssetCategory.GOLD_SILVER to "Gold & Silver",
                com.fintracker.tax.core.matcher.AssetCategory.INTERNATIONAL to "International Equities",
                com.fintracker.tax.core.matcher.AssetCategory.SGB to "Sovereign Gold Bonds"
            )

            for ((category, lots) in grouped) {
                val invested = lots.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.totalCostBasis) }
                val currentVal = lots.fold(BigDecimal.ZERO) { acc, l ->
                    val nav = navMap[l.assetId]?.nav ?: l.costPerUnit
                    acc.add(l.remainingUnits.multiply(nav))
                }
                val pct = if (totalVal > BigDecimal.ZERO) {
                    currentVal.multiply(BigDecimal("100")).divide(totalVal, 2, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                allocations.add(
                    CategoryAllocationEntry(
                        category = category.name,
                        categoryName = nameMap[category] ?: category.name,
                        investedValue = invested.fmt(),
                        currentValue = currentVal.fmt(),
                        percentage = pct.fmt()
                    )
                )
            }

            call.respond(allocations.sortedByDescending { BigDecimal(it.currentValue) })
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
                        percentage = pct.fmt(),
                        navStale = navMap[assetId] == null
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

private fun getHoldingsList(
    openLots: List<com.fintracker.tax.core.model.Lot>,
    navMap: Map<String, com.fintracker.valuation.nav.NavEntry>,
    today: LocalDate
): List<HoldingDetailDto> {
    val totalVal = openLots.fold(BigDecimal.ZERO) { acc, lot ->
        val nav = navMap[lot.assetId]?.nav ?: lot.costPerUnit
        acc.add(lot.remainingUnits.multiply(nav))
    }

    val grouped = openLots.groupBy { it.assetId }
    val holdings = mutableListOf<HoldingDetailDto>()
    fun BigDecimal.fmt() = this.setScale(2, RoundingMode.HALF_UP).toPlainString()

    for ((assetId, lots) in grouped) {
        val assetName = lots.first().assetName
        val category = TaxClassifier.detectCategory(assetId, assetName)

        val thresholdDays = when (category) {
            com.fintracker.tax.core.matcher.AssetCategory.EQUITY -> 365L
            com.fintracker.tax.core.matcher.AssetCategory.GOLD_SILVER, com.fintracker.tax.core.matcher.AssetCategory.INTERNATIONAL, com.fintracker.tax.core.matcher.AssetCategory.SGB -> 730L
            com.fintracker.tax.core.matcher.AssetCategory.DEBT_SPECIFIED_50AA -> -1L
        }

        val invested = lots.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.totalCostBasis) }
        val currentVal = lots.fold(BigDecimal.ZERO) { acc, l ->
            val nav = navMap[assetId]?.nav ?: l.costPerUnit
            acc.add(l.remainingUnits.multiply(nav))
        }
        val unrealizedGain = currentVal.subtract(invested)
        val unrealizedGainPct = if (invested > BigDecimal.ZERO) {
            unrealizedGain.multiply(BigDecimal("100")).divide(invested, 2, RoundingMode.HALF_UP).toPlainString()
        } else "0.00"

        val allocPct = if (totalVal > BigDecimal.ZERO) {
            currentVal.multiply(BigDecimal("100")).divide(totalVal, 2, RoundingMode.HALF_UP).toPlainString()
        } else "0.00"

        val lotDtos = lots.map { l ->
            val nav = navMap[assetId]?.nav ?: l.costPerUnit
            val lotCurVal = l.remainingUnits.multiply(nav)
            val lotGain = lotCurVal.subtract(l.totalCostBasis)
            val hDays = l.acquisitionDate.daysUntil(today).toLong()
            val daysToLtcg = if (thresholdDays > 0) (thresholdDays - hDays).coerceAtLeast(0) else -1L
            val isLtcg = thresholdDays > 0 && hDays >= thresholdDays

            OpenLotDto(
                lotId = l.lotId,
                acquisitionDate = l.acquisitionDate.toString(),
                remainingUnits = l.remainingUnits.fmt(),
                costPerUnit = l.costPerUnit.fmt(),
                totalCostBasis = l.totalCostBasis.fmt(),
                currentNav = nav.fmt(),
                currentValue = lotCurVal.fmt(),
                unrealizedGain = lotGain.fmt(),
                holdingDays = hDays,
                daysToLtcg = daysToLtcg,
                isLtcg = isLtcg
            )
        }.sortedBy { it.acquisitionDate }

        holdings.add(
            HoldingDetailDto(
                assetId = assetId,
                assetName = assetName,
                category = category.name,
                investedValue = invested.fmt(),
                currentValue = currentVal.fmt(),
                unrealizedGain = unrealizedGain.fmt(),
                unrealizedGainPct = unrealizedGainPct,
                allocationPct = allocPct,
                navStale = navMap[assetId] == null,
                lots = lotDtos
            )
        )
    }

    return holdings.sortedByDescending { BigDecimal(it.currentValue) }
}

private fun getFiscalYearBounds(fiscalYear: String): Pair<LocalDate, LocalDate> {
    val parts = fiscalYear.split("-")
    val startYear = parts[0].trim().toIntOrNull() ?: 2026
    val endYear = if (parts.size > 1 && parts[1].trim().length == 2) {
        (startYear / 100) * 100 + parts[1].trim().toInt()
    } else {
        startYear + 1
    }
    return Pair(LocalDate(startYear, 4, 1), LocalDate(endYear, 3, 31))
}
