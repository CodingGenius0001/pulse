package com.pulse.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.theme.PulseColors

private enum class ThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
    Auto("Auto"),
}

@Composable
fun SettingsScreen() {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()

    var themeMode by remember { mutableStateOf(ThemeMode.Dark) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.Canvas),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = BottomBarContentPadding.calculateBottomPadding(),
        ),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                Text(
                    text = "Settings",
                    color = PulseColors.TextPrimary,
                    style = MaterialTheme.typography.displayMedium,
                )
            }
        }

        item { ProfileCard() }

        item {
            SectionLabel("Appearance")
            SettingRow(
                title = "Theme",
                subtitle = "Dark is the default vibe",
                trailing = {
                    ThemeToggle(
                        selected = themeMode,
                        onSelect = { themeMode = it },
                    )
                },
            )
        }

        item {
            SectionLabel("Music folder")
            SettingRow(
                title = "Source folder",
                subtitle = "Any folder named 'Pulse' on your device",
                onClick = { /* TODO folder picker */ },
                trailing = { Chevron() },
            )
            val sub = when (val s = scanState) {
                is LibraryViewModel.ScanState.Completed -> "${s.count} songs · Just scanned"
                is LibraryViewModel.ScanState.Scanning -> "Scanning…"
                else -> "${allSongs.size} songs"
            }
            SettingRow(
                title = "Rescan library",
                subtitle = sub,
                onClick = { vm.rescan() },
                trailing = { Chevron() },
            )
        }

        item {
            SectionLabel("Account")
            SettingRow(
                title = "Profile picture",
                subtitle = "Change your avatar",
                onClick = { /* TODO */ },
                trailing = { Chevron() },
            )
            SettingRow(
                title = "Sync across devices",
                subtitle = "Playlists, history, likes (coming soon)",
                onClick = { /* TODO Firebase */ },
                trailing = { Chevron() },
            )
            SettingRow(
                title = "Sign in with Google",
                subtitle = "Not signed in",
                onClick = { /* TODO Firebase */ },
                trailing = { Chevron() },
            )
        }

        item {
            SectionLabel("About")
            SettingRow(
                title = "Pulse · v0.1.0",
                subtitle = "Made with care",
            )
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ProfileCard() {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PulseColors.PillSurface)
            .border(1.dp, PulseColors.Line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(PulseColors.AccentViolet, PulseColors.AccentPink)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "K",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "You",
                color = PulseColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Sign in to sync across devices",
                color = PulseColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            text = "Edit",
            color = PulseColors.TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PulseColors.PillSurfaceStrong)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = PulseColors.TextDim,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PulseColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = PulseColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 12.dp)) { trailing() }
        }
    }
}

@Composable
private fun ThemeToggle(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PulseColors.PillSurface)
            .border(1.dp, PulseColors.Line, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) PulseColors.TextPrimary else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = mode.label,
                    color = if (isSelected) PulseColors.Canvas else PulseColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun Chevron() {
    Text(
        text = "›",
        color = PulseColors.TextDim,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    )
}
