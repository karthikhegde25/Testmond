package com.testmond.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SeedNavy = Color(0xFF1E3A5F)

private val LightColors = lightColorScheme(
    primary = SeedNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E2F5),
    onPrimaryContainer = Color(0xFF0A2540),
    secondary = Color(0xFF3F5C74),
    secondaryContainer = Color(0xFFD9E4EE),
    background = Color(0xFFFBFDFF),
    surface = Color(0xFFFBFDFF),
    surfaceVariant = Color(0xFFE1E7EE),
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC1E8),
    onPrimary = Color(0xFF0A2540),
    // Explicitly navy-based and dark -- without this, Material3's default
    // darkColorScheme() falls back to a baseline purple for primaryContainer,
    // which looked out of place ("light") against the rest of this dark theme.
    primaryContainer = Color(0xFF20364C),
    onPrimaryContainer = Color(0xFFBFD8F0),
    secondary = Color(0xFFB6C6D9),
    secondaryContainer = Color(0xFF2A323C),
    background = Color(0xFF11141A),
    surface = Color(0xFF1A1D24),
    surfaceVariant = Color(0xFF404957),
    onBackground = Color(0xFFE2E4E8),
    onSurface = Color(0xFFE2E4E8)
)

@Composable
fun TestmondTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
