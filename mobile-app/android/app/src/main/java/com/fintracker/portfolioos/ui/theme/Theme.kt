package com.fintracker.portfolioos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// M3 Expressive Color System - Curated Obsidian Design Tokens
val NeonCyan = Color(0xFF00F2FE)
val ElectricTeal = Color(0xFF4FACFE)
val EmeraldGreen = Color(0xFF10B981)
val AmberGlow = Color(0xFFF59E0B)
val CrimsonRed = Color(0xFFFF453A)
val DeepViolet = Color(0xFF6366F1)

val VoidBlack = Color(0xFF060913)
val SurfaceContainerLowest = Color(0xFF0B0F1B)
val SurfaceContainerLow = Color(0xFF111728)
val SurfaceContainer = Color(0xFF182035)
val SurfaceContainerHigh = Color(0xFF222C46)
val SurfaceContainerHighest = Color(0xFF2C3857)

val TextMain = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)

// Expressive Gradients
val HeroGradientBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF0B192C), Color(0xFF1E3E62), Color(0xFF008B8B))
)

val GlowCyanBrush = Brush.horizontalGradient(
    colors = listOf(NeonCyan, ElectricTeal)
)

val PositiveGainBrush = Brush.horizontalGradient(
    colors = listOf(EmeraldGreen, Color(0xFF34D399))
)

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363D),
    onPrimaryContainer = NeonCyan,
    secondary = EmeraldGreen,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF023824),
    onSecondaryContainer = EmeraldGreen,
    tertiary = AmberGlow,
    background = VoidBlack,
    onBackground = TextMain,
    surface = SurfaceContainer,
    onSurface = TextMain,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = TextMuted,
    error = CrimsonRed
)

@Composable
fun PortfolioOsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ExpressiveDarkColorScheme,
        content = content
    )
}
