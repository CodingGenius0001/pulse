package com.pulse.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography. Uses the system default sans (which is Roboto on Pixel,
 * Samsung One UI Sans on Galaxy devices — both look clean).
 *
 * Two weights: Normal (400) for body, Medium (500) for emphasis.
 * Heavy weights are avoided to preserve the refined aesthetic.
 */
val PulseTypography = Typography(
    // Display — big hero titles
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
    ),

    // Headlines — now-playing title, section page titles
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),

    // Titles — section titles, card titles
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),

    // Body — song titles, descriptions
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),

    // Labels — pill buttons, tab labels
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.2.sp,
    ),
)
