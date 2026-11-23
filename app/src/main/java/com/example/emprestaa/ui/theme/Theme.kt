package com.example.emprestaa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = SoftBlue,
    onPrimaryContainer = DeepBlue,
    secondary = AvailableGreen,
    onSecondary = Color.White,
    tertiary = PendingOrange,
    onTertiary = Color.White,
    error = ErrorRed,
    onError = Color.White,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = SoftBlue,
    onSurfaceVariant = TextSecondary,
    outline = BorderNeutral,
    outlineVariant = BorderNeutral
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8EAEFF),
    onPrimary = Color(0xFF102658),
    primaryContainer = Color(0xFF253B70),
    onPrimaryContainer = Color(0xFFE8F0FF),
    secondary = Color(0xFF65C59D),
    onSecondary = Color(0xFF062C1F),
    tertiary = Color(0xFFF0B96C),
    onTertiary = Color(0xFF392100),
    error = Color(0xFFFF9298),
    onError = Color(0xFF4B0713),
    background = DarkBackground,
    onBackground = Color(0xFFF1F4FA),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F4FA),
    surfaceVariant = Color(0xFF202D45),
    onSurfaceVariant = DarkMuted,
    outline = Color(0xFF46536B),
    outlineVariant = Color(0xFF35435C)
)

// Contraste alto: textos em preto/branco puro e bordas bem visíveis (razão de contraste acima de 7:1
// para textos e acima de 4,5:1 para bordas). Usado quando "Contraste alto" está ligado em Configurações.
private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF0033A0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A5C),
    secondary = Color(0xFF0B5F3F),
    onSecondary = Color.White,
    tertiary = Color(0xFF7A3E00),
    onTertiary = Color.White,
    error = Color(0xFF9B0A1A),
    onError = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE6ECFA),
    onSurfaceVariant = Color(0xFF1F2937),
    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF4B5563)
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8CCFF),
    onPrimary = Color(0xFF00153D),
    primaryContainer = Color(0xFF1B2F66),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF8FE6BE),
    onSecondary = Color(0xFF00210F),
    tertiary = Color(0xFFFFD39A),
    onTertiary = Color(0xFF2B1700),
    error = Color(0xFFFFB3B8),
    onError = Color(0xFF3A000A),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0B0F1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1D2538),
    onSurfaceVariant = Color(0xFFE3E9F5),
    outline = Color(0xFFC9D3E6),
    outlineVariant = Color(0xFFA9B6CE)
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