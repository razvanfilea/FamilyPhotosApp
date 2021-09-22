package net.theluckycoder.familyphotos.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val primary = Color(0xFF4caf50)
val primaryVariant = Color(0xFF087f23)
val secondary = Color(0xFFffea00)
val secondaryVariant = Color(0xFFc7b800)

private val DarkColorPalette = darkColors(
    primary = primary,
    primaryVariant = primaryVariant,
    secondary = secondary,
    secondaryVariant = secondaryVariant,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) =
    MaterialTheme(
        colors = DarkColorPalette,
        content = content
    )
