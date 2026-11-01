package io.github.itlearner0.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

// Identité OmniZap : bleu nuit, accent bleu électrique → cyan, rouge réservé à la mise en marche.
val Accent = Color(0xFF3D6BFF)
val AccentSoft = Color(0xFF22C8F0)
val PowerRed = Color(0xFFFF4D5E)
val KeyColor = Color(0xFF232838)
val KeyPressed = Color(0xFF323A52)
val PadColor = Color(0xFF141826)
val BodyColor = Color(0xFF191E2C)
val BorderColor = Color(0xFF2A3147)
val Muted = Color(0xFF98A2B8)
val OnColor = Color(0xFF34D399)

/** Dégradé de marque (boutons principaux, éléments choisis). */
val AccentBrush = Brush.horizontalGradient(listOf(Accent, AccentSoft))

/** Fond de l'application : bleu nuit. */
val BackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF0E1222), Color(0xFF080A12)))

/** Boîtier de la télécommande. */
val BodyBrush = Brush.verticalGradient(listOf(Color(0xFF1D2335), Color(0xFF151A28)))

/** Fond de l'application avec la lueur de marque en haut de l'écran. */
fun Modifier.appBackground(): Modifier = background(BackgroundBrush).drawBehind {
    drawRect(
        Brush.radialGradient(
            listOf(Accent.copy(alpha = 0.32f), AccentSoft.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 0.25f, 0f),
            radius = size.width * 1.15f,
        ),
    )
}

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentSoft,
    background = Color(0xFF0B0E19),
    onBackground = Color(0xFFEEF0F4),
    surface = BodyColor,
    onSurface = Color(0xFFEEF0F4),
    surfaceVariant = KeyColor,
    onSurfaceVariant = Muted,
    surfaceContainerHigh = Color(0xFF1D2335),
    secondaryContainer = Color(0xFF253055),
    onSecondaryContainer = Color(0xFFDDE5FF),
    outline = BorderColor,
    error = Color(0xFFFF6B6B),
)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
