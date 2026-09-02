package com.es1.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val title: String) {
    SYSTEM("Sistema"),
    LIGHT("Chiaro"),
    DARK("Scuro")
}

private val DarkColorScheme = darkColorScheme(
    primary = CyberWhite,
    onPrimary = CyberBlack,
    primaryContainer = CyberDarkGray,
    onPrimaryContainer = CyberWhite,
    secondary = CyberLightGray,
    onSecondary = CyberBlack,
    tertiary = CyberMidGray,
    onTertiary = CyberWhite,
    background = CyberBlack,
    onBackground = CyberWhite,
    surface = CyberDarkSurface,
    onSurface = CyberWhite,
    surfaceVariant = CyberDarkCard,
    onSurfaceVariant = CyberLightGray,
    outline = CyberDarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SurfingCoral,
    onPrimary = Color.White,
    primaryContainer = SurfingCoralContainer,
    onPrimaryContainer = SurfingCoralOnContainer,
    secondary = SurfingBlack,
    onSecondary = Color.White,
    tertiary = SurfingGray,
    onTertiary = Color.White,
    background = SurfingWhite,
    onBackground = SurfingBlack,
    surface = SurfingLightSurface,
    onSurface = SurfingBlack,
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = SurfingGray,
    outline = SurfingLightCardBorder
)

@Composable
fun ES1CompanionTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
