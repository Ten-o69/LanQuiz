package io.github.ten_o69.lanquiz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = NeonBlue,
    secondary = NeonAmber,
    tertiary = NeonGreen,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceAlt,
    onPrimary = LightOn,
    onSecondary = LightOn,
    onTertiary = LightOn,
    onBackground = LightOn,
    onSurface = LightOn,
    onSurfaceVariant = LightMuted,
    outline = LightMuted
)

private val DarkColors = darkColorScheme(
    primary = NeonBlue,
    secondary = NeonAmber,
    tertiary = NeonGreen,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceAlt,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onTertiary = DarkBackground,
    onBackground = DarkOn,
    onSurface = DarkOn,
    onSurfaceVariant = DarkMuted,
    outline = DarkMuted
)

@Composable
fun LanQuizTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
