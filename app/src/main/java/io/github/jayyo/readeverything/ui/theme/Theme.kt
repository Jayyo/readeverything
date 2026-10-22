package io.github.jayyo.readeverything.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A6DF4),
    secondary = Color(0xFF4C6A92),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC4FF),
    secondary = Color(0xFFB4C8E8),
)

@Composable
fun ReadeverythingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
