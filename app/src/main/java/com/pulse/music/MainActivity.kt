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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pulse.music.ui.PulseApp
import com.pulse.music.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                AppEntry()
            }
        }
    }
}

/**
 * Handles the audio permission gate. Pulse can't read music without it,
 * so we block the main app until permission is granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun AppEntry() {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    if (permissionState.status.isGranted) {
        PulseApp()
    } else {
        PermissionGate(onRequest = { permissionState.launchPermissionRequest() })
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF08080A))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pulse",
                color = androidx.compose.ui.graphics.Color(0xFFFAFAF8),
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Needs access to your music library to play songs from your device.",
                color = androidx.compose.ui.graphics.Color(0xFF888881),
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFAFAF8),
                    contentColor = androidx.compose.ui.graphics.Color(0xFF0A0A0C)
                ),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "Grant access",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
