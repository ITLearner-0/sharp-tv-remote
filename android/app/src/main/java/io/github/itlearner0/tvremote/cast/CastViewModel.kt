package io.github.itlearner0.tvremote.cast

import android.app.Application
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import com.google.android.gms.cast.MediaStatus
import java.io.ByteArrayOutputStream
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import io.github.itlearner0.tvremote.data.TvStore
import io.github.itlearner0.tvremote.protocol.MediaHttpServer
import io.github.itlearner0.tvremote.protocol.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address

/** Photo ou vidéo du téléphone à afficher sur la TV. */
data class MediaItem(val uri: Uri, val name: String, val mimeType: String, val size: Long) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

data class CastDevice(val id: String, val name: String)

data class CastUiState(
    val available: Boolean = true,
    val devices: List<CastDevice> = emptyList(),
    val connectedTo: String? = null,
    val connecting: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val index: Int = 0,
    val slideshow: Boolean = false,
    val playing: Boolean = false,
    val message: String? = null,
    /** Journal des étapes (diagnostic), le plus récent en dernier. */
    val log: List<String> = emptyList(),
)

/**
 * Envoi de photos et vidéos du téléphone vers la TV par Google Cast : le
 * téléphone sert les fichiers sur le réseau local, la TV les lit.
 */
class CastViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state.asStateFlow()

    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(application) }.getOrNull()
    private val router = MediaRouter.getInstance(application)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .build()
    private var server: MediaHttpServer? = null
    private val prepared = HashMap<Int, MediaSource>()
    private var lastPlayerState: String? = null

    /** Suivi du lecteur de la TV : signale notamment les formats qu'il ne sait pas lire. */
    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = client()?.mediaStatus ?: return
            val text = when (status.playerState) {
                MediaStatus.PLAYER_STATE_PLAYING -> "lecture en cours"
                MediaStatus.PLAYER_STATE_PAUSED -> "en pause"
                MediaStatus.PLAYER_STATE_BUFFERING -> "chargement"
                MediaStatus.PLAYER_STATE_IDLE -> when (status.idleReason) {
                    MediaStatus.IDLE_REASON_ERROR -> "erreur de lecture (format non pris en charge par la TV ?)"
                    MediaStatus.IDLE_REASON_FINISHED -> "terminé"
                    MediaStatus.IDLE_REASON_INTERRUPTED -> "interrompu"
                    else -> null
                }
                else -> null
            } ?: return
            if (text != lastPlayerState) {
                lastPlayerState = text
                log("Lecteur de la TV : $text")
            }
        }
    }
    private var slideshowJob: Job? = null

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onConnected(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            log("Connexion refusée par la TV (code $error)")
            _state.update { it.copy(connecting = false, message = "La TV n'a pas accepté la connexion (erreur $error)") }
        }
        override fun onSessionEnded(session: CastSession, error: Int) =
            _state.update { it.copy(connectedTo = null, connecting = false, playing = false) }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    init {
        if (castContext == null) {
            _state.update { it.copy(available = false, message = "Google Cast n'est pas disponible sur ce téléphone") }
        } else {
            castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            castContext.sessionManager.currentCastSession?.let(::onConnected)
            refreshDevices()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun log(text: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())
        _state.update { it.copy(log = (it.log + "$time  $text").takeLast(10)) }
    }

    /** Fichiers reçus (partage ou sélection dans la galerie). */
    fun setItems(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { uris.mapNotNull(::describe) }
            prepared.clear()
            server?.clear()
            log("${items.size} fichier(s) choisi(s)" + items.firstOrNull()?.let { " (${it.mimeType})" }.orEmpty())
            _state.update { it.copy(items = items, index = 0) }
            if (_state.value.connectedTo != null) show(0)
        }
    }

    fun connect(device: CastDevice) {
        val route = router.routes.firstOrNull { it.id == device.id } ?: return
        log("Connexion à ${device.name}…")
        _state.update { it.copy(connecting = true) }
        router.selectRoute(route)
    }

    fun disconnect() {
        slideshowJob?.cancel()
        castContext?.sessionManager?.endCurrentSession(true)
        _state.update { it.copy(connectedTo = null, playing = false, slideshow = false) }
    }

    fun show(index: Int) {
        val items = _state.value.items
        if (items.isEmpty()) return
        val i = ((index % items.size) + items.size) % items.size
        _state.update { it.copy(index = i) }
        val client = client()
        if (client == null) {
            log("Pas encore connecté à une TV : choisissez-la dans la liste")
            return
        }
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) { publish(i) }
            if (url == null) {
                _state.update { it.copy(message = "Wi-Fi introuvable : le téléphone doit être sur le même réseau que la TV") }
                return@launch
            }
            val item = items[i]
            val metadata = MediaMetadata(if (item.isVideo) MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_PHOTO)
                .apply { putString(MediaMetadata.KEY_TITLE, item.name) }
            val info = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(if (item.isVideo) item.mimeType else "image/jpeg")
                .setMetadata(metadata)
                .build()
            log("Envoi de « ${item.name} » à la TV")
            client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
                .setResultCallback { result ->
                    log(
                        if (result.status.isSuccess) "La TV a accepté le fichier"
                        else "La TV a refusé le fichier (code ${result.status.statusCode})",
                    )
                }
            _state.update { it.copy(playing = item.isVideo) }
        }
    }

    fun next() = show(_state.value.index + 1)

    fun previous() = show(_state.value.index - 1)

    /** Diaporama : photo suivante toutes les 6 secondes (les vidéos sont lues jusqu'au bout). */
    fun toggleSlideshow() {
        if (slideshowJob?.isActive == true) {
            slideshowJob?.cancel()
            _state.update { it.copy(slideshow = false) }
            return
        }
        _state.update { it.copy(slideshow = true) }
        slideshowJob = viewModelScope.launch {
            while (isActive) {
                val current = _state.value.items.getOrNull(_state.value.index)
                if (current?.isVideo == true) {
                    delay(2_000)
                    val client = client()
                    if (client != null && client.isPlaying) continue
                } else {
                    delay(6_000)
                }
                next()
            }
        }
    }

    fun playPause() {
        val client = client() ?: return
        if (client.isPlaying) client.pause() else client.play()
        _state.update { it.copy(playing = !client.isPlaying) }
    }

    fun seekBy(deltaMs: Long) {
        val client = client() ?: return
        val target = (client.approximateStreamPosition + deltaMs).coerceAtLeast(0)
        client.seek(MediaSeekOptions.Builder().setPosition(target).build())
    }

    private fun client(): RemoteMediaClient? = castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    private fun onConnected(session: CastSession) {
        val name = session.castDevice?.friendlyName ?: "TV"
        log("Connecté à $name")
        session.remoteMediaClient?.registerCallback(mediaCallback)
        _state.update { it.copy(connectedTo = name, connecting = false) }
        if (_state.value.items.isNotEmpty()) show(_state.value.index)
    }

    private fun refreshDevices() {
        val devices = router.routes
            .filter { !it.isDefault && it.matchesSelector(selector) && it.isEnabled }
            .map { CastDevice(it.id, it.name) }
        if (devices.map { it.name } != _state.value.devices.map { it.name }) {
            log(if (devices.isEmpty()) "Aucune TV Google Cast trouvée pour l'instant" else "TV trouvées : " + devices.joinToString { it.name })
        }
        _state.update { it.copy(devices = devices) }
        autoConnect(devices)
    }

    /** Se connecte d'office à la TV dont le nom correspond à la TV choisie dans la télécommande. */
    private fun autoConnect(devices: List<CastDevice>) {
        val s = _state.value
        if (s.connectedTo != null || s.connecting || devices.isEmpty()) return
        val tvName = TvStore(getApplication<Application>().getSharedPreferences("tv", 0)).current()?.name.orEmpty().lowercase()
        val match = devices.singleOrNull()
            ?: devices.firstOrNull { tvName.isNotBlank() && (it.name.lowercase() in tvName || tvName in it.name.lowercase()) }
        match?.let(::connect)
    }

    private fun describe(uri: Uri): MediaItem? = runCatching {
        val resolver = getApplication<Application>().contentResolver
        var name = uri.lastPathSegment ?: "media"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size < 0) size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
        val mime = resolver.getType(uri) ?: if (name.endsWith(".mp4", true)) "video/mp4" else "image/jpeg"
        MediaItem(uri, name, mime, size)
    }.getOrNull()?.takeIf { it.size > 0 && (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")) }

    /** Rend le fichier [index] accessible à la TV ; renvoie son adresse. */
    private fun publish(index: Int): String? {
        val host = phoneIpv4() ?: return null
        val srv = server ?: MediaHttpServer { client, i, status ->
            log("La TV ($client) télécharge le fichier ${i?.plus(1) ?: "?"} → " + if (status < 300) "OK" else "erreur $status")
        }.also { server = it }
        val item = _state.value.items[index]
        prepared[index]?.let { return srv.publish(host, index, it) }
        if (!item.isVideo) {
            // Photos converties en JPEG à la taille de la TV : le lecteur Cast ne lit pas
            // le HEIC (format par défaut de beaucoup de téléphones) et c'est plus rapide.
            val jpeg = toJpeg(item)
            if (jpeg != null) {
                log("Photo convertie en JPEG (${jpeg.size / 1024} Ko)")
                val source = object : MediaSource {
                    override val name = item.name.substringBeforeLast('.') + ".jpg"
                    override val mimeType = "image/jpeg"
                    override val size = jpeg.size.toLong()
                    override fun open(offset: Long): InputStream = jpeg.inputStream().also { it.skip(offset) }
                }
                prepared[index] = source
                return srv.publish(host, index, source)
            }
            log("Conversion de la photo impossible : envoi du fichier d'origine")
        }
        val resolver = getApplication<Application>().contentResolver
        return srv.publish(
            host,
            index,
            object : MediaSource {
                override val name = item.name
                override val mimeType = item.mimeType
                override val size = item.size
                override fun open(offset: Long): InputStream {
                    val fd: AssetFileDescriptor = resolver.openAssetFileDescriptor(item.uri, "r")
                        ?: error("fichier inaccessible")
                    val stream = FileInputStream(fd.fileDescriptor)
                    stream.channel.position(fd.startOffset + offset)
                    return object : InputStream() {
                        override fun read() = stream.read()
                        override fun read(b: ByteArray, off: Int, len: Int) = stream.read(b, off, len)
                        override fun close() {
                            stream.close()
                            fd.close()
                        }
                    }
                }
            },
        )
    }

    /** Photo décodée (orientation EXIF comprise), réduite à 1920 px au plus, en JPEG. */
    private fun toJpeg(item: MediaItem): ByteArray? = runCatching {
        val resolver = getApplication<Application>().contentResolver
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, item.uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            resolver.openInputStream(item.uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
        }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }.getOrNull()

    private fun phoneIpv4(): String? {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.firstNotNullOfOrNull { (it.address as? Inet4Address)?.hostAddress }
    }

    private companion object {
        const val MAX_SIDE = 1920
    }

    override fun onCleared() {
        slideshowJob?.cancel()
        router.removeCallback(routerCallback)
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        server?.close()
    }
}
