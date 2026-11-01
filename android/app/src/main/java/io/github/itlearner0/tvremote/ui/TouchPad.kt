package io.github.itlearner0.tvremote.ui

import io.github.itlearner0.tvremote.i18n.tr

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itlearner0.tvremote.protocol.Keys
import kotlin.math.abs

private const val PREFS = "ui"
private const val KEY_TOUCHPAD = "touchpad"

fun touchpadMode(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TOUCHPAD, false)

fun setTouchpadMode(context: Context, on: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TOUCHPAD, on).apply()
}

/** Bascule « flèches / pavé tactile », en forme de pilule. */
@Composable
fun NavModeToggle(touchpad: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(PadColor)
            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToggleChoice(Icons.Filled.Gamepad, tr("Flèches"), selected = !touchpad) { onChange(false) }
        ToggleChoice(Icons.Filled.TouchApp, tr("Pavé tactile"), selected = touchpad) { onChange(true) }
    }
}

@Composable
private fun ToggleChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.background(AccentBrush) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else Muted, modifier = Modifier.size(18.dp))
        Text("  $label", color = if (selected) Color.White else Muted, fontSize = 13.sp)
    }
}

/**
 * Pavé tactile : glisser déplace la sélection sur la TV (une flèche tous les
 * ~44 dp, plusieurs pour un long glissement), toucher = OK. Le doigt laisse un
 * halo lumineux ; chaque pas fait vibrer légèrement le téléphone.
 */
@Composable
fun TouchPad(onKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val step = with(LocalDensity.current) { 44.dp.toPx() }
    val slop = with(LocalDensity.current) { 10.dp.toPx() }
    var touch by remember { mutableStateOf<Offset?>(null) }
    val glow by animateFloatAsState(if (touch != null) 1f else 0f, label = "glow")
    var lastTouch by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1A2032), PadColor)))
            .border(1.dp, BorderColor, RoundedCornerShape(32.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touch = down.position
                    lastTouch = down.position
                    var moved = false
                    var accX = 0f
                    var accY = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        change.consume()
                        touch = change.position
                        lastTouch = change.position
                        accX += delta.x
                        accY += delta.y
                        if (!moved && maxOf(abs(accX), abs(accY)) > slop) moved = true
                        if (moved) {
                            // Une flèche par pas, dans l'axe dominant.
                            while (abs(accX) >= step || abs(accY) >= step) {
                                if (abs(accX) >= abs(accY)) {
                                    onKey(if (accX > 0) Keys.DPAD_RIGHT else Keys.DPAD_LEFT)
                                    accX -= if (accX > 0) step else -step
                                } else {
                                    onKey(if (accY > 0) Keys.DPAD_DOWN else Keys.DPAD_UP)
                                    accY -= if (accY > 0) step else -step
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    if (!moved) {
                        onKey(Keys.DPAD_CENTER)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    touch = null
                }
            },
    ) {
        // Grille de points discrète et halo sous le doigt.
        Canvas(Modifier.fillMaxSize()) {
            val gap = 22.dp.toPx()
            var y = gap
            while (y < size.height) {
                var x = gap
                while (x < size.width) {
                    drawCircle(Color.White.copy(alpha = 0.06f), radius = 1.2.dp.toPx(), center = Offset(x, y))
                    x += gap
                }
                y += gap
            }
            if (glow > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(AccentSoft.copy(alpha = 0.45f * glow), Accent.copy(alpha = 0.15f * glow), Color.Transparent),
                        center = lastTouch,
                        radius = 90.dp.toPx(),
                    ),
                    radius = 90.dp.toPx(),
                    center = lastTouch,
                )
            }
        }
        val hint = Modifier.alpha(0.35f)
        Icon(Icons.Filled.KeyboardArrowUp, null, tint = Color.White, modifier = hint.align(Alignment.TopCenter).padding(10.dp))
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White, modifier = hint.align(Alignment.BottomCenter).padding(10.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White, modifier = hint.align(Alignment.CenterStart).padding(10.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = hint.align(Alignment.CenterEnd).padding(10.dp))
        Column(
            Modifier.align(Alignment.Center).alpha(1f - glow * 0.7f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.TouchApp, null, tint = AccentSoft, modifier = Modifier.size(40.dp))
            Text(tr("Glissez pour naviguer"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text(tr("Touchez pour OK"), color = Muted, fontSize = 13.sp)
        }
    }
}
