package com.pulse.music.util

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit

/** Format millis as M:SS. */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Produces a deterministic gradient from a seed string. Used as the fallback
 * for songs whose files don't contain embedded album art.
 *
 * The same input always yields the same colors — so the UI is stable across
 * recomposition and the visual identity of a song is consistent.
 */
fun gradientFor(seed: String): Brush {
    val hash = seed.hashCode()
    // 12 warm, muted palettes lifted from the design doc
    val palettes = listOf(
        listOf(Color(0xFF5E3A4A), Color(0xFF7A4A5E)),
        listOf(Color(0xFF2D3E4F), Color(0xFF1A2B3C)),
        listOf(Color(0xFF4A3524), Color(0xFF2F1F14)),
        listOf(Color(0xFF3B4A3C), Color(0xFF253126)),
        listOf(Color(0xFF5A3A30), Color(0xFF3A2620)),
        listOf(Color(0xFF3A4A5A), Color(0xFF253A4A)),
        listOf(Color(0xFF4A2E5A), Color(0xFF2E1A3A)),
        listOf(Color(0xFF5A4A30), Color(0xFF3A2E20)),
        listOf(Color(0xFF2A3E3A), Color(0xFF1A2A28)),
        listOf(Color(0xFF5A3A3A), Color(0xFF3A2626)),
        listOf(Color(0xFF3A3A5A), Color(0xFF262640)),
        listOf(Color(0xFF5A4A5A), Color(0xFF3A2E3A)),
    )
    val palette = palettes[Math.floorMod(hash, palettes.size)]
    return Brush.linearGradient(palette)
}
