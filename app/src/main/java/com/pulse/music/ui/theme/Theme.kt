package com.pulse.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.PulseApplication
import com.pulse.music.data.ThemePreference

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFAFAF8),
    onPrimary = Color(0xFF0A0A0C),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF0A0A0C),
    tertiary = Color(0xFFF472B6),
    background = Color(0xFF08080A),
    onBackground = Color(0xFFFAFAF8),
    surface = Color(0xFF0A0A0C),
    onSurface = Color(0xFFFAFAF8),
    surfaceVariant = Color(0xFF121214),
    onSurfaceVariant = Color(0xFF888881),
    outline = Color(0x1FFFFFFF),
    outlineVariant = Color(0x0FFFFFFF),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0A0A0C),
    onPrimary = Color(0xFFFAFAF8),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    tertiary = Color(0xFFDB2777),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF0A0A0C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0C),
    surfaceVariant = Color(0xFFF0F0EC),
    onSurfaceVariant = Color(0xFF6B6B66),
    outline = Color(0x24000000),
    outlineVariant = Color(0x14000000),
)

/**
 * Root theme. Reads the persisted ThemePreference from DataStore, applies the
 * right color scheme, and provides the theme-aware PulseColorTokens via a
 * CompositionLocal so screens can stay reactive.
 */
@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    val prefs = PulseApplication.get().userPreferences
    val themePref by prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePreference.Dark)
    val systemDark = isSystemInDarkTheme()

    val darkTheme = when (themePref) {
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
        ThemePreference.Auto -> systemDark
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val pulseTokens = if (darkTheme) DarkPulseColors else LightPulseColors

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

    CompositionLocalProvider(LocalPulseColors provides pulseTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PulseTypography,
            content = content,
        )
    }
}
