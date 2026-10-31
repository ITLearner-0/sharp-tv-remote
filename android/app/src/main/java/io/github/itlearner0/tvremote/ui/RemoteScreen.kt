package io.github.itlearner0.tvremote.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import io.github.itlearner0.tvremote.cast.CastActivity
import io.github.itlearner0.tvremote.cast.ScreenMirroring
import io.github.itlearner0.tvremote.cast.MirrorActivity
import io.github.itlearner0.tvremote.cast.MirrorService
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import io.github.itlearner0.tvremote.protocol.RemoteSession
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itlearner0.tvremote.protocol.Keys
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RemoteScreen(state: UiState, vm: RemoteViewModel, onSettings: () -> Unit, onSwitchTv: () -> Unit) {
    // Avec l'infrarouge en secours, les touches restent utilisables même hors réseau.
    val offline = state.connection != Connection.CONNECTED && !state.irFallback
    val dim = Modifier.alpha(if (offline) 0.45f else 1f)
    var chooser by remember { mutableStateOf<CustomKey?>(null) }
    chooser?.let { key ->
        CustomKeyDialog(key, state.customKeys[key] ?: key.default, vm, onDismiss = { chooser = null })
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header(state, onSettings, onSwitchTv)

        if (state.needsPairing) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PadColor).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("La TV ne reconnaît plus ce téléphone.", color = MaterialTheme.colorScheme.error)
                FilledTonalButton(onClick = vm::repairCurrentTv, modifier = Modifier.fillMaxWidth()) {
                    Text("Refaire l'appairage")
                }
            }
        }

        // Boîtier de la télécommande
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(BodyBrush)
                .border(1.dp, BorderColor, RoundedCornerShape(36.dp))
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundKey(Icons.Filled.PowerSettingsNew, "Marche", color = Accent, onPress = vm::power)
                RoundKey(Icons.Filled.NightsStay, "Veille", dim, onPress = vm::sleep)
                RoundKey(Icons.Filled.Input, "Source", dim) { vm.key(Keys.TV_INPUT) }
                RoundKey(Icons.Filled.Tune, "Réglages", dim) { vm.key(Keys.SETTINGS) }
            }

            DPad(vm, dim)

            Row(Modifier.fillMaxWidth().then(dim), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundKey(Icons.Filled.ArrowBack, "Retour") { vm.key(Keys.BACK) }
                RoundKey(Icons.Filled.Home, "Accueil") { vm.key(Keys.HOME) }
                VoiceKey(state, vm)
                RoundKey(Icons.Filled.Menu, "Menu", onLongPress = { chooser = CustomKey.MENU }) { vm.customKey(CustomKey.MENU) }
            }

            Row(
                Modifier.fillMaxWidth().height(176.dp).then(dim),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Rocker("VOL", Icons.Filled.Add, Icons.Filled.Remove, { vm.key(Keys.VOLUME_UP) }, { vm.key(Keys.VOLUME_DOWN) })
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconKey(Icons.Filled.VolumeOff, "Muet", Modifier.fillMaxWidth().weight(1f)) { vm.key(Keys.VOLUME_MUTE) }
                    IconKey(
                        Icons.Filled.LiveTv,
                        "Guide",
                        Modifier.fillMaxWidth().weight(1f),
                        onLongPress = { chooser = CustomKey.GUIDE },
                    ) { vm.customKey(CustomKey.GUIDE) }
                    IconKey(Icons.Filled.Info, "Info", Modifier.fillMaxWidth().weight(1f)) { vm.key(Keys.INFO) }
                }
                Rocker(
                    "CH",
                    Icons.Filled.KeyboardArrowUp,
                    Icons.Filled.KeyboardArrowDown,
                    { vm.key(Keys.CHANNEL_UP) },
                    { vm.key(Keys.CHANNEL_DOWN) },
                )
            }

            Row(Modifier.fillMaxWidth().then(dim), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconKey(Icons.Filled.FastRewind, "Retour rapide", Modifier.weight(1f)) { vm.key(Keys.MEDIA_REWIND) }
                IconKey(Icons.Filled.PlayArrow, "Lecture / pause", Modifier.weight(1f)) { vm.key(Keys.MEDIA_PLAY_PAUSE) }
                IconKey(Icons.Filled.Stop, "Stop", Modifier.weight(1f)) { vm.key(Keys.MEDIA_STOP) }
                IconKey(Icons.Filled.FastForward, "Avance rapide", Modifier.weight(1f)) { vm.key(Keys.MEDIA_FAST_FORWARD) }
            }
        }

        ExtraPanels(state, vm, dim)
    }
}

