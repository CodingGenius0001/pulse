package com.pulse.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.music.ui.theme.PulseColors

/**
 * A group of circular buttons inside a single translucent pill.
 * Mirrors the prev/next + shuffle/repeat clusters in the Now Playing mockup.
 *
 * Children are laid out in a horizontal Row; content receives RowScope
 * so child buttons get standard row modifiers.
 */
@Composable
fun PillGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PulseColors.PillSurface)
            .border(1.dp, PulseColors.Line, RoundedCornerShape(999.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * A single circular button meant to live inside a PillGroup.
 */
@Composable
fun PillIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * The big white play-pause pill. Wider than tall, hero of the transport row.
 */
@Composable
fun PlayPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(PulseColors.TextPrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Standalone circular icon button on a subtle surface — used for header
 * back/overflow buttons.
 */
@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    background: Color = PulseColors.PillSurfaceStrong,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Chip-style filter pill used in the Library filter row.
 */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) PulseColors.TextPrimary else PulseColors.PillSurface
    val fg = if (selected) PulseColors.Canvas else PulseColors.TextMuted
    val borderStroke = if (selected) {
        BorderStroke(1.dp, PulseColors.TextPrimary)
    } else {
        BorderStroke(1.dp, PulseColors.Line)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(borderStroke, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
