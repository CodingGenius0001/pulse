package com.pulse.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PulseColors.TextPrimary,
    onPrimary = PulseColors.Canvas,
    secondary = PulseColors.AccentViolet,
    onSecondary = PulseColors.Canvas,
    tertiary = PulseColors.AccentPink,
    background = PulseColors.Canvas,
    onBackground = PulseColors.TextPrimary,
    surface = PulseColors.Surface,
    onSurface = PulseColors.TextPrimary,
    surfaceVariant = PulseColors.SurfaceElevated,
    onSurfaceVariant = PulseColors.TextMuted,
    outline = PulseColors.Line2,
    outlineVariant = PulseColors.Line,
)

private val LightColorScheme = lightColorScheme(
    primary = PulseColors.TextPrimaryLight,
    onPrimary = PulseColors.SurfaceLight,
    secondary = PulseColors.AccentViolet,
    onSecondary = Color.White,
    tertiary = PulseColors.AccentPink,
    background = PulseColors.CanvasLight,
    onBackground = PulseColors.TextPrimaryLight,
    surface = PulseColors.SurfaceLight,
    onSurface = PulseColors.TextPrimaryLight,
    surfaceVariant = Color(0xFFF0F0EC),
    onSurfaceVariant = PulseColors.TextMutedLight,
    outline = Color(0x1F000000),
    outlineVariant = Color(0x0F000000),
)

@Composable
fun PulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseTypography,
        content = content,
    )
}
