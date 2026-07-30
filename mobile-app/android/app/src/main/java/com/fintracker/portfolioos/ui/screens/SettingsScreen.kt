package com.fintracker.portfolioos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fintracker.portfolioos.ui.theme.*
import com.fintracker.portfolioos.ui.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(
    hideBalances: Boolean,
    onToggleHideBalances: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Settings & Connections",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextMain
            )
            Text(
                text = "Desktop Node Sync • Privacy Controls • Android 17 Native",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }

        // Host Connection Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceContainer)
                .border(
                    width = 1.dp,
                    color = NeonCyan.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DESKTOP COCKPIT NODE",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceContainerHighest
                    ) {
                        Text(
                            text = "Ktor Port 8080",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMain,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.desktopIp,
                    onValueChange = { settingsViewModel.updateDesktopIp(it) },
                    label = { Text("Desktop Host IP Address / Tailscale DNS", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceContainerHighest,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        settingsViewModel.testConnectionAndSync()
                    },
                    enabled = !uiState.isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (uiState.isSyncing) "Connecting to Host..." else "Test Connection & Sync Snapshot",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.syncStatusMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (uiState.syncStatusMsg.startsWith("✓")) Color(0xFF023824) else Color(0xFF3B2400),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (uiState.syncStatusMsg.startsWith("✓")) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (uiState.syncStatusMsg.startsWith("✓")) EmeraldGreen else AmberGlow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.syncStatusMsg,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.syncStatusMsg.startsWith("✓")) EmeraldGreen else AmberGlow
                            )
                        }
                    }
                }
            }
        }

        // Privacy & Stealth Mode Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceContainer)
                .border(
                    width = 1.dp,
                    color = SurfaceContainerHighest,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = TextMain, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRIVACY & STEALTH MODE",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                    }

                    Switch(
                        checked = hideBalances,
                        onCheckedChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleHideBalances()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceContainerHighest
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Mask net worth totals and invested capital values across all dashboard cards.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        // System Diagnostic Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceContainer)
                .border(
                    width = 1.dp,
                    color = SurfaceContainerHighest,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "ENGINE DIAGNOSTICS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Architecture", fontSize = 12.sp, color = TextMuted)
                    Text("Hexagonal Core + Compose", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextMain)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Storage Driver", fontSize = 12.sp, color = TextMuted)
                    Text("DuckDB Hybrid Event Store", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextMain)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("System OS", fontSize = 12.sp, color = TextMuted)
                    Text("Android 17 (API 35+)", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = NeonCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}
