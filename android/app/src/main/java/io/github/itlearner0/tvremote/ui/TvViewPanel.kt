package io.github.itlearner0.tvremote.ui

import io.github.itlearner0.tvremote.i18n.tr

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.itlearner0.tvremote.cast.TvAudioPlayer
import io.github.itlearner0.tvremote.cast.TvScreenViewer
import io.github.itlearner0.tvremote.data.TvAdb
import io.github.itlearner0.tvremote.protocol.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Onglet « Voir la TV » : l'écran de la TV en direct sur le téléphone, avec le son
 * des applications qui le permettent, les touches de navigation et la capture d'écran.
 * Toucher l'image = OK ; la faire glisser = flèches. Bouton plein écran : paysage.
 */
@Composable
fun TvViewPanel(state: UiState, vm: RemoteViewModel) {
    val context = LocalContext.current
    val tv = state.current
    if (tv == null || !tv.isAndroidTv) {
        Text(tr("« Voir la TV » fonctionne avec une TV Android connectée en Wi-Fi."), color = Muted, fontSize = 13.sp)
        return
    }
    var status by remember(tv.id) { mutableStateOf(tr("Connexion à l'écran de la TV…")) }
    var audioStatus by remember(tv.id) { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean(KEY_SOUND, true)) }
    val scope = rememberCoroutineScope()
    val adb = remember { TvAdb(context.filesDir) }
    val viewer = remember(tv.id, tv.host) { TvScreenViewer(adb, tv.host) { status = it } }
    val audio = remember(tv.id, tv.host) {
        TvAudioPlayer(adb, tv.host, { TvAdb.helperApk(context) }) { audioStatus = it }
    }
    DisposableEffect(viewer) { onDispose { viewer.stop() } }
    DisposableEffect(audio, soundOn) {
        if (soundOn) audio.start() else audioStatus = null
        onDispose { audio.stop() }
    }

    fun toggleSound() {
        soundOn = !soundOn
        prefs.edit().putBoolean(KEY_SOUND, soundOn).apply()
    }

    fun capture() {
        if (capturing) return
        capturing = true
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching { saveScreenshot(context, adb.screenshot(tv.host, context.cacheDir)) }
                    .recoverCatching { tvError ->
                        // Secours : l'image affichée sur le téléphone (720p).
                        val png = surfaceView?.let { copyFrame(it) } ?: throw tvError
                        saveScreenshot(context, png) + tr(" (image du téléphone)")
                    }
                    .getOrElse { tr("Capture impossible : {1}", it.message ?: it.javaClass.simpleName) }
            }
            capturing = false
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).also { surfaceView = it }.apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = viewer.start(holder.surface)
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                            override fun surfaceDestroyed(holder: SurfaceHolder) = viewer.panelGone()
                        })
                    }
                },
            )
            Box(Modifier.fillMaxSize().navGestures(tv.id, vm))
            FilledTonalIconButton(
                onClick = { fullscreen = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp),
            ) { Icon(Icons.Filled.Fullscreen, contentDescription = tr("Plein écran")) }
        }
        Text(audioStatus?.let { "$status · $it" } ?: status, color = Muted, fontSize = 12.sp)
        if (soundOn) {
            androidx.compose.material3.TextButton(onClick = {
                // Partie téléphone tout de suite ; partie TV ajoutée dès qu'elle répond (20 s au plus).
                val phonePart = audio.phoneReport()
                report = phonePart + "\n" + tr("Lecture du diagnostic…")
                scope.launch {
                    val tvPart = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                        kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
                            runCatching { adb.tvAudioReport(tv.host) }.getOrElse { tr("TV injoignable : {1}", it.message ?: "") }
                        }
                    } ?: tr("TV injoignable : {1}", "20 s")
                    if (report != null) report = phonePart + "\n" + tvPart
                }
            }) { Text(tr("Diagnostic du son"), fontSize = 12.sp) }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavKey(Icons.AutoMirrored.Filled.ArrowBack, tr("Retour")) { vm.key(Keys.BACK) }
            NavKey(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("Gauche")) { vm.key(Keys.DPAD_LEFT) }
            NavKey(Icons.Filled.KeyboardArrowUp, tr("Haut")) { vm.key(Keys.DPAD_UP) }
            NavKey(Icons.Filled.RadioButtonChecked, "OK") { vm.key(Keys.DPAD_CENTER) }
            NavKey(Icons.Filled.KeyboardArrowDown, tr("Bas")) { vm.key(Keys.DPAD_DOWN) }
            NavKey(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Droite")) { vm.key(Keys.DPAD_RIGHT) }
            NavKey(Icons.Filled.Home, tr("Accueil")) { vm.key(Keys.HOME) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = ::toggleSound, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(
                    if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(if (soundOn) tr("  Son sur le téléphone") else tr("  Son coupé"))
            }
            OutlinedButton(onClick = ::capture, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (capturing) tr("  Capture en cours…") else tr("  Capture"))
            }
        }
        Text(
            tr("Le son des applications protégées (Netflix, Prime Video…) n'est pas transmis, et leur image ") +
                tr("apparaît en noir. Touchez l'image pour « OK », faites-la glisser pour les flèches."),
            color = Muted,
            fontSize = 11.sp,
        )
    }

    report?.let { text ->
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(tr("Diagnostic du son")) },
            text = {
                Text(
                    text,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                    Toast.makeText(context, tr("Rapport copié"), Toast.LENGTH_SHORT).show()
                }) { Text(tr("Copier")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { report = null }) { Text(tr("Fermer")) } },
        )
    }

    if (fullscreen) {
        FullscreenTv(
            viewer = viewer,
            vm = vm,
            tvId = tv.id,
            soundOn = soundOn,
            status = audioStatus,
            onToggleSound = ::toggleSound,
            onCapture = ::capture,
            onClose = { fullscreen = false },
        )
    }
}

