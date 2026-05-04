package com.pulse.music

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.ui.PulseApp
import com.pulse.music.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {
    private var launchRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launchRoute = intent?.getStringExtra(EXTRA_OPEN_ROUTE)
        setContent {
            PulseTheme {
                AppEntry(
                    launchRoute = launchRoute,
                    onLaunchRouteConsumed = { launchRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRoute = intent.getStringExtra(EXTRA_OPEN_ROUTE)
    }

    companion object {
        const val EXTRA_OPEN_ROUTE = "open_route"
    }
}

/**
 * Handles the audio permission gate. Pulse can't read music without it,
 * so we block the main app until permission is granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun AppEntry(
    launchRoute: String?,
    onLaunchRouteConsumed: () -> Unit,
) {
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val mediaPermissionState = rememberPermissionState(mediaPermission)
    val notificationPermissionState = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    val prefs = PulseApplication.get().userPreferences
    val updateNotificationsEnabled by prefs.updateNotificationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val updateNotificationsPrompted by prefs.updateNotificationsPrompted.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(
        mediaPermissionState.status.isGranted,
        notificationPermissionState.status.isGranted,
        updateNotificationsEnabled,
        updateNotificationsPrompted,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            mediaPermissionState.status.isGranted &&
            updateNotificationsEnabled &&
            !updateNotificationsPrompted &&
            !notificationPermissionState.status.isGranted
        ) {
            prefs.setUpdateNotificationsPrompted(true)
            notificationPermissionState.launchPermissionRequest()
        }
    }

    if (mediaPermissionState.status.isGranted) {
        PulseApp(
            launchRoute = launchRoute,
            onLaunchRouteConsumed = onLaunchRouteConsumed,
        )
    } else {
        PermissionGate(onRequest = { mediaPermissionState.launchPermissionRequest() })
    }
}

/**
 * First-run gate asking for audio access. Uses theme-aware colors so it
 * matches the user's selected Light/Dark preference even before they reach
 * the main app.
 */
@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Pulse",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Pulse needs access to your audio library to play songs from your device. Your music stays on your phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = "Grant access",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
