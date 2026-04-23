package com.pulse.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulse.music.ui.theme.PulseColors

@Composable
fun SearchScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.Canvas)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Search",
                color = PulseColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Coming soon — type to search your library.",
                color = PulseColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