/**
 * Micro : la reconnaissance vocale du téléphone comprend la phrase, puis
 * « lance Netflix » ouvre l'application et toute autre phrase lance une
 * recherche sur la TV. Appui long : son envoyé à l'Assistant de la TV
 * (seulement sur les TV qui l'acceptent).
 */
@Composable
private fun VoiceKey(state: UiState, vm: RemoteViewModel) {
    val context = LocalContext.current
    val tvVoice = state.tv.features and RemoteSession.FEATURE_VOICE != 0 && !state.usesIr
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::voiceQuery)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleVoice() else Toast.makeText(context, "Autorisez le micro pour la recherche vocale", Toast.LENGTH_SHORT).show()
    }
    RoundKey(
        Icons.Filled.Mic,
        if (state.listening) "Parlez…" else "Voix",
        color = if (state.listening) Accent else KeyColor,
        onLongPress = {
            when {
                !tvVoice -> Toast.makeText(context, "Cette TV n'accepte pas le son du micro : touchez simplement Voix", Toast.LENGTH_LONG).show()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                    vm.toggleVoice()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    ) {
        if (state.listening) {
            vm.stopVoice()
            return@RoundKey
        }
        runCatching {
            dictation.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "« Lance Netflix », « films d'action »…"),
            )
        }.onFailure {
            Toast.makeText(context, "Reconnaissance vocale indisponible sur ce téléphone", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun Header(state: UiState, onSettings: () -> Unit, onSwitchTv: () -> Unit) {
    val tv = state.tv
    val status = when {
        state.isIr -> "Infrarouge · visez la TV"
        state.usesIr -> "Hors réseau · infrarouge (visez la TV)"
        state.connection != Connection.CONNECTED -> "Éteinte · appuyez sur Marche"
        !tv.isOn -> "En veille"
        else -> "Allumée"
    }
    val on = state.isIr || state.connection == Connection.CONNECTED && tv.isOn
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(PadColor)
                .clickable(onClick = onSwitchTv)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) OnColor else Color(0xFF6B7078)))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    state.current?.name ?: "TV",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(status, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Changer de TV", tint = Muted)
        }
        IconButton(onClick = onSettings, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Réglages de la TV", tint = Muted)
        }
    }
}

/**
 * Surface pressable : vibration au toucher, répétition si [repeat] et
 * maintenue (volume, flèches…).
 */
@Composable
fun PressSurface(
    modifier: Modifier,
    shape: Shape,
    color: Color = KeyColor,
    brush: Brush? = null,
    repeat: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onPress: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val background = when {
        pressed -> Modifier.background(KeyPressed)
        brush != null -> Modifier.background(brush)
        else -> Modifier.background(color)
    }
    Box(
        contentAlignment = Alignment.Center,
        content = content,
        modifier = modifier
            .clip(shape)
            .then(background)
            .pointerInput(repeat, onLongPress != null) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val longPress = currentOnLongPress
                    if (longPress != null) {
                        // Touche à appui long : la commande part au relâchement si l'appui est court.
                        var long = false
                        val timer = scope.launch {
                            delay(550)
                            long = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            longPress()
                        }
                        val up = waitForUpOrCancellation()
                        timer.cancel()
                        pressed = false
                        if (!long && up != null) currentOnPress()
                        return@awaitEachGesture
                    }
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
    )
}

