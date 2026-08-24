package com.es1.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ES1Primary,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = ES1DarkBackground,
    surface = ES1DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = ES1DarkCard,
    onSurfaceVariant = Color(0xFFCCCCCC)
)

@Composable
fun ES1CompanionTheme(
    darkTheme: Boolean = true, // Defaulting to sleek dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
