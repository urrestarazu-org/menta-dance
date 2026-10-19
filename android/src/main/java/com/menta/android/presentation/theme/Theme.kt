package com.menta.android.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3D6D),
    secondary = Color(0xFF6B5D7A),
    tertiary = Color(0xFF8A5A44),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD6B8E8),
    secondary = Color(0xFFC9B8D5),
    tertiary = Color(0xFFF0B8A0),
)

@Composable
fun MentaDanceTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