/** Bouton rond avec légende (Marche, Retour, Accueil…). */
@Composable
private fun RoundKey(
    icon: ImageVector,
    caption: String,
    modifier: Modifier = Modifier,
    color: Color = KeyColor,
    size: Dp = 56.dp,
    onLongPress: (() -> Unit)? = null,
    onPress: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PressSurface(Modifier.size(size), CircleShape, color = color, onLongPress = onLongPress, onPress = onPress) {
            Icon(icon, contentDescription = caption, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(caption, color = Muted, fontSize = 11.sp)
    }
}

/** Touche rectangulaire à icône. */
@Composable
private fun IconKey(
    icon: ImageVector,
    description: String,
    modifier: Modifier,
    repeat: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onPress: () -> Unit,
) {
    PressSurface(modifier.height(48.dp), RoundedCornerShape(14.dp), repeat = repeat, onLongPress = onLongPress, onPress = onPress) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

/** Touche rectangulaire à texte (pavé numérique). */
@Composable
private fun TextKey(label: String, modifier: Modifier, fontSize: TextUnit = 20.sp, onPress: () -> Unit) {
    PressSurface(modifier.height(52.dp), RoundedCornerShape(14.dp), onPress = onPress) {
        Text(label, fontSize = fontSize, color = Color.White)
    }
}

@Composable
private fun DPad(vm: RemoteViewModel, dim: Modifier) {
    Box(
        Modifier
            .fillMaxWidth(0.84f)
            .aspectRatio(1f)
            .then(dim)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2A2E37), PadColor)))
            .border(1.dp, BorderColor, CircleShape),
    ) {
        val arrow = Modifier.fillMaxSize(0.3f)
        PadArrow(Icons.Filled.KeyboardArrowUp, "Haut", arrow.align(Alignment.TopCenter)) { vm.key(Keys.DPAD_UP) }
        PadArrow(Icons.Filled.KeyboardArrowDown, "Bas", arrow.align(Alignment.BottomCenter)) { vm.key(Keys.DPAD_DOWN) }
        PadArrow(Icons.Filled.KeyboardArrowLeft, "Gauche", arrow.align(Alignment.CenterStart)) { vm.key(Keys.DPAD_LEFT) }
        PadArrow(Icons.Filled.KeyboardArrowRight, "Droite", arrow.align(Alignment.CenterEnd)) { vm.key(Keys.DPAD_RIGHT) }
        PressSurface(
            Modifier.align(Alignment.Center).fillMaxSize(0.38f).border(2.dp, Accent.copy(alpha = 0.7f), CircleShape),
            CircleShape,
            brush = Brush.verticalGradient(listOf(Color(0xFF353A45), Color(0xFF262A32))),
            onPress = { vm.key(Keys.DPAD_CENTER) },
        ) {
            Text("OK", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun PadArrow(icon: ImageVector, description: String, modifier: Modifier, onPress: () -> Unit) {
    PressSurface(modifier.padding(6.dp), CircleShape, color = Color.Transparent, repeat = true, onPress = onPress) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun RowScope.Rocker(
    label: String,
    up: ImageVector,
    down: ImageVector,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF323741), Color(0xFF272B33)))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PressSurface(Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(0.dp), color = Color.Transparent, repeat = true, onPress = onUp) {
            Icon(up, contentDescription = "$label +", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        PressSurface(Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(0.dp), color = Color.Transparent, repeat = true, onPress = onDown) {
            Icon(down, contentDescription = "$label −", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

/** Onglets sous la télécommande : applications, pavé numérique, clavier. */
@Composable
private fun ExtraPanels(state: UiState, vm: RemoteViewModel, dim: Modifier) {
    if (state.isIr) {
        // En infrarouge, pas d'applications ni de saisie de texte : seulement le pavé numérique.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PadColor)
                .border(1.dp, BorderColor, RoundedCornerShape(28.dp))
                .padding(14.dp),
        ) { NumPad(vm) }
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        Icons.Filled.Apps to "Applications",
        Icons.Filled.Dialpad to "Pavé",
        Icons.Filled.Keyboard to "Clavier",
        Icons.Filled.Cast to "Écran",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PadColor)
            .border(1.dp, BorderColor, RoundedCornerShape(28.dp)),
    ) {
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color.White) {
            tabs.forEachIndexed { i, (icon, title) ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, fontSize = 12.sp, maxLines = 1) },
                    icon = { Icon(icon, contentDescription = null) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Muted,
                )
            }
        }
        Column(Modifier.padding(14.dp).then(dim)) {
            when (tab) {
                0 -> AppsPanel(state, vm)
                1 -> NumPad(vm)
                2 -> TextInput(vm)
                else -> ScreenPanel()
            }
        }
    }
}

/** Afficher le téléphone sur la TV : duplication d'écran (système) ou photos et vidéos (Google Cast). */
@Composable
private fun ScreenPanel() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { CastActivity.start(context, emptyList()) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Photos et vidéos du téléphone")
        }
        Text(
            "Diaporama et vidéos en plein écran sur la TV. Aussi depuis la Galerie : Partager → « Ouvrir sur la TV ».",
            color = Muted,
            fontSize = 12.sp,
        )
        val mirroring by MirrorService.running.collectAsState()
        val mirrorStatus by MirrorService.status.collectAsState()
        Button(
            onClick = { if (mirroring) MirrorService.stop(context) else MirrorActivity.start(context) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Filled.ScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (mirroring) "  Arrêter la duplication" else "  Dupliquer l'écran sur la TV")
        }
        Text(
            mirrorStatus?.takeIf { mirroring || it.startsWith("Duplication") }
                ?: ("L'écran du téléphone (ou une seule application) s'affiche sur la TV, sans Google Cast ni " +
                    "Miracast, par le débogage ADB. Image seulement, sans le son."),
            color = Muted,
            fontSize = 12.sp,
        )
        val homeInstalled = remember { ScreenMirroring.isGoogleHomeInstalled(context) }
        OutlinedButton(
            onClick = { if (homeInstalled) ScreenMirroring.open(context) else ScreenMirroring.installGoogleHome(context) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Filled.ScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (homeInstalled) "  Dupliquer l'écran (Google Home)" else "  Installer Google Home pour dupliquer l'écran")
        }
        Text(
            "Pour une TV à Chromecast intégré, la duplication passe par Google Home : touchez la TV, puis " +
                "« Caster mon écran ». (« Smart View » de Samsung utilise Miracast, que beaucoup de TV Android ignorent.)",
            color = Muted,
            fontSize = 12.sp,
        )
        TextButton(onClick = { ScreenMirroring.openSystemMenu(context) }) {
            Text("Menu de diffusion du téléphone (Miracast)")
        }
    }
}

