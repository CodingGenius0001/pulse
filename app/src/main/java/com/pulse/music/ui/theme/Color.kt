package com.pulse.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified

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
    canvas = Color(0xFF08080A),
    surface = Color(0xFF0A0A0C),
    surfaceElevated = Color(0xFF121214),
    textPrimary = Color(0xFFFAFAF8),
    textSoft = Color(0xFFE8E6E0),
    textMuted = Color(0xFF888881),
    textDim = Color(0xFF5A5A55),
    onPrimary = Color(0xFF0A0A0C),
    pillSurface = Color(0x0FFFFFFF),
    pillSurfaceStrong = Color(0x1AFFFFFF),
    line = Color(0x0FFFFFFF),
    line2 = Color(0x1FFFFFFF),
    accentViolet = Color(0xFFA78BFA),
    accentPink = Color(0xFFF472B6),
    accentCream = Color(0xFFF0DFC8),
)

private val LightTokens = PulseColorTokens(
    canvas = Color(0xFFFAFAF8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF3F2EE),
    textPrimary = Color(0xFF0A0A0C),
    textSoft = Color(0xFF1F1F1D),
    textMuted = Color(0xFF6B6B66),
    textDim = Color(0xFF9A9A95),
    onPrimary = Color(0xFFFAFAF8),
    pillSurface = Color(0x0F000000),
    pillSurfaceStrong = Color(0x1A000000),
    line = Color(0x14000000),
    line2 = Color(0x24000000),
    accentViolet = Color(0xFF7C3AED),
    accentPink = Color(0xFFDB2777),
    accentCream = Color(0xFF8B6F47),
)

internal val LocalPulseColors = compositionLocalOf { DarkTokens }
internal val LocalPulseBackgroundTint = compositionLocalOf { Color.Unspecified }

internal val DarkPulseColors = DarkTokens
internal val LightPulseColors = LightTokens

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
    val Canvas = Color(0xFF08080A)
    val Surface = Color(0xFF0A0A0C)
    val SurfaceElevated = Color(0xFF121214)
    val TextPrimary = Color(0xFFFAFAF8)
    val TextSoft = Color(0xFFE8E6E0)
    val TextMuted = Color(0xFF888881)
    val TextDim = Color(0xFF5A5A55)
    val PillSurface = Color(0x0FFFFFFF)
    val PillSurfaceStrong = Color(0x1AFFFFFF)
    val Line = Color(0x0FFFFFFF)
    val Line2 = Color(0x1FFFFFFF)
    val AccentViolet = Color(0xFFA78BFA)
    val AccentPink = Color(0xFFF472B6)
    val AccentCream = Color(0xFFF0DFC8)
    val CanvasLight = Color(0xFFFAFAF8)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextPrimaryLight = Color(0xFF0A0A0C)
    val TextMutedLight = Color(0xFF6B6B66)
}
