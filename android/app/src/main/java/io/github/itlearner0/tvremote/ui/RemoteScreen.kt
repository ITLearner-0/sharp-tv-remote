package io.github.itlearner0.tvremote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itlearner0.tvremote.data.TvApp
import io.github.itlearner0.tvremote.protocol.Keys
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RemoteScreen(state: UiState, vm: RemoteViewModel, onSettings: () -> Unit) {
    val offline = state.connection != Connection.CONNECTED
    val dim = Modifier.alpha(if (offline) 0.4f else 1f)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StatusBar(state, onSettings)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey("⏻", Modifier.weight(1f), color = Accent, fontSize = 22.sp, onPress = vm::power)
            RemoteKey("Source", Modifier.weight(1f).then(dim)) { vm.key(Keys.TV_INPUT) }
            RemoteKey("Réglages", Modifier.weight(1f).then(dim)) { vm.key(Keys.SETTINGS) }
        }

        DPad(vm, dim)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = dim) {
            RemoteKey("↩ Retour", Modifier.weight(1f)) { vm.key(Keys.BACK) }
            RemoteKey("⌂ Accueil", Modifier.weight(1f)) { vm.key(Keys.HOME) }
            RemoteKey("☰ Menu", Modifier.weight(1f)) { vm.key(Keys.MENU) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = dim.height(170.dp)) {
            Rocker("VOL", "＋", "－", Modifier.weight(1f), { vm.key(Keys.VOLUME_UP) }, { vm.key(Keys.VOLUME_DOWN) })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteKey("🔇", Modifier.fillMaxWidth().weight(1f)) { vm.key(Keys.VOLUME_MUTE) }
                RemoteKey("Guide", Modifier.fillMaxWidth().weight(1f)) { vm.key(Keys.GUIDE) }
                RemoteKey("Info", Modifier.fillMaxWidth().weight(1f)) { vm.key(Keys.INFO) }
            }
            Rocker("CH", "⌃", "⌄", Modifier.weight(1f), { vm.key(Keys.CHANNEL_UP) }, { vm.key(Keys.CHANNEL_DOWN) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = dim) {
            RemoteKey("⏪", Modifier.weight(1f)) { vm.key(Keys.MEDIA_REWIND) }
            RemoteKey("⏯", Modifier.weight(1f)) { vm.key(Keys.MEDIA_PLAY_PAUSE) }
            RemoteKey("⏹", Modifier.weight(1f)) { vm.key(Keys.MEDIA_STOP) }
            RemoteKey("⏩", Modifier.weight(1f)) { vm.key(Keys.MEDIA_FAST_FORWARD) }
        }

        Drawer("Pavé numérique et touches couleur", dim) { NumPad(vm) }
        Drawer("Applications", dim) { Apps(state, vm) }
        TextInput(vm, dim)
    }
}

@Composable
private fun StatusBar(state: UiState, onSettings: () -> Unit) {
    val tv = state.tv
    val text = when (state.connection) {
        Connection.CONNECTED -> {
            val name = listOfNotNull(tv.device?.manufacturer, tv.device?.model).joinToString(" ").ifBlank { "TV" }
            if (!tv.isOn) "$name · en veille" else name
        }
        else -> "TV éteinte · ⏻ pour l'allumer"
    }
    val on = state.connection == Connection.CONNECTED && tv.isOn
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) Color(0xFF3CCF7A) else Color(0xFF777777)))
        Text(
            text,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Réglages") }
    }
}

/** Touche de télécommande : vibration au toucher, répétition si [repeat] et maintenue. */
@Composable
fun RemoteKey(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = KeyColor,
    shape: Shape = RoundedCornerShape(12.dp),
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    repeat: Boolean = false,
    onPress: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (pressed) color.copy(alpha = 0.6f) else color)
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnPress()
                    var repeater: Job? = null
                    if (repeat) {
                        repeater = scope.launch {
                            delay(400)
                            while (true) {
                                currentOnPress()
                                delay(120)
                            }
                        }
                    }
                    waitForUpOrCancellation()
                    repeater?.cancel()
                    pressed = false
                }
            },
    ) {
        Text(label, fontSize = fontSize, color = Color.White)
    }
}

