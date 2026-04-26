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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.pulse.music.PulseApplication
import com.pulse.music.data.ThemePreference
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.update.UpdateState
import com.pulse.music.update.UpdateViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: LibraryViewModel,
    updateVm: UpdateViewModel,
) {
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val folderState by vm.folderState.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()

    val prefs = PulseApplication.get().userPreferences
    val themePref by prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePreference.Dark)
    val scope = rememberCoroutineScope()
    var showRenameDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentPadding = PaddingValues(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(text = "Settings", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.displayMedium)
                Text(text = "Keep the app understated and predictable.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item { ProfileCard(userName = userName, onEdit = { showRenameDialog = true }) }
        item { SettingsSection(title = "Appearance") { ThemeRow(themePref = themePref) { mode -> scope.launch { prefs.setTheme(mode) } } } }
        item {
            SettingsSection(title = "Music folder") {
                val scanSub = when (val s = scanState) {
                    is LibraryViewModel.ScanState.Completed -> if (s.count == 0) "Folder is empty" else "${s.count} songs after the latest scan"
                    is LibraryViewModel.ScanState.Scanning -> "Scanning the library"
                    else -> "${allSongs.size} songs indexed"
                }
                SettingRow(
                    title = "Source folder",
                    subtitle = folderState.displayPath,
                    leading = { SectionIcon(Icons.Filled.FolderOpen) },
                )
                if (!folderState.exists) {
                    SettingRow(
                        title = "Create Pulse folder",
                        subtitle = "Create the dedicated folder first",
                        onClick = { vm.createPulseFolder() },
                        trailing = { Chevron() },
                    )
                }
                SettingRow(
                    title = "Rescan library",
                    subtitle = scanSub,
                    onClick = { vm.rescan() },
                    trailing = { Chevron() },
                )
            }
        }
        item {
            SettingsSection(title = "Account") {
                SettingRow(title = "Sign in with Google", subtitle = "Not signed in yet")
                SettingRow(title = "Sync across devices", subtitle = "Reserved for a future account flow")
            }
        }
        item { SettingsSection(title = "Updates") { UpdateRow(updateVm) } }
        item {
            SettingsSection(title = "About") {
                val versionLabel = run {
                    val v = com.pulse.music.BuildConfig.VERSION_NAME
                    val build = com.pulse.music.BuildConfig.BUILD_NUMBER
                    if (build > 0) "Pulse v$v (build $build)" else "Pulse v$v"
                }
                SettingRow(title = versionLabel, subtitle = "Made with care for local listening")
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
        }
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
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(28.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink))),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = userName.firstOrNull()?.uppercase() ?: "P", color = PulseTheme.colors.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = userName, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium)
            Text(text = "Personal details stay on-device for now.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "Edit",
            color = PulseTheme.colors.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(PulseTheme.colors.accentCream)
                .clickable(onClick = onEdit)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title.uppercase(), color = PulseTheme.colors.accentViolet, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ThemeRow(themePref: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    SettingRow(
        title = "Theme",
        subtitle = when (themePref) {
            ThemePreference.Light -> "Light mode"
            ThemePreference.Dark -> "Dark mode"
            ThemePreference.Auto -> "Follow system"
        },
        trailing = { ThemeToggle(selected = themePref, onSelect = onSelect) },
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
            .clip(RoundedCornerShape(20.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleSmall)
                Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ThemeToggle(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PulseTheme.colors.surfaceSoft)
            .padding(4.dp),
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) PulseTheme.colors.accentCream else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = label,
                    color = if (isSelected) PulseTheme.colors.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(PulseTheme.colors.surfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = PulseTheme.colors.accentViolet, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Chevron() {
    Text(text = ">", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
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
        title = { Text("Your name", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            BasicTextField(
                value = input,
                onValueChange = { input = it.take(32) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PulseTheme.colors.surfaceElevated)
                    .padding(14.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (input.isNotBlank()) onSave(input.trim()) }) {
                Text("Save", color = PulseTheme.colors.accentViolet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = PulseTheme.colors.surface,
    )
}

@Composable
private fun UpdateRow(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        UpdateState.Idle -> {
            SettingRow(
                title = "Check for updates",
                subtitle = "Pull the latest build from GitHub",
                leading = { SectionIcon(Icons.Filled.SystemUpdate) },
                onClick = { vm.checkForUpdate() },
                trailing = { Chevron() },
            )
        }

        UpdateState.Checking -> {
            SettingRow(
                title = "Checking for updates",
                subtitle = "Talking to GitHub",
                leading = {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = PulseTheme.colors.accentViolet,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }

        UpdateState.UpToDate -> {
            SettingRow(
                title = "You're up to date",
                subtitle = "No newer build is available",
                leading = { SectionIcon(Icons.Filled.CheckCircle) },
                onClick = { vm.checkForUpdate() },
                trailing = {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Check again", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                },
            )
        }

        is UpdateState.Available -> AvailableCard(
            buildNumber = s.info.buildNumber,
            sizeBytes = s.info.sizeBytes,
            onDownload = { vm.downloadUpdate() },
            onDismiss = { vm.dismiss() },
        )

        is UpdateState.Downloading -> DownloadingCard(progress = s.progress)

        is UpdateState.Ready -> ReadyCard(
            onInstall = { vm.installUpdate() },
            onDismiss = { vm.dismiss() },
        )

        is UpdateState.Error -> {
            SettingRow(
                title = "Update check failed",
                subtitle = s.message,
                leading = { SectionIcon(Icons.Filled.ErrorOutline) },
                onClick = { vm.checkForUpdate() },
                trailing = {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun AvailableCard(
    buildNumber: Int,
    sizeBytes: Long,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Update available", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
        Text(text = "Build #$buildNumber - ${formatBytes(sizeBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                modifier = Modifier.weight(1.45f),
                label = "Download",
                icon = Icons.Filled.Download,
                filled = true,
                onClick = onDownload,
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "Later",
                filled = false,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun DownloadingCard(progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Downloading update", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
        Text(text = "${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = PulseTheme.colors.accentViolet,
            trackColor = PulseTheme.colors.surfaceElevated,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
        )
    }
}

@Composable
private fun ReadyCard(
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Update ready", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
        Text(text = "Install hands off to Android's package installer.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                modifier = Modifier.weight(1.2f),
                label = "Install",
                filled = true,
                onClick = onInstall,
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "Cancel",
                filled = false,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) PulseTheme.colors.accentCream else PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) PulseTheme.colors.onPrimary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            color = if (filled) PulseTheme.colors.onPrimary else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / 1024.0 / 1024.0
    if (mb >= 1.0) return String.format("%.1f MB", mb)
    val kb = bytes / 1024.0
    return String.format("%.0f KB", kb)
}
