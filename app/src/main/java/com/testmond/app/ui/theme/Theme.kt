package com.testmond.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.testmond.app.model.ThemeColor

// Background/surface neutrals stay the same across every accent color -- only primary/secondary
// (the "accent") changes with [ThemeColor], same as most apps' color-picker settings.
private val NeutralBackgroundLight = Color(0xFFFBFDFF)
private val NeutralSurfaceLight = Color(0xFFFBFDFF)
private val NeutralSurfaceVariantLight = Color(0xFFE1E7EE)
private val NeutralOnBackgroundLight = Color(0xFF1A1C1E)
private val NeutralOnSurfaceLight = Color(0xFF1A1C1E)

private val NeutralBackgroundDark = Color(0xFF11141A)
private val NeutralSurfaceDark = Color(0xFF1A1D24)
private val NeutralSurfaceVariantDark = Color(0xFF404957)
private val NeutralOnBackgroundDark = Color(0xFFE2E4E8)
private val NeutralOnSurfaceDark = Color(0xFFE2E4E8)

/** The handful of colors that change between [ThemeColor] options; everything else in the
 *  scheme (background, surface, etc.) is one of the Neutral* constants above regardless of color. */
private class AccentPalette(
    val lightPrimary: Color, val lightOnPrimary: Color,
    val lightPrimaryContainer: Color, val lightOnPrimaryContainer: Color,
    val lightSecondary: Color, val lightSecondaryContainer: Color,
    val darkPrimary: Color, val darkOnPrimary: Color,
    val darkPrimaryContainer: Color, val darkOnPrimaryContainer: Color,
    val darkSecondary: Color, val darkSecondaryContainer: Color
)

private fun accentPalette(color: ThemeColor): AccentPalette = when (color) {
    ThemeColor.BLUE -> AccentPalette( // the app's original color
        lightPrimary = Color(0xFF1E3A5F), lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFD3E2F5), lightOnPrimaryContainer = Color(0xFF0A2540),
        lightSecondary = Color(0xFF3F5C74), lightSecondaryContainer = Color(0xFFD9E4EE),
        darkPrimary = Color(0xFF9EC1E8), darkOnPrimary = Color(0xFF0A2540),
        darkPrimaryContainer = Color(0xFF20364C), darkOnPrimaryContainer = Color(0xFFBFD8F0),
        darkSecondary = Color(0xFFB6C6D9), darkSecondaryContainer = Color(0xFF2A323C)
    )
    ThemeColor.GREEN -> AccentPalette(
        lightPrimary = Color(0xFF1E5F3A), lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFD3F0DE), lightOnPrimaryContainer = Color(0xFF08351E),
        lightSecondary = Color(0xFF3F7455), lightSecondaryContainer = Color(0xFFD7E8DC),
        darkPrimary = Color(0xFF9EE8B8), darkOnPrimary = Color(0xFF0A4025),
        darkPrimaryContainer = Color(0xFF1F4C33), darkOnPrimaryContainer = Color(0xFFC6F0D6),
        darkSecondary = Color(0xFFB0D9BE), darkSecondaryContainer = Color(0xFF28352C)
    )
    ThemeColor.PURPLE -> AccentPalette(
        lightPrimary = Color(0xFF4B2E83), lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFE4D6F7), lightOnPrimaryContainer = Color(0xFF2A1652),
        lightSecondary = Color(0xFF6A5480), lightSecondaryContainer = Color(0xFFE7DCEF),
        darkPrimary = Color(0xFFCBB2ED), darkOnPrimary = Color(0xFF2A1652),
        darkPrimaryContainer = Color(0xFF3A2760), darkOnPrimaryContainer = Color(0xFFE7D6F7),
        darkSecondary = Color(0xFFCBB9DA), darkSecondaryContainer = Color(0xFF392F44)
    )
    ThemeColor.ORANGE -> AccentPalette(
        lightPrimary = Color(0xFF8A4B14), lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFE0C2), lightOnPrimaryContainer = Color(0xFF3D2200),
        lightSecondary = Color(0xFF82603F), lightSecondaryContainer = Color(0xFFF3E0CC),
        darkPrimary = Color(0xFFFFB77C), darkOnPrimary = Color(0xFF4A2800),
        darkPrimaryContainer = Color(0xFF653A0E), darkOnPrimaryContainer = Color(0xFFFFDCB8),
        darkSecondary = Color(0xFFE9C29B), darkSecondaryContainer = Color(0xFF4A3623)
    )
    ThemeColor.RED -> AccentPalette(
        lightPrimary = Color(0xFF8A1F2B), lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFDADA), lightOnPrimaryContainer = Color(0xFF410E14),
        lightSecondary = Color(0xFF7A4B4F), lightSecondaryContainer = Color(0xFFF3DEDF),
        darkPrimary = Color(0xFFFFB3B7), darkOnPrimary = Color(0xFF4A1116),
        darkPrimaryContainer = Color(0xFF652329), darkOnPrimaryContainer = Color(0xFFFFDADB),
        darkSecondary = Color(0xFFE7BDC0), darkSecondaryContainer = Color(0xFF4A3436)
    )
    ThemeColor.TEAL -> AccentPalette(
        lightPrimary = Color(0xFF0B6E71), lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFC8F0F0), lightOnPrimaryContainer = Color(0xFF002A2B),
        lightSecondary = Color(0xFF4A6C6D), lightSecondaryContainer = Color(0xFFD3E9E9),
        darkPrimary = Color(0xFF8FDADB), darkOnPrimary = Color(0xFF00373A),
        darkPrimaryContainer = Color(0xFF144F51), darkOnPrimaryContainer = Color(0xFFC0F0F0),
        darkSecondary = Color(0xFFB0CDCE), darkSecondaryContainer = Color(0xFF2E4546)
    )
}

/** The color actually shown as this option's swatch in Settings -- its light-theme primary. */
val ThemeColor.swatch: Color
    get() = accentPalette(this).lightPrimary

@Composable
fun TestmondTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: ThemeColor = ThemeColor.BLUE,
    content: @Composable () -> Unit
) {
    val accent = accentPalette(themeColor)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent.darkPrimary,
            onPrimary = accent.darkOnPrimary,
            primaryContainer = accent.darkPrimaryContainer,
            onPrimaryContainer = accent.darkOnPrimaryContainer,
            secondary = accent.darkSecondary,
            secondaryContainer = accent.darkSecondaryContainer,
            background = NeutralBackgroundDark,
            surface = NeutralSurfaceDark,
            surfaceVariant = NeutralSurfaceVariantDark,
            onBackground = NeutralOnBackgroundDark,
            onSurface = NeutralOnSurfaceDark
        )
    } else {
        lightColorScheme(
            primary = accent.lightPrimary,
            onPrimary = accent.lightOnPrimary,
            primaryContainer = accent.lightPrimaryContainer,
            onPrimaryContainer = accent.lightOnPrimaryContainer,
            secondary = accent.lightSecondary,
            secondaryContainer = accent.lightSecondaryContainer,
            background = NeutralBackgroundLight,
            surface = NeutralSurfaceLight,
            surfaceVariant = NeutralSurfaceVariantLight,
            onBackground = NeutralOnBackgroundLight,
            onSurface = NeutralOnSurfaceLight
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
