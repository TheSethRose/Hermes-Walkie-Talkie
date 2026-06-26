package com.hermes.voiceremote.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

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
        accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        onClick = onOpenFullText,
        modifier = modifier.testTag("transcript_card"),
        contentPadding = PaddingValues(12.dp),
        iconSize = 16.dp,
        bodyTextStyle = MaterialTheme.typography.bodyMedium
    )
}
