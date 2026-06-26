package com.hermes.voiceremote.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.voiceremote.state.VoiceSessionStatus

@Composable
fun StatusPill(
    isConnected: Boolean,
    status: VoiceSessionStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isSessionActive = status == VoiceSessionStatus.LISTENING || 
                             status == VoiceSessionStatus.UPLOADING || 
                             status == VoiceSessionStatus.THINKING || 
                             status == VoiceSessionStatus.SPEAKING

        val (dotColor, textColor, textValue) = when {
            isSessionActive -> Triple(
                Color(0xFFFFD600).copy(alpha = dotAlpha),
                Color(0xFFFFD600),
                "ACTIVE"
            )
            isConnected -> Triple(
                Color(0xFF22C55E).copy(alpha = dotAlpha),
                Color(0xFF22C55E),
                "ONLINE"
            )
            else -> Triple(
                Color(0xFF757575),
                Color(0xFFA8A8A8),
                "OFFLINE"
            )
        }

        // Connected Dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Text(
            text = textValue,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Divider(
            modifier = Modifier
                .height(12.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.outline
        )

        val statusColor = when (status) {
            VoiceSessionStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            VoiceSessionStatus.LISTENING -> MaterialTheme.colorScheme.error
            VoiceSessionStatus.UPLOADING -> MaterialTheme.colorScheme.onSurface
            VoiceSessionStatus.THINKING -> MaterialTheme.colorScheme.onSurface
            VoiceSessionStatus.SPEAKING -> MaterialTheme.colorScheme.primary
            VoiceSessionStatus.ERROR -> MaterialTheme.colorScheme.error
        }

        Text(
            text = status.name,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

// Extension helper for M3 surface tone mapping
@Composable
private fun ColorScheme.surfaceColorAtElevation(elevation: androidx.compose.ui.unit.Dp): Color {
    return surface
}
