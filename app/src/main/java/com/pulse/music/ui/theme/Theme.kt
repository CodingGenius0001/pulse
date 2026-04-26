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
    primary = Color(0xFFD7B47A),
    onPrimary = Color(0xFF12100E),
    secondary = Color(0xFFB97A5A),
    onSecondary = Color(0xFFF5F0E8),
    tertiary = Color(0xFF8D8B74),
    background = Color(0xFF12100E),
    onBackground = Color(0xFFF5F0E8),
    surface = Color(0xFF171411),
    onSurface = Color(0xFFF5F0E8),
    surfaceVariant = Color(0xFF211C18),
    onSurfaceVariant = Color(0xFFAE9F90),
    outline = Color(0x33F5E8D8),
    outlineVariant = Color(0x1EF5E8D8),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF9A6D3A),
    onPrimary = Color(0xFFF6F0E7),
    secondary = Color(0xFF96593F),
    onSecondary = Color(0xFFF6F0E7),
    tertiary = Color(0xFF7C8064),
    background = Color(0xFFF6F0E7),
    onBackground = Color(0xFF171411),
    surface = Color(0xFFFCF8F2),
    onSurface = Color(0xFF171411),
    surfaceVariant = Color(0xFFF0E7DC),
    onSurfaceVariant = Color(0xFF75695D),
    outline = Color(0x2B171411),
    outlineVariant = Color(0x1A171411),
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
