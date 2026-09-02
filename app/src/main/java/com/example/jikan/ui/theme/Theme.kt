package com.example.jikan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentDark,
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = SageSoft,
    onSecondaryContainer = Sage,
    tertiary = Vermillion,
    onTertiary = Color.White,
    tertiaryContainer = VermillionSoft,
    onTertiaryContainer = Vermillion,
    error = Vermillion,
    onError = Color.White,
    errorContainer = VermillionSoft,
    onErrorContainer = Vermillion,
    background = Bg,
    onBackground = Ink,
    surface = Bg,
    onSurface = Ink,
    surfaceVariant = AccentSoft,
    onSurfaceVariant = InkSoft,
    outline = InkFaint,
)

/**
 * The reference design (see project-context.md's screen flow + the mockups it was
 * built from) is a single warm light palette with no dark variant, so this always
 * renders light regardless of the system theme — matching a branded app rather than
 * following the device setting.
 */
@Composable
fun JikanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}