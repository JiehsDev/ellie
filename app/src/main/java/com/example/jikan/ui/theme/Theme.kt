package com.example.jikan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.jikan.data.ThemeMode

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

private val DarkColorScheme = darkColorScheme(
    primary = AccentDarkMode,
    onPrimary = Color(0xFF18213A),
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = InkDark,
    secondary = SageDarkMode,
    onSecondary = Color(0xFF172416),
    secondaryContainer = SageContainerDark,
    onSecondaryContainer = InkDark,
    tertiary = VermillionDarkMode,
    onTertiary = Color(0xFF35110B),
    tertiaryContainer = VermillionContainerDark,
    onTertiaryContainer = InkDark,
    error = VermillionDarkMode,
    onError = Color(0xFF35110B),
    errorContainer = VermillionContainerDark,
    onErrorContainer = InkDark,
    background = BgDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = AccentContainerDark,
    onSurfaceVariant = InkSoftDark,
    outline = InkFaintDark,
)

@Composable
fun JikanTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
