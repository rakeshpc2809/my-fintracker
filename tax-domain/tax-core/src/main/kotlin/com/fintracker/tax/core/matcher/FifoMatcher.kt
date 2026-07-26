package com.fintracker.tax.core.matcher

import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.model.Lot
import com.fintracker.tax.core.model.MatchedLot
import com.fintracker.tax.core.model.TaxEvent
import com.fintracker.tax.core.model.TaxTerm
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

class FifoMatcher {

    fun processEvents(events: List<TaxEvent>): Pair<List<Lot>, List<MatchedLot>> {
        val sortedEvents = events.sortedBy { it.eventDate }
        val openLots = mutableListOf<Lot>()
        val matchedLots = mutableListOf<MatchedLot>()

        for (event in sortedEvents) {
            when (event.eventType) {
                EventType.ACQUISITION, EventType.SIP_INSTALMENT, EventType.DIVIDEND_REINVEST -> {
                    openLots.add(
                        Lot(
                            lotId = UUID.randomUUID().toString(),
                            assetId = event.assetId,
                            assetName = event.assetName,
                            acquisitionDate = event.eventDate,
                            originalUnits = event.units,
                            remainingUnits = event.units,
                            costPerUnit = event.pricePerUnit,
                            totalCostBasis = event.grossAmount
                        )
                    )
                }

                EventType.BONUS -> {
                    // Bonus: Zero-cost lot, allotment date starts holding period
                    openLots.add(
                        Lot(
                            lotId = UUID.randomUUID().toString(),
                            assetId = event.assetId,
                            assetName = event.assetName,
                            acquisitionDate = event.eventDate,
                            originalUnits = event.units,
                            remainingUnits = event.units,
                            costPerUnit = BigDecimal.ZERO,
                            totalCostBasis = BigDecimal.ZERO
                        )
                    )
                }

                EventType.SPLIT -> {
                    // Split: Re-denominates existing open lots, keeping same acquisition date and total cost basis
                    val splitRatio = event.units // e.g. 2 for 1:2 split
                    if (splitRatio > BigDecimal.ZERO) {
                        for (i in openLots.indices) {
                            if (openLots[i].assetId == event.assetId) {
                                val current = openLots[i]
                                val newRemaining = current.remainingUnits.multiply(splitRatio)
                                val newOriginal = current.originalUnits.multiply(splitRatio)
                                val newCostPerUnit = if (newRemaining > BigDecimal.ZERO) {
                                    current.totalCostBasis.divide(newRemaining, 4, RoundingMode.HALF_UP)
                                } else BigDecimal.ZERO

                                openLots[i] = current.copy(
                                    originalUnits = newOriginal,
                                    remainingUnits = newRemaining,
                                    costPerUnit = newCostPerUnit
                                )
                            }
                        }
                    }
                }

                EventType.DISPOSAL -> {
                    var unitsToMatch = event.units
                    val iterator = openLots.iterator()

                    while (iterator.hasNext() && unitsToMatch > BigDecimal.ZERO) {
                        val currentLot = iterator.next()
                        if (currentLot.assetId != event.assetId || currentLot.remainingUnits <= BigDecimal.ZERO) {
                            continue
                        }

                        val matchedUnits = unitsToMatch.min(currentLot.remainingUnits)
                        val costBasisSlice = matchedUnits.multiply(currentLot.costPerUnit)
                        val saleProceedsSlice = matchedUnits.multiply(event.pricePerUnit)
                        val realizedGain = saleProceedsSlice.subtract(costBasisSlice)
                        val holdingDays = currentLot.acquisitionDate.daysUntil(event.eventDate).toLong()
                        val category = TaxClassifier.detectCategory(event.assetId, event.assetName)
                        val taxTerm = TaxClassifier.classifyTaxTerm(category, holdingDays)

                        matchedLots.add(
                            MatchedLot(
                                matchId = UUID.randomUUID().toString(),
                                disposalEventId = event.id,
                                lotId = currentLot.lotId,
                                assetId = event.assetId,
                                acquisitionDate = currentLot.acquisitionDate,
                                disposalDate = event.eventDate,
                                unitsMatched = matchedUnits,
                                costBasis = costBasisSlice,
                                saleProceeds = saleProceedsSlice,
                                realizedGain = realizedGain,
                                holdingPeriodDays = holdingDays,
                                taxTerm = taxTerm
                            )
                        )

                        unitsToMatch = unitsToMatch.subtract(matchedUnits)
                        val updatedRemaining = currentLot.remainingUnits.subtract(matchedUnits)

                        if (updatedRemaining <= BigDecimal.ZERO) {
                            iterator.remove()
                        } else {
                            val lotIdx = openLots.indexOf(currentLot)
                            if (lotIdx != -1) {
                                openLots[lotIdx] = currentLot.copy(remainingUnits = updatedRemaining)
                            }
                        }
                    }
                }

                else -> {
                    // Cash or non-unit events like SGB interest
                }
            }
        }

        return Pair(openLots, matchedLots)
    }
}
