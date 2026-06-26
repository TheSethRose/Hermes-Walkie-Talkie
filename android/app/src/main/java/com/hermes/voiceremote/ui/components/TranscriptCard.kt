package com.hermes.voiceremote.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

@Composable
fun TranscriptCard(
    transcript: String,
    onOpenFullText: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextPreviewCard(
        title = "You said",
        text = transcript,
        emptyText = "No speech transcribed yet. Tap the Talk button to start.",
        icon = Icons.Default.Mic,
        accentColor = MaterialTheme.colorScheme.primary,
        maxLines = 3,
        onClick = onOpenFullText,
        modifier = modifier
    )
}
