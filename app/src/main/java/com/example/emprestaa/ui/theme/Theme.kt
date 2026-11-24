package com.example.emprestaa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = BrandWhite,
    primaryContainer = SoftBlue,
    onPrimaryContainer = BrandNavy,
    secondary = BrandYellow,
    onSecondary = BrandNavy,
    secondaryContainer = Color(0xFFFFF2C9),
    onSecondaryContainer = Color(0xFF3D2B00),
    tertiary = InfoBlue,
    onTertiary = BrandWhite,
    error = ErrorRed,
    onError = BrandWhite,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = SoftBlue,
    onSurfaceVariant = TextSecondary,
    outline = BorderNeutral,
    outlineVariant = BorderNeutral
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandNavy,
    onPrimary = BrandWhite,
    primaryContainer = Color(0xFF173B64),
    onPrimaryContainer = Color(0xFFEAF2FC),
    secondary = BrandYellow,
    onSecondary = Color(0xFF302200),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE7A0),
    tertiary = Color(0xFF9BB8D8),
    onTertiary = Color(0xFF102640),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = DarkBackground,
    onBackground = Color(0xFFF0F4F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF0F4F9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    outline = Color(0xFF526B84),
    outlineVariant = Color(0xFF344D67)
)

// Esquemas de alto contraste preservam a identidade e reforçam leitura e contornos.
private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF06234A), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8F7), onPrimaryContainer = Color(0xFF00152F),
    secondary = Color(0xFFFFC928), onSecondary = Color(0xFF171000),
    secondaryContainer = Color(0xFFFFE9A3), onSecondaryContainer = Color(0xFF211700),
    tertiary = Color(0xFF365D85), onTertiary = Color.White,
    error = Color(0xFF9B1C13), onError = Color.White,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFE7EEF7), onSurfaceVariant = Color(0xFF172B43),
    outline = Color(0xFF34465B), outlineVariant = Color(0xFF34465B)
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFF174477), onPrimary = Color.White,
    primaryContainer = Color(0xFF244D7A), onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFD54F), onSecondary = Color(0xFF171000),
    secondaryContainer = Color(0xFF624A00), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFB8D7F5), onTertiary = Color(0xFF001A31),
    error = Color(0xFFFFB4AB), onError = Color(0xFF410002),
    background = Color(0xFF000D1A), onBackground = Color.White,
    surface = Color(0xFF07182B), onSurface = Color.White,
    surfaceVariant = Color(0xFF183653), onSurfaceVariant = Color(0xFFE4EDF7),
    outline = Color(0xFFD1E2F4), outlineVariant = Color(0xFFB7CFE8)
)

@Composable
fun EmprestaAêTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    altoContraste: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = when {
            altoContraste && darkTheme -> HighContrastDarkColorScheme
            altoContraste -> HighContrastLightColorScheme
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        },
        typography = Typography,
        content = content
    )
}
