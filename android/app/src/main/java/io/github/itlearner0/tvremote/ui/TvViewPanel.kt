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
 * Onglet « Voir la TV » : l'écran de la TV en direct sur le téléphone (sans le son),
 * les touches de navigation juste en dessous et la capture d'écran.
 * Toucher l'image = OK ; la faire glisser = flèches.
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
    var capturing by remember { mutableStateOf(false) }
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    val scope = rememberCoroutineScope()
    val adb = remember { TvAdb(context.filesDir) }
    val viewer = remember(tv.id, tv.host) { TvScreenViewer(adb, tv.host) { status = it } }
    DisposableEffect(viewer) { onDispose { viewer.stop() } }

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
                            override fun surfaceDestroyed(holder: SurfaceHolder) = viewer.stop()
                        })
                    }
                },
            )
            // Calque transparent au-dessus de la vidéo : gestes de navigation.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tv.id) {
                        detectTapGestures(onTap = { vm.key(Keys.DPAD_CENTER) })
                    }
                    .pointerInput(tv.id) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                            },
                            onDragEnd = {
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
                    },
            )
        }
        Text(status, color = Muted, fontSize = 12.sp)
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
        OutlinedButton(
            onClick = {
                if (capturing) return@OutlinedButton
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
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (capturing) tr("  Capture en cours…") else tr("  Capture d'écran de la TV"))
        }
        Text(
            tr("Image seulement, sans le son. Les contenus protégés (Netflix, chaînes TV…) ") +
                tr("apparaissent en noir. Touchez l'image pour « OK », faites-la glisser pour les flèches."),
            color = Muted,
            fontSize = 11.sp,
        )
    }
}

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