@Composable
private fun NumPad(vm: RemoteViewModel) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in row) TextKey(n.toString(), Modifier.weight(1f)) { vm.key(Keys.digit(n)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconKey(Icons.Filled.ClosedCaption, "Sous-titres", Modifier.weight(1f)) { vm.key(Keys.CAPTIONS) }
            TextKey("0", Modifier.weight(1f)) { vm.key(Keys.digit(0)) }
            IconKey(Icons.Filled.History, "Chaîne précédente", Modifier.weight(1f)) { vm.key(Keys.LAST_CHANNEL) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            ColorKey(Color(0xFFD93636)) { vm.key(Keys.PROG_RED) }
            ColorKey(Color(0xFF2FA84F)) { vm.key(Keys.PROG_GREEN) }
            ColorKey(Color(0xFFE0B31B)) { vm.key(Keys.PROG_YELLOW) }
            ColorKey(Color(0xFF2F6FD6)) { vm.key(Keys.PROG_BLUE) }
        }
    }
}

@Composable
private fun RowScope.ColorKey(color: Color, onPress: () -> Unit) {
    PressSurface(Modifier.weight(1f).height(26.dp), RoundedCornerShape(13.dp), color = color, onPress = onPress) {}
}

@Composable
private fun TextInput(vm: RemoteViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotEmpty()) {
            vm.text(text)
            text = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Placez d'abord le curseur dans une zone de saisie sur la TV (recherche, mot de passe…), puis tapez ici. " +
                "Les accents sont remplacés par la lettre simple (é → e).",
            color = Muted,
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Texte à envoyer…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = send,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent),
            ) { Icon(Icons.Filled.Send, contentDescription = "Envoyer") }
            PressSurface(Modifier.size(48.dp), CircleShape, repeat = true, onPress = { vm.key(Keys.DEL) }) {
                Icon(Icons.Filled.Backspace, contentDescription = "Effacer", tint = Color.White)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
