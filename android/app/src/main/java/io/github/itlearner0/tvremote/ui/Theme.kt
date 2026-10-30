package io.github.itlearner0.tvremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFE4002B)
val KeyColor = Color(0xFF31353E)
val PadColor = Color(0xFF1B1D22)
val BodyColor = Color(0xFF22252C)
val Muted = Color(0xFF9AA0AB)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Color(0xFF16181D),
    onBackground = Color(0xFFEEF0F4),
    surface = BodyColor,
    onSurface = Color(0xFFEEF0F4),
    surfaceVariant = KeyColor,
    onSurfaceVariant = Muted,
    error = Color(0xFFFF6B6B),
)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
