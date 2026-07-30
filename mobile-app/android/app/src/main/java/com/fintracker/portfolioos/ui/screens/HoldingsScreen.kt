package com.fintracker.portfolioos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintracker.portfolioos.data.OpenLotDto
import com.fintracker.portfolioos.data.PortfolioRepository
import com.fintracker.portfolioos.ui.theme.*
import com.fintracker.tax.core.matcher.AssetCategory
import com.fintracker.tax.core.matcher.TaxClassifier

data class NativeHoldingItem(
    val assetId: String,
    val assetName: String,
    val category: AssetCategory,
    val currentValue: String,
    val investedValue: String,
    val gainPct: String,
    val lotCount: Int,
    val ltcgCount: Int,
    val rawLots: List<OpenLotDto>
)

@Composable
fun HoldingsScreen() {
    var selectedCategory by remember { mutableStateOf("ALL") }
    var expandedAssetId by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    val snapshot by PortfolioRepository.snapshotState.collectAsState()

    val realHoldings = remember(snapshot) {
        snapshot?.holdings?.map { h ->
            val cat = TaxClassifier.detectCategory(h.assetId, h.assetName)
            val ltcgCount = h.lots.count { it.isLtcg }
            val gainSign = if (h.unrealizedGainPct.startsWith("-")) "" else "+"
            val fmtCur = try {
                val num = h.currentValue.toDouble()
                val rounded = Math.round(Math.abs(num))
                val str = rounded.toString()
                if (str.length <= 3) str else {
                    val last3 = str.substring(str.length - 3)
                    val rest = str.substring(0, str.length - 3)
                    val chunkedRest = rest.reversed().chunked(2).joinToString(",").reversed()
                    "$chunkedRest,$last3"
                }
            } catch (e: Exception) { h.currentValue }

            val fmtInv = try {
                val num = h.investedValue.toDouble()
                val rounded = Math.round(Math.abs(num))
                val str = rounded.toString()
                if (str.length <= 3) str else {
                    val last3 = str.substring(str.length - 3)
                    val rest = str.substring(0, str.length - 3)
                    val chunkedRest = rest.reversed().chunked(2).joinToString(",").reversed()
                    "$chunkedRest,$last3"
                }
            } catch (e: Exception) { h.investedValue }

            NativeHoldingItem(
                assetId = h.assetId,
                assetName = h.assetName,
                category = cat,
                currentValue = fmtCur,
                investedValue = fmtInv,
                gainPct = "$gainSign${h.unrealizedGainPct}%",
                lotCount = h.lots.size,
                ltcgCount = ltcgCount,
                rawLots = h.lots
            )
        } ?: emptyList()
    }

    val filteredHoldings = remember(selectedCategory, realHoldings) {
        if (selectedCategory == "ALL") realHoldings
        else realHoldings.filter { it.category.name.contains(selectedCategory) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
    ) {
        Text(
            text = "Holdings & FIFO Lots",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMain
        )
        Text(
            text = "Tax Lot Matching • Section 112A Tracker",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(14.dp))

        // M3 Expressive Pill Filters
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf("ALL", "EQUITY", "INTERNATIONAL", "SGB", "DEBT").forEach { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) NeonCyan else SurfaceContainerHigh,
                    modifier = Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedCategory = cat
                    }
                ) {
                    Text(
                        text = cat,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) Color.Black else TextMuted,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredHoldings.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainer)
                    .padding(32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Holdings Synced",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect to Desktop Cockpit in Settings to sync snapshot.",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 90.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredHoldings) { holding ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceContainer)
                            .border(
                                width = 1.dp,
                                color = SurfaceContainerHighest,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                expandedAssetId = if (expandedAssetId == holding.assetId) null else holding.assetId
                            }
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SurfaceContainerHighest
                                    ) {
                                        Text(
                                            text = holding.category.name.replace("_SPECIFIED_50AA", ""),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = NeonCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = holding.assetName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMain
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "₹ ${holding.currentValue}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMain
                                    )
                                    Text(
                                        text = holding.gainPct,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (holding.gainPct.startsWith("+")) EmeraldGreen else CrimsonRed
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceContainerLowest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${holding.lotCount} Open Lots • ${holding.ltcgCount} LTCG, ${holding.lotCount - holding.ltcgCount} STCG",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }

                            // Dynamic FIFO Lot Breakdown Rendering
                            AnimatedVisibility(
                                visible = expandedAssetId == holding.assetId,
                                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn()
                            ) {
                                Column(modifier = Modifier.padding(top = 14.dp)) {
                                    HorizontalDivider(color = SurfaceContainerHighest)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "DYNAMIC FIFO LOT BREAKDOWN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = NeonCyan
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    holding.rawLots.forEach { lot ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = SurfaceContainerHigh,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "${lot.acquisitionDate} • ${lot.remainingUnits} units @ ₹${lot.costPerUnit}",
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = TextMain
                                                    )
                                                    Text(
                                                        text = "Cost: ₹${lot.totalCostBasis} (${lot.holdingDays}d held)",
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = TextMuted
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (lot.isLtcg) Color(0xFF023824) else Color(0xFF3B2400)
                                                ) {
                                                    Text(
                                                        text = if (lot.isLtcg) "LTCG" else "STCG",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (lot.isLtcg) EmeraldGreen else AmberGlow,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
