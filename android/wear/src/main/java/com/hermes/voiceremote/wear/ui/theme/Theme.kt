package com.hermes.voiceremote.wear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = HermesYellow,
        onPrimary = Color(0xFF111111),
        background = HermesBg,
        onBackground = HermesTextPrimary,
        surface = HermesBg,
        onSurface = HermesTextPrimary,
        primaryContainer = HermesSurfaceVariant,
        onPrimaryContainer = HermesYellow,
        surfaceVariant = HermesSurfaceVariant,
        onSurfaceVariant = HermesTextSecondary,
        outline = HermesBorder,
        error = HermesError,
        onError = Color(0xFF111111)
    )

@Composable
fun HermesWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
