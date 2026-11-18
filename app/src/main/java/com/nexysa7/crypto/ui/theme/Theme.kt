package com.nexysa7.crypto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NexysGreen,
    onPrimary = NexysDark,
    secondary = NexysTeal,
    background = NexysDark,
    onBackground = NexysLight,
    surface = NexysDarkSurface,
    onSurface = NexysLight,
)

private val LightColors = lightColorScheme(
    primary = NexysGreenDark,
    onPrimary = NexysSurface,
    secondary = NexysTealDark,
    background = NexysBackground,
    onBackground = NexysDark,
    surface = NexysSurface,
    onSurface = NexysDark,
)

@Composable
fun NexysA7Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NexysTypography,
        content = content,
    )
}
