package com.hermes.voiceremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.voiceremote.settings.AudioRoute

@Composable
fun AudioRouteLabel(
    route: AudioRoute,
    modifier: Modifier = Modifier
) {
    val icon = when (route) {
        AudioRoute.BLUETOOTH_HEADSET -> Icons.Default.Bluetooth
        AudioRoute.PHONE -> Icons.Default.PhoneAndroid
        AudioRoute.UNKNOWN -> Icons.Default.VolumeUp
    }

    val text = when (route) {
        AudioRoute.BLUETOOTH_HEADSET -> "Bluetooth headset"
        AudioRoute.PHONE -> "Phone mic/speaker"
        AudioRoute.UNKNOWN -> "Unknown route"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Audio route indicator",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
