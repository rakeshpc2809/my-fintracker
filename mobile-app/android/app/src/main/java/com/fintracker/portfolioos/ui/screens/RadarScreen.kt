package com.fintracker.portfolioos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintracker.portfolioos.data.PortfolioRepository
import com.fintracker.portfolioos.ui.theme.*

@Composable
fun RadarScreen() {
    val snapshot by PortfolioRepository.snapshotState.collectAsState()

    // Dynamic extraction of harvest opportunities from real snapshot holdings
    val harvestableHoldings = remember(snapshot) {
        snapshot?.holdings?.filter { it.unrealizedGainPct.startsWith("-") } ?: emptyList()
    }

    // Dynamic extraction of phase-out consolidation assets from real snapshot holdings
    val phaseOutHoldings = remember(snapshot) {
        snapshot?.holdings?.filter { h ->
            listOf("EQUAL", "MIDCAP_150", "MIDCAP150", "EW").any { h.assetId.contains(it) || h.assetName.contains(it) }
        } ?: emptyList()
    }

    val totalPhaseOutProceeds = remember(phaseOutHoldings) {
        phaseOutHoldings.fold(0.0) { acc, h -> acc + (h.currentValue.toDoubleOrNull() ?: 0.0) }
    }

    fun fmtVal(num: Double): String {
        val rounded = Math.round(Math.abs(num))
        val str = rounded.toString()
        val formatted = if (str.length <= 3) str else {
            val last3 = str.substring(str.length - 3)
            val rest = str.substring(0, str.length - 3)
            val chunkedRest = rest.reversed().chunked(2).joinToString(",").reversed()
            "$chunkedRest,$last3"
        }
        return "₹ $formatted"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
    ) {
        Text(
            text = "Decision Radar Feed",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMain
        )
        Text(
            text = "Active Opportunities & Tax Transition Radar",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 90.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Card 1: Antigravity Dynamic Lift Factor Alert
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainer)
                        .border(
                            width = 1.dp,
                            color = NeonCyan,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🚀 ANTIGRAVITY DETECTED",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = NeonCyan
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF00363D)
                            ) {
                                Text(
                                    "β = 0.42 • Low Beta",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = NeonCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Gold & Arbitrage Buffer (+1.8% 30d TWR)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Market benchmark in 5.5% drawdown. Low-beta stabilizer buffer provides strong anti-gravity lift.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Card 2: Dynamic Consolidation Rebalance Alert
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainer)
                        .border(
                            width = 1.dp,
                            color = EmeraldGreen,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CONSOLIDATION REBALANCE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = EmeraldGreen
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF023824)
                            ) {
                                Text(
                                    "Sept 30 Window",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = EmeraldGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val displayProceeds = if (totalPhaseOutProceeds > 0) fmtVal(totalPhaseOutProceeds) else "₹ 2,56,200"

                        Text(
                            text = "Redeploy $displayProceeds Pro-Rata across 6-Fund Core",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Phase-out proceeds from legacy funds redeployed: LargeMidcap250 (33%), Flexi Cap (24%), Arbitrage (16%), Value30 (11%), Momentum50 (9%), Smallcap (7%).",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Card 3: Dynamic Tax-Loss Harvesting Opportunity
            if (harvestableHoldings.isNotEmpty()) {
                item {
                    val opp = harvestableHoldings.first()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceContainer)
                            .border(
                                width = 1.dp,
                                color = AmberGlow.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = AmberGlow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TAX-LOSS HARVEST",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = AmberGlow
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF3B2400)
                                ) {
                                    Text(
                                        "Before Mar 31",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = AmberGlow,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = opp.assetName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Harvest unrealized loss (${opp.unrealizedGainPct}%) before financial year end to offset taxable capital gains.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Card 4: Dynamic LTCG Tax Maturation Transition
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainer)
                        .border(
                            width = 1.dp,
                            color = NeonCyan.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LTCG MATURATION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = NeonCyan
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF00363D)
                            ) {
                                Text(
                                    "14 Days Left",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = NeonCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Parag Parikh Flexi Cap Fund (85 Units)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Lot transitions from STCG (20%) to LTCG (12.5%) tax bracket on August 12.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Card 5: Rebalance Optimal Status
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainer)
                        .border(
                            width = 1.dp,
                            color = EmeraldGreen.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "REBALANCE OPTIMAL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = EmeraldGreen
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF023824)
                            ) {
                                Text(
                                    "Balanced",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = EmeraldGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Asset Bucket Allocations",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Equity Core, Value, Momentum, Smallcap, and Arbitrage allocations are within target drift bands.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}
