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
    primary = ES1Primary,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = ES1DarkBackground,
    surface = ES1DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = ES1DarkOnSurface,
    onSurface = ES1DarkOnSurface,
    surfaceVariant = ES1DarkCard,
    onSurfaceVariant = ES1DarkOnSurfaceVariant,
    outline = ES1DarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = ES1Primary,
    secondary = Purple40,
    tertiary = Pink40,
    background = ES1LightBackground,
    surface = ES1LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = ES1LightOnSurface,
    onSurface = ES1LightOnSurface,
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = ES1LightOnSurfaceVariant,
    outline = ES1LightCardBorder
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
