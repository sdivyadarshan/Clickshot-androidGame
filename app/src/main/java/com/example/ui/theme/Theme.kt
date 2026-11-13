package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF004D59),
    onPrimaryContainer = Color(0xFF99F6FF),
    secondary = NeonMagenta,
    onSecondary = Color(0xFF330018),
    secondaryContainer = Color(0xFF660032),
    onSecondaryContainer = Color(0xFFFFB3D9),
    tertiary = NeonGold,
    onTertiary = Color(0xFF332300),
    tertiaryContainer = Color(0xFF664600),
    onTertiaryContainer = Color(0xFFFFDE80),
    background = CyberBackground,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = NeonCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = Typography,
        content = content
    )
}
