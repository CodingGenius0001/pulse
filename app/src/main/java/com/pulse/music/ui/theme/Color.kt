package com.pulse.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp

/**
 * Pulse semantic color tokens. Provided by a CompositionLocal that
 * changes with the active theme, so screens can reference `PulseTheme.colors.canvas`
 * and auto-adapt when the user toggles Light/Dark.
 */
@Immutable
data class PulseColorTokens(
    val canvas: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSoft: Color,
    val textPrimary: Color,
    val textSoft: Color,
    val textMuted: Color,
    val textDim: Color,
    val onPrimary: Color,       // text that sits on top of primary-filled surfaces
    val pillSurface: Color,
    val pillSurfaceStrong: Color,
    val line: Color,
    val line2: Color,
    val accentViolet: Color,
    val accentPink: Color,
    val accentCream: Color,
)

private val DarkTokens = PulseColorTokens(
    canvas = Color(0xFF12100E),
    surface = Color(0xFF171411),
    surfaceElevated = Color(0xFF211C18),
    surfaceSoft = Color(0xFF2A241F),
    textPrimary = Color(0xFFF5F0E8),
    textSoft = Color(0xFFE4D8CB),
    textMuted = Color(0xFFAE9F90),
    textDim = Color(0xFF75695D),
    onPrimary = Color(0xFF12100E),
    pillSurface = Color(0xFF1D1814),
    pillSurfaceStrong = Color(0xFF2A231D),
    line = Color(0x1EF5E8D8),
    line2 = Color(0x33F5E8D8),
    accentViolet = Color(0xFFD7B47A),
    accentPink = Color(0xFFB97A5A),
    accentCream = Color(0xFFF1E6D5),
)

private val LightTokens = PulseColorTokens(
    canvas = Color(0xFFF6F0E7),
    surface = Color(0xFFFCF8F2),
    surfaceElevated = Color(0xFFF0E7DC),
    surfaceSoft = Color(0xFFE6D8C9),
    textPrimary = Color(0xFF171411),
    textSoft = Color(0xFF2A241F),
    textMuted = Color(0xFF75695D),
    textDim = Color(0xFFA6927C),
    onPrimary = Color(0xFFF6F0E7),
    pillSurface = Color(0xFFF1E7DB),
    pillSurfaceStrong = Color(0xFFE5D7C7),
    line = Color(0x1A171411),
    line2 = Color(0x2B171411),
    accentViolet = Color(0xFF9A6D3A),
    accentPink = Color(0xFF96593F),
    accentCream = Color(0xFF4A3A2A),
)

internal val LocalPulseColors = compositionLocalOf { DarkTokens }
internal val LocalPulseBackgroundTint = compositionLocalOf { Color.Unspecified }

internal val DarkPulseColors = DarkTokens
internal val LightPulseColors = LightTokens

internal fun PulseColorTokens.withAccent(accent: Color, darkTheme: Boolean): PulseColorTokens {
    val tunedAccent = accent.normalizeAccent(darkTheme)
    val companion = lerp(
        tunedAccent,
        if (darkTheme) Color(0xFFE7B28A) else Color(0xFF7E5A39),
        if (darkTheme) 0.36f else 0.42f,
    )
    val emphasis = if (darkTheme) {
        lerp(tunedAccent, Color(0xFFF4E9D8), 0.62f)
    } else {
        lerp(tunedAccent, Color(0xFF2E2217), 0.54f)
    }
    return copy(
        accentViolet = tunedAccent,
        accentPink = companion,
        accentCream = emphasis,
    )
}

private fun Color.normalizeAccent(darkTheme: Boolean): Color {
    val solid = copy(alpha = 1f)
    return if (darkTheme) lerp(solid, Color.White, 0.24f) else lerp(solid, Color.Black, 0.18f)
}

/**
 * Entry point for consuming theme colors. Usage:
 *   Box(modifier = Modifier.background(PulseTheme.colors.canvas))
 */
object PulseTheme {
    val colors: PulseColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalPulseColors.current

    val background: Color
        @Composable
        @ReadOnlyComposable
        get() {
            val tint = LocalPulseBackgroundTint.current
            val base = MaterialTheme.colorScheme.background
            return if (tint.isSpecified) tint.compositeOver(base) else base
        }
}

/**
 * Legacy reference object kept for any call sites still using the old dark-only
 * palette. New code should use `PulseTheme.colors.*` instead. These are the DARK
 * values and do not switch with the theme — so anything still referencing these
 * will look wrong in light mode. Kept here for compatibility during migration.
 */
@Deprecated("Use PulseTheme.colors for theme-aware colors")
object PulseColors {
    val Canvas = Color(0xFF12100E)
    val Surface = Color(0xFF171411)
    val SurfaceElevated = Color(0xFF211C18)
    val TextPrimary = Color(0xFFF5F0E8)
    val TextSoft = Color(0xFFE4D8CB)
    val TextMuted = Color(0xFFAE9F90)
    val TextDim = Color(0xFF75695D)
    val PillSurface = Color(0xFF1D1814)
    val PillSurfaceStrong = Color(0xFF2A231D)
    val Line = Color(0x1EF5E8D8)
    val Line2 = Color(0x33F5E8D8)
    val AccentViolet = Color(0xFFD7B47A)
    val AccentPink = Color(0xFFB97A5A)
    val AccentCream = Color(0xFFF1E6D5)
    val CanvasLight = Color(0xFFF6F0E7)
    val SurfaceLight = Color(0xFFFCF8F2)
    val TextPrimaryLight = Color(0xFF171411)
    val TextMutedLight = Color(0xFF75695D)
}
