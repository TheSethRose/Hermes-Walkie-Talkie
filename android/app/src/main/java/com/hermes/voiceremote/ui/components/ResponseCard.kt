package com.hermes.voiceremote.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ResponseCard(
    responseText: String,
    audioUrl: String?,
    onPlay: () -> Unit,
    onOpenFullText: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextPreviewCard(
        title = "Hermes responded",
        text = responseText,
        emptyText = "No response from Hermes yet.",
        icon = Icons.Default.Hearing,
        accentColor = MaterialTheme.colorScheme.secondary,
        maxLines = 5,
        onClick = onOpenFullText,
        modifier = modifier.testTag("response_card"),
        trailingContent = {
            if (!audioUrl.isNullOrEmpty()) {
                IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Replay response")
                }
            }
        }
    )
}
