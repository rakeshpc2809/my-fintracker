package com.fintracker.portfolioos.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintracker.portfolioos.data.PortfolioRepository
import com.fintracker.portfolioos.ui.theme.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HomeScreen(
    hideBalances: Boolean,
    onToggleHideBalances: () -> Unit
) {
    var returnMetric by remember { mutableStateOf("XIRR") }
    val haptics = LocalHapticFeedback.current

    val snapshot by PortfolioRepository.snapshotState.collectAsState()
    val isOffline by PortfolioRepository.isOfflineState.collectAsState()

    // Pulsing aura animation for live engine badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura"
    )

    val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val fireSummary = remember(currentDate) {
        PortfolioRepository.computeLocalFireSummary()
    }

    val scrollState = rememberScrollState()

    // Dynamic metrics calculated directly from current snapshot
    val curValNum = snapshot?.totalCurrentValue?.toDoubleOrNull() ?: 1796027.00
    val invValNum = snapshot?.totalInvested?.toDoubleOrNull() ?: 1553217.00
    val gainValNum = snapshot?.totalUnrealizedGain?.toDoubleOrNull() ?: 242810.00
    val gainPctNum = if (invValNum > 0) ((gainValNum / invValNum) * 100) else 15.63

    fun fmtInr(num: Double): String {
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

    val curValStr = fmtInr(curValNum)
    val invValStr = fmtInr(invValNum)
    val gainValStr = (if (gainValNum >= 0) "+ " else "- ") + fmtInr(Math.abs(gainValNum))
    val gainPctStr = String.format("%s%.2f%%", if (gainPctNum >= 0) "+" else "", gainPctNum)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Expressive Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Portfolio OS",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMain
                    )
                }
                Text(
                    text = if (isOffline) "Offline Engine • Cached Snapshot" else "Live Backend • Connected Node",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isOffline) AmberGlow else NeonCyan
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleHideBalances) {
                    Icon(
                        if (hideBalances) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Privacy Toggle",
                        tint = TextMain
                    )
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceContainerHigh,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(auraScale)
                                .clip(CircleShape)
                                .background(if (isOffline) AmberGlow else EmeraldGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOffline) "Offline" else "Live",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextMain
                        )
                    }
                }
            }
        }

        // Asymmetric Expressive Hero Card with Mesh Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomStart = 16.dp, bottomEnd = 36.dp))
                .background(HeroGradientBrush)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent)),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomStart = 16.dp, bottomEnd = 36.dp)
                )
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NET WORTH SNAPSHOT",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NeonCyan,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.3f)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(gainPctStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (hideBalances) "••••••••" else curValStr,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = TextMain
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Segmented Metric Switcher with Tactile Haptics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.4f)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (returnMetric == "XIRR") NeonCyan else Color.Transparent,
                                modifier = Modifier.clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    returnMetric = "XIRR"
                                }
                            ) {
                                Text(
                                    text = "XIRR",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (returnMetric == "XIRR") Color.Black else TextMuted,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (returnMetric == "TWR") NeonCyan else Color.Transparent,
                                modifier = Modifier.clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    returnMetric = "TWR"
                                }
                            ) {
                                Text(
                                    text = "TWR",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (returnMetric == "TWR") Color.Black else TextMuted,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (returnMetric == "XIRR") "+7.28%" else "+8.15%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = EmeraldGreen
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Unrealized Gain", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (hideBalances) "••••" else gainValStr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (gainValNum >= 0) EmeraldGreen else CrimsonRed
                        )
                    }
                    Column {
                        Text("Invested Capital", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (hideBalances) "••••" else invValStr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextMain
                        )
                    }
                }
            }
        }

        // Custom Canvas Gauge Card for Section 112A LTCG Exemption
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
                    Canvas(modifier = Modifier.size(70.dp)) {
                        drawArc(
                            color = Color(0xFF2C3857),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = GlowCyanBrush,
                            startAngle = 135f,
                            sweepAngle = 15f,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text("0%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Section 112A Exemption", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("₹ 0 used of ₹ 1,25,000 annual limit", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextMuted)

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF023824)) {
                        Text(
                            "₹ 1.25L Headroom Available",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // M3 Expressive FIRE Target Card with Dynamic Calculation
        val targetCorpus = try { fireSummary.requiredCorpus.toDouble() } catch (e: Exception) { 24000000.0 }
        val currentInvestable = try { fireSummary.fireInvestableNetWorth.toDouble() } catch (e: Exception) { curValNum }
        val fireProgressFloat = (currentInvestable / targetCorpus).coerceIn(0.0, 1.0).toFloat()

        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "FIRE RETIREMENT MILESTONE",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Target: ₹ ${(targetCorpus / 10000000.0)} Cr",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMain
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerHighest
                    ) {
                        Text(
                            text = fireSummary.status,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { fireProgressFloat },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = NeonCyan,
                    trackColor = SurfaceContainerHighest
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Investable: ${fmtInr(currentInvestable)} (${fireSummary.yearsRemaining} yrs to age ${fireSummary.scenarios.firstOrNull()?.label ?: "45"})",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}