/** Gestes sur l'image : toucher = OK, glisser = flèches. */
private fun Modifier.navGestures(tvId: String, vm: RemoteViewModel, onActivity: () -> Unit = {}): Modifier =
    this
        .pointerInput(tvId) {
            detectTapGestures(onTap = {
                onActivity()
                vm.key(Keys.DPAD_CENTER)
            })
        }
        .pointerInput(tvId) {
            var total = Offset.Zero
            detectDragGestures(
                onDragStart = { total = Offset.Zero },
                onDrag = { change, amount ->
                    change.consume()
                    total += amount
                },
                onDragEnd = {
                    onActivity()
                    if (maxOf(abs(total.x), abs(total.y)) > 40f) {
                        vm.key(
                            if (abs(total.x) > abs(total.y)) {
                                if (total.x > 0) Keys.DPAD_RIGHT else Keys.DPAD_LEFT
                            } else {
                                if (total.y > 0) Keys.DPAD_DOWN else Keys.DPAD_UP
                            },
                        )
                    }
                },
            )
        }

/**
 * Plein écran en paysage : l'image de la TV occupe tout l'écran, l'écran du téléphone
 * reste allumé ; les commandes s'affichent au toucher et se masquent après 4 secondes.
 */
@Composable
private fun FullscreenTv(
    viewer: TvScreenViewer,
    vm: RemoteViewModel,
    tvId: String,
    soundOn: Boolean,
    status: String?,
    onToggleSound: () -> Unit,
    onCapture: () -> Unit,
    onClose: () -> Unit,
) {
    val activity = LocalContext.current as? android.app.Activity
    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            viewer.setFullscreenSurface(null)
            activity?.requestedOrientation = previous ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    var controls by remember { mutableStateOf(true) }
    var touchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(touchedAt) {
        controls = true
        kotlinx.coroutines.delay(4_000)
        controls = false
    }
    val wake = { touchedAt = System.currentTimeMillis() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose { }
        }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = viewer.setFullscreenSurface(holder.surface)
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                            override fun surfaceDestroyed(holder: SurfaceHolder) = viewer.setFullscreenSurface(null)
                        })
                    }
                },
            )
            Box(Modifier.fillMaxSize().navGestures(tvId, vm, wake))
            if (controls) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OverlayKey(
                        if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        if (soundOn) tr("Couper le son du téléphone") else tr("Son sur le téléphone"),
                    ) { wake(); onToggleSound() }
                    OverlayKey(Icons.Filled.CameraAlt, tr("Capture d'écran de la TV")) { wake(); onCapture() }
                    OverlayKey(Icons.Filled.FullscreenExit, tr("Quitter le plein écran"), onClose)
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OverlayKey(Icons.AutoMirrored.Filled.ArrowBack, tr("Retour")) { wake(); vm.key(Keys.BACK) }
                    OverlayKey(Icons.Filled.Home, tr("Accueil")) { wake(); vm.key(Keys.HOME) }
                    OverlayKey(Icons.Filled.Remove, tr("Volume -")) { wake(); vm.key(Keys.VOLUME_DOWN) }
                    OverlayKey(Icons.Filled.Add, tr("Volume +")) { wake(); vm.key(Keys.VOLUME_UP) }
                    OverlayKey(Icons.Filled.PlayArrow, tr("Lecture / pause")) { wake(); vm.key(Keys.MEDIA_PLAY_PAUSE) }
                }
                status?.let {
                    Text(
                        it,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .widthIn(max = 360.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color(0x99000000),
            contentColor = Color.White,
        ),
    ) { Icon(icon, contentDescription = description) }
}

private const val KEY_SOUND = "tv_view_sound"

@Composable
private fun NavKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(icon, contentDescription = description)
    }
}

/** Copie l'image actuellement affichée dans la vue vidéo, en PNG (null si impossible). */
private suspend fun copyFrame(view: SurfaceView): ByteArray? {
    if (view.width == 0 || view.height == 0 || !view.holder.surface.isValid) return null
    val bitmap = android.graphics.Bitmap.createBitmap(
        TvScreenViewer.WIDTH,
        TvScreenViewer.HEIGHT,
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    val ok = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        android.view.PixelCopy.request(view, bitmap, { result ->
            cont.resumeWith(Result.success(result == android.view.PixelCopy.SUCCESS))
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }
    if (!ok) return null
    return java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
}

/** Enregistre la capture dans Images/OmniZap ; renvoie le message à afficher. */
private fun saveScreenshot(context: Context, png: ByteArray): String {
    require(png.size > 8 && png[1] == 'P'.code.toByte()) { tr("la TV n'a pas renvoyé d'image") }
    val name = "TV_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OmniZap")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error(tr("galerie inaccessible"))
        context.contentResolver.openOutputStream(uri)?.use { it.write(png) } ?: error(tr("galerie inaccessible"))
        return tr("Capture enregistrée dans la galerie (Images/OmniZap)")
    }
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), tr("OmniZap")).apply { mkdirs() }
    File(dir, name).writeBytes(png)
    return tr("Capture enregistrée : {1}/{2}", dir.absolutePath, name)
}
