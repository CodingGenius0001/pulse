package com.pulse.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pulse color tokens.
 *
 * Stay close to the mockup: near-black canvas, off-white primary,
 * translucent pill surfaces, and two soft accents (violet + pink)
 * used only for content-derived hints.
 */
object PulseColors {
    // Base
    val Canvas = Color(0xFF08080A)
    val Surface = Color(0xFF0A0A0C)
    val SurfaceElevated = Color(0xFF121214)

    // Text
    val TextPrimary = Color(0xFFFAFAF8)
    val TextSoft = Color(0xFFE8E6E0)
    val TextMuted = Color(0xFF888881)
    val TextDim = Color(0xFF5A5A55)

    // Translucent surfaces (for pill buttons, cards, etc.)
    val PillSurface = Color(0x0FFFFFFF)     // 6% white
    val PillSurfaceStrong = Color(0x1AFFFFFF) // 10% white

    // Borders / dividers
    val Line = Color(0x0FFFFFFF)
    val Line2 = Color(0x1FFFFFFF)

    // Accents
    val AccentViolet = Color(0xFFA78BFA)
    val AccentPink = Color(0xFFF472B6)
    val AccentCream = Color(0xFFF0DFC8)

    // Light-mode counterparts (for the toggle)
    val CanvasLight = Color(0xFFFAFAF8)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextPrimaryLight = Color(0xFF0A0A0C)
    val TextMutedLight = Color(0xFF6B6B66)
}
