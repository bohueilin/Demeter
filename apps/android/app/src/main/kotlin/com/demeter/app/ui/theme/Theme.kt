package com.demeter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Demeter brand tokens. Semantic status colors are PINNED — they are never recolored
 * by dynamic color or theme, so urgent/exhausted/stale stay identifiable. Status is
 * additionally always carried by icon + text, never color alone.
 */
object DemeterColors {
    val Harvest = Color(0xFFD9A441)
    val HarvestDeep = Color(0xFF8C6D1F)
    val Sprout = Color(0xFF7CB342)
    val SproutDeep = Color(0xFF4C8C4A)
    val Soil = Color(0xFF1C1B16)
    val SoilDark = Color(0xFF15140F)
    val SoilSurface = Color(0xFF262520)
    val Linen = Color(0xFFFAF6EE)
    val LinenSurface = Color(0xFFFFFDF7)
    val Attention = Color(0xFFE6A817)
    val Urgent = Color(0xFFE4572E)
    val Stale = Color(0xFF8E8B82)
}

/** Pinned status tokens (same in light and dark; verified for contrast on both). */
data class StatusColors(
    val healthy: Color,
    val useSoon: Color,
    val urgent: Color,
    val exhausted: Color,
    val unknown: Color,
    val staleEvidence: Color,
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(
        healthy = DemeterColors.SproutDeep,
        useSoon = DemeterColors.Attention,
        urgent = DemeterColors.Urgent,
        exhausted = DemeterColors.Stale,
        unknown = DemeterColors.Stale,
        staleEvidence = DemeterColors.Stale,
    )
}

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = DemeterColors.Harvest,
    onPrimary = Color(0xFF241A05),
    primaryContainer = Color(0xFF3D2F0D),
    onPrimaryContainer = Color(0xFFF2DDAE),
    secondary = DemeterColors.Sprout,
    onSecondary = Color(0xFF15290A),
    secondaryContainer = Color(0xFF2C3A1E),
    onSecondaryContainer = Color(0xFFD5E8BC),
    tertiary = DemeterColors.Attention,
    onTertiary = Color(0xFF2A1D00),
    tertiaryContainer = Color(0xFF3E2E08),
    onTertiaryContainer = Color(0xFFF5DFAE),
    background = DemeterColors.SoilDark,
    onBackground = Color(0xFFEAE5D9),
    surface = DemeterColors.Soil,
    onSurface = Color(0xFFEAE5D9),
    surfaceVariant = DemeterColors.SoilSurface,
    onSurfaceVariant = Color(0xFFBDB8AA),
    surfaceContainerLowest = Color(0xFF121109),
    surfaceContainerLow = Color(0xFF1C1B13),
    surfaceContainer = Color(0xFF23221A),
    surfaceContainerHigh = Color(0xFF2B2A20),
    surfaceContainerHighest = Color(0xFF34332A),
    surfaceTint = DemeterColors.Harvest,
    inverseSurface = Color(0xFFEAE5D9),
    inverseOnSurface = Color(0xFF201F1A),
    inversePrimary = DemeterColors.HarvestDeep,
    outline = Color(0xFF57544A),
    outlineVariant = Color(0xFF3B382F),
    error = DemeterColors.Urgent,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = DemeterColors.HarvestDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2DDAE),
    onPrimaryContainer = Color(0xFF3D2F0D),
    secondary = DemeterColors.SproutDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBC5),
    onSecondaryContainer = Color(0xFF1D2E0F),
    tertiary = Color(0xFF9C6F00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E3B3),
    onTertiaryContainer = Color(0xFF3A2A00),
    background = DemeterColors.Linen,
    onBackground = Color(0xFF201F1A),
    surface = DemeterColors.LinenSurface,
    onSurface = Color(0xFF201F1A),
    surfaceVariant = Color(0xFFEFE8D8),
    onSurfaceVariant = Color(0xFF5B5850),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2E7),
    surfaceContainer = Color(0xFFF1EBDD),
    surfaceContainerHigh = Color(0xFFEBE4D3),
    surfaceContainerHighest = Color(0xFFE5DECA),
    surfaceTint = DemeterColors.HarvestDeep,
    inverseSurface = Color(0xFF35342E),
    inverseOnSurface = Color(0xFFF5F0E4),
    inversePrimary = DemeterColors.Harvest,
    outline = Color(0xFFB3AD9E),
    outlineVariant = Color(0xFFD8D1BF),
    error = Color(0xFFB33A17),
)

@Composable
fun statusColors(): StatusColors {
    val dark = isSystemInDarkTheme()
    return StatusColors(
        healthy = if (dark) DemeterColors.Sprout else DemeterColors.SproutDeep,
        useSoon = if (dark) DemeterColors.Attention else Color(0xFF9C6F00),
        urgent = if (dark) Color(0xFFFF7A50) else Color(0xFFB33A17),
        exhausted = DemeterColors.Stale,
        unknown = DemeterColors.Stale,
        staleEvidence = DemeterColors.Stale,
    )
}

@Composable
fun DemeterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}
