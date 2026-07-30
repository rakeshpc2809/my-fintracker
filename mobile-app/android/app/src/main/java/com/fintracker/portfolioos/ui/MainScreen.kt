package com.fintracker.portfolioos.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintracker.portfolioos.data.PortfolioRepository
import com.fintracker.portfolioos.ui.screens.HoldingsScreen
import com.fintracker.portfolioos.ui.screens.HomeScreen
import com.fintracker.portfolioos.ui.screens.RadarScreen
import com.fintracker.portfolioos.ui.screens.SettingsScreen
import com.fintracker.portfolioos.ui.theme.NeonCyan
import com.fintracker.portfolioos.ui.theme.SurfaceContainer
import com.fintracker.portfolioos.ui.theme.VoidBlack

enum class NavTab(val label: String) {
    HOME("Home"),
    HOLDINGS("Holdings"),
    RADAR("Radar"),
    SETTINGS("Settings")
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }
    var hideBalances by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        PortfolioRepository.syncWithBackend()
    }

    Scaffold(
        containerColor = VoidBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // M3 Expressive Floating Bottom Navigation Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = SurfaceContainer.copy(alpha = 0.98f),
                    tonalElevation = 10.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(78.dp)
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == NavTab.HOME,
                            alwaysShowLabel = true,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTab = NavTab.HOME
                            },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = {
                                Text(
                                    NavTab.HOME.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == NavTab.HOME) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan,
                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.HOLDINGS,
                            alwaysShowLabel = true,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTab = NavTab.HOLDINGS
                            },
                            icon = { Icon(Icons.Default.Assessment, contentDescription = "Holdings") },
                            label = {
                                Text(
                                    NavTab.HOLDINGS.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == NavTab.HOLDINGS) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan,
                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.RADAR,
                            alwaysShowLabel = true,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTab = NavTab.RADAR
                            },
                            icon = { Icon(Icons.Default.Radar, contentDescription = "Radar") },
                            label = {
                                Text(
                                    NavTab.RADAR.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == NavTab.RADAR) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan,
                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.SETTINGS,
                            alwaysShowLabel = true,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTab = NavTab.SETTINGS
                            },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = {
                                Text(
                                    NavTab.SETTINGS.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == NavTab.SETTINGS) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan,
                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Native Android 17 Shared Axis Spring Motion
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow), initialOffsetX = { width -> width / 4 }) + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow), targetOffsetX = { width -> -width / 4 }) + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow), initialOffsetX = { width -> -width / 4 }) + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow), targetOffsetX = { width -> width / 4 }) + fadeOut(tween(200)))
                }
            },
            modifier = Modifier.padding(innerPadding),
            label = "nativeAndroid17Motion"
        ) { tab ->
            when (tab) {
                NavTab.HOME -> HomeScreen(
                    hideBalances = hideBalances,
                    onToggleHideBalances = { hideBalances = !hideBalances }
                )
                NavTab.HOLDINGS -> HoldingsScreen()
                NavTab.RADAR -> RadarScreen()
                NavTab.SETTINGS -> SettingsScreen(
                    hideBalances = hideBalances,
                    onToggleHideBalances = { hideBalances = !hideBalances }
                )
            }
        }
    }
}
