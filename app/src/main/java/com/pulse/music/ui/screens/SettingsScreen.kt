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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.music.PulseApplication
import com.pulse.music.data.ThemePreference
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.theme.PulseTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val folderState by vm.folderState.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()

    val prefs = PulseApplication.get().userPreferences
    val themePref by prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePreference.Dark)
    val scope = rememberCoroutineScope()

    var showRenameDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = BottomBarContentPadding.calculateBottomPadding(),
        ),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                Text(
                    text = "Settings",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.displayMedium,
                )
            }
        }

        item {
            ProfileCard(
                userName = userName,
                onEdit = { showRenameDialog = true },
            )
        }

        item {
            SectionLabel("Appearance")
            SettingRow(
                title = "Theme",
                subtitle = when (themePref) {
                    ThemePreference.Light -> "Light mode"
                    ThemePreference.Dark -> "Dark mode"
                    ThemePreference.Auto -> "Follow system"
                },
                trailing = {
                    ThemeToggle(
                        selected = themePref,
                        onSelect = { mode ->
                            scope.launch { prefs.setTheme(mode) }
                        },
                    )
                },
            )
        }

        item {
            SectionLabel("Music folder")
            SettingRow(
                title = "Source folder",
                subtitle = folderState.displayPath,
                leading = {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            if (!folderState.exists) {
                SettingRow(
                    title = "Create Pulse folder",
                    subtitle = "Make the folder so you can drop music in",
                    onClick = { vm.createPulseFolder() },
                    trailing = { Chevron() },
                )
            }
            val scanSub = when (val s = scanState) {
                is LibraryViewModel.ScanState.Completed ->
                    if (s.count == 0) "Folder is empty"
                    else "${s.count} songs · Just scanned"
                is LibraryViewModel.ScanState.Scanning -> "Scanning…"
                else -> "${allSongs.size} songs"
            }
            SettingRow(
                title = "Rescan library",
                subtitle = scanSub,
                onClick = { vm.rescan() },
                trailing = { Chevron() },
            )
        }

        item {
            SectionLabel("Account")
            SettingRow(
                title = "Sign in with Google",
                subtitle = "Not signed in · Coming soon",
                // Disabled for v0.2 — Firebase integration is scaffolded but off
            )
            SettingRow(
                title = "Sync across devices",
                subtitle = "Requires sign-in",
            )
        }

        item {
            SectionLabel("About")
            SettingRow(
                title = "Pulse · v0.2.0",
                subtitle = "Made with care",
            )
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = userName,
            onDismiss = { showRenameDialog = false },
            onSave = { newName ->
                scope.launch { prefs.setUserName(newName) }
                showRenameDialog = false
            },
        )
    }
}

@Composable
private fun ProfileCard(userName: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PulseTheme.colors.pillSurface)
            .border(1.dp, PulseTheme.colors.line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink)
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase() ?: "K",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = userName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Tap Edit to change your name",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            text = "Edit",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PulseTheme.colors.pillSurfaceStrong)
                .clickable(onClick = onEdit)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = PulseTheme.colors.textDim,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    leading: @Composable (() -> Unit)? = null,
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
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 12.dp)) { trailing() }
        }
    }
}

@Composable
private fun ThemeToggle(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PulseTheme.colors.pillSurface)
            .border(1.dp, PulseTheme.colors.line, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        ThemePreference.entries.forEach { mode ->
            val isSelected = mode == selected
            val label = when (mode) {
                ThemePreference.Light -> "Light"
                ThemePreference.Dark -> "Dark"
                ThemePreference.Auto -> "Auto"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = PulseTheme.colors.textDim,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var input by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            BasicTextField(
                value = input,
                onValueChange = { input = it.take(32) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PulseTheme.colors.pillSurface)
                    .padding(14.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.isNotBlank()) onSave(input.trim()) },
            ) {
                Text("Save", color = MaterialTheme.colorScheme.onBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
