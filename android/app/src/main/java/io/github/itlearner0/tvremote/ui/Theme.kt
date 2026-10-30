package io.github.itlearner0.tvremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFE4002B)
val AccentSoft = Color(0xFFFF4D6A)
val KeyColor = Color(0xFF2C3039)
val KeyPressed = Color(0xFF3D4350)
val PadColor = Color(0xFF181B21)
val BodyColor = Color(0xFF1F2229)
val BorderColor = Color(0xFF2E323B)
val Muted = Color(0xFF9AA0AB)
val OnColor = Color(0xFF3CCF7A)

/** Fond de l'application : léger dégradé vertical. */
val BackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF14161B), Color(0xFF0D0F12)))

/** Boîtier de la télécommande. */
val BodyBrush = Brush.verticalGradient(listOf(Color(0xFF262A32), Color(0xFF1B1E24)))

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF111317),
    onBackground = Color(0xFFEEF0F4),
    surface = BodyColor,
    onSurface = Color(0xFFEEF0F4),
    surfaceVariant = KeyColor,
    onSurfaceVariant = Muted,
    surfaceContainerHigh = Color(0xFF262A32),
    outline = BorderColor,
    error = Color(0xFFFF6B6B),
)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