@Composable
private fun DPad(vm: RemoteViewModel, dim: Modifier) {
    Box(
        Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(CircleShape).background(PadColor).then(dim),
    ) {
        val arrow = Modifier.size(72.dp)
        PadKey("▲", arrow.align(Alignment.TopCenter).padding(top = 6.dp)) { vm.key(Keys.DPAD_UP) }
        PadKey("▼", arrow.align(Alignment.BottomCenter).padding(bottom = 6.dp)) { vm.key(Keys.DPAD_DOWN) }
        PadKey("◀", arrow.align(Alignment.CenterStart).padding(start = 6.dp)) { vm.key(Keys.DPAD_LEFT) }
        PadKey("▶", arrow.align(Alignment.CenterEnd).padding(end = 6.dp)) { vm.key(Keys.DPAD_RIGHT) }
        RemoteKey(
            "OK",
            Modifier.align(Alignment.Center).size(96.dp),
            shape = CircleShape,
            fontSize = 22.sp,
        ) { vm.key(Keys.DPAD_CENTER) }
    }
}

@Composable
private fun PadKey(label: String, modifier: Modifier, onPress: () -> Unit) {
    RemoteKey(label, modifier, color = PadColor, shape = CircleShape, fontSize = 22.sp, repeat = true, onPress = onPress)
}

@Composable
private fun Rocker(
    label: String,
    up: String,
    down: String,
    modifier: Modifier,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(KeyColor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteKey(up, Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(0.dp), fontSize = 24.sp, repeat = true, onPress = onUp)
        Text(label, color = Muted, fontSize = 12.sp)
        RemoteKey(down, Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(0.dp), fontSize = 24.sp, repeat = true, onPress = onDown)
    }
}

@Composable
private fun Drawer(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PadColor).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            (if (open) "▾ " else "▸ ") + title,
            color = Muted,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp),
        )
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 10.dp)) { content() }
        }
    }
}

@Composable
private fun NumPad(vm: RemoteViewModel) {
    val rows = listOf(
        listOf("1" to Keys.digit(1), "2" to Keys.digit(2), "3" to Keys.digit(3)),
        listOf("4" to Keys.digit(4), "5" to Keys.digit(5), "6" to Keys.digit(6)),
        listOf("7" to Keys.digit(7), "8" to Keys.digit(8), "9" to Keys.digit(9)),
        listOf("ST" to Keys.CAPTIONS, "0" to Keys.digit(0), "⟲" to Keys.LAST_CHANNEL),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, key) in row) RemoteKey(label, Modifier.weight(1f), fontSize = 20.sp) { vm.key(key) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            ColorKey(Color(0xFFD93636)) { vm.key(Keys.PROG_RED) }
            ColorKey(Color(0xFF2FA84F)) { vm.key(Keys.PROG_GREEN) }
            ColorKey(Color(0xFFE0B31B)) { vm.key(Keys.PROG_YELLOW) }
            ColorKey(Color(0xFF2F6FD6)) { vm.key(Keys.PROG_BLUE) }
        }
    }
}

@Composable
private fun RowScope.ColorKey(color: Color, onPress: () -> Unit) {
    RemoteKey("", Modifier.weight(1f).height(24.dp), color = color, onPress = onPress)
}

@Composable
private fun Apps(state: UiState, vm: RemoteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in state.apps.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (app in row) AppButton(app, Modifier.weight(1f)) { vm.launch(app) }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        FilledTonalButton(
            onClick = vm::scanApps,
            enabled = !state.scanningApps,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text(if (state.scanningApps) "Lecture en cours…" else "🔄  Lister toutes les applications de la TV") }
        if (state.scanningApps) LinearProgressIndicator(Modifier.fillMaxWidth())
        val hint = state.appsMessage ?: if (state.appsComplete) null else
            "Seules les applications courantes et celles déjà ouvertes sur la TV s'affichent. " +
                "Pour toutes les voir, activez le « Débogage USB » sur la TV, puis touchez le bouton ci-dessus."
        hint?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AppButton(app: TvApp, modifier: Modifier, onClick: () -> Unit) {
    val hue = (app.packageName.sumOf { it.code } % 360).toFloat()
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(KeyColor).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.hsl(hue, 0.55f, 0.42f)),
            contentAlignment = Alignment.Center,
        ) { Text(app.label.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White) }
        Text(app.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TextInput(vm: RemoteViewModel, modifier: Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotEmpty()) {
            vm.text(text)
            text = ""
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Taper du texte sur la TV…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = send) { Text("Envoyer") }
        RemoteKey("⌫", Modifier.size(48.dp), repeat = true) { vm.key(Keys.DEL) }
    }
}
