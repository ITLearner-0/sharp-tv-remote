package io.github.itlearner0.tvremote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bouton principal OmniZap : pilule en dégradé de marque. */
@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(28.dp))
            .background(AccentBrush)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Icône dans un disque de marque, entourée d'ondes qui s'élargissent (recherche
 * en cours) quand [active] est vrai.
 */
@Composable
fun RadarIcon(icon: ImageVector, active: Boolean, diameter: Dp = 84.dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter * 2.2f)) {
        if (active) {
            val transition = rememberInfiniteTransition(label = "radar")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
                label = "radar-progress",
            )
            Canvas(Modifier.fillMaxSize()) {
                val base = diameter.toPx() / 2
                val max = size.minDimension / 2
                for (i in 0 until 3) {
                    val p = (progress + i / 3f) % 1f
                    val radius = base + (max - base) * p
                    drawCircle(
                        color = AccentSoft.copy(alpha = (1f - p) * 0.55f),
                        radius = radius,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(color = Accent.copy(alpha = (1f - p) * 0.10f), radius = radius)
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AccentSoft, Accent))),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(diameter / 2))
        }
    }
}

/** Coche verte qui apparaît avec un petit rebond (« TV trouvée ! »). */
@Composable
fun SuccessCheck(size: Dp = 28.dp) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).scale(scale.value).clip(CircleShape).background(OnColor),
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.65f))
    }
}
