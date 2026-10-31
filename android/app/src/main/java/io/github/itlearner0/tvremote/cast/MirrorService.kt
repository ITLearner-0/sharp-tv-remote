package io.github.itlearner0.tvremote.cast

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.widget.Toast
import io.github.itlearner0.tvremote.R
import io.github.itlearner0.tvremote.data.TvAdb
import io.github.itlearner0.tvremote.data.TvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Duplication de l'écran du téléphone sur la TV, sans Google Cast ni Miracast :
 * l'écran est capturé (MediaProjection), encodé en H.264 et envoyé sur le
 * réseau local au récepteur de l'application d'aide, ouvert sur la TV par ADB.
 * Seule l'adresse de la TV est acceptée. Pas de son.
 */
class MirrorService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var stopping = false
    @Volatile private var streaming = false
    /** Échec : on laisse le message d'erreur affiché sur la TV et on rapporte son journal. */
    @Volatile private var failed = false
    @Volatile private var framesSent = 0
    private val steps = java.util.Collections.synchronizedList(mutableListOf<String>())

    private fun step(text: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())
        steps += "$time  $text"
    }
    private var tvHost: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY
        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        // Le service doit être au premier plan avant d'obtenir la capture d'écran (Android 14).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        val tv = TvStore(getSharedPreferences("tv", Context.MODE_PRIVATE)).current()
        if (data == null || tv == null || tv.isIr || tv.host.isBlank()) {
            fail("aucune TV Wi-Fi enregistrée")
            return START_NOT_STICKY
        }
        tvHost = tv.host
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mp = runCatching { manager.getMediaProjection(code, data) }.getOrNull()
        if (mp == null) {
            fail("capture de l'écran refusée")
            return START_NOT_STICKY
        }
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopSelf() }
            }
        }, main)
        _running.value = true
        _report.value = null
        _status.value = "Ouverture du récepteur sur ${tv.name}…"
        thread(name = "mirror-server") { serve(tv.host) }
        return START_NOT_STICKY
    }

    private fun serve(host: String) {
        try {
            val phone = phoneIpv4() ?: error("Wi-Fi introuvable : le téléphone doit être sur le même réseau que la TV")
            val srv = ServerSocket(0).also { server = it }
            srv.soTimeout = 30_000
            step("Téléphone $phone:${srv.localPort}, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}")
            TvAdb(filesDir).startMirror(host, TvAdb.helperApk(this), phone, srv.localPort)
                ?.let { error("la TV n'a pas ouvert le récepteur : $it") }
            step("Récepteur ouvert sur la TV ($host)")
            // N'accepte que la TV : personne d'autre sur le réseau ne peut voir l'écran.
            var socket: Socket
            while (true) {
                socket = srv.accept()
                if (sameHost(socket.inetAddress, host)) break
                step("Connexion refusée depuis ${socket.inetAddress?.hostAddress}")
                socket.close()
            }
            srv.close()
            step("TV connectée")
            client = socket
            socket.tcpNoDelay = true
            _status.value = "Écran du téléphone affiché sur la TV"
            streaming = true
            stream(DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 1 shl 16)))
        } catch (e: Exception) {
            if (!stopping) {
                step("Arrêt : $e")
                failed = true
                if (streaming && e is java.io.IOException) _status.value = "La TV a interrompu la duplication"
                else fail(e.message ?: e.javaClass.simpleName)
                report(host)
            }
        } finally {
            main.post { stopSelf() }
        }
    }

    /**
     * Encode l'écran et envoie les images. L'image est toujours horizontale
     * (1920 × 1080, sinon 1280 × 720) : les décodeurs des TV refusent souvent
     * les images verticales ; Android y place l'écran du téléphone en gardant
     * ses proportions (bandes noires), y compris quand il tourne.
     */
    private fun stream(out: DataOutputStream) {
        out.writeInt(MAGIC)
        val dm = getSystemService(DisplayManager::class.java)
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            dm.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(it)
        }
        var configured: Triple<MediaCodec, Int, Int>? = null
        var lastError: Exception? = null
        for ((w, h, rate) in SIZES) {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, rate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                // Image répétée quand l'écran est immobile : la TV reste à jour.
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
            }
            val candidate = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            try {
                candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                configured = Triple(candidate, w, h)
                step("Encodeur ${candidate.name} : $w × $h")
                break
            } catch (e: Exception) {
                step("Encodeur refusé en $w × $h : $e")
                candidate.release()
                lastError = e
            }
        }
        val (codec, w, h) = configured ?: throw IllegalStateException("encodeur vidéo indisponible", lastError)
        val input = codec.createInputSurface()
        codec.start()
        try {
            display = projection?.createVirtualDisplay(
                "telecommande-tv", w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, input, null, null,
            ) ?: error("capture de l'écran arrêtée")
            out.writeInt(0)
            out.writeInt(w)
            out.writeInt(h)
            out.flush()
            _status.value = "Écran du téléphone envoyé à la TV ($w × $h)"
            val info = MediaCodec.BufferInfo()
            while (!stopping) {
                val index = codec.dequeueOutputBuffer(info, 100_000)
                if (index < 0) continue
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val bytes = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(bytes)
                    out.writeInt(1)
                    out.writeInt(info.flags)
                    out.writeLong(info.presentationTimeUs)
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                    if (++framesSent == 1) step("Première image envoyée (${bytes.size} octets)")
                }
                codec.releaseOutputBuffer(index, false)
            }
        } finally {
            display?.release()
            display = null
            runCatching { codec.stop() }
            codec.release()
            input.release()
        }
    }

    /** Rapport complet : étapes du téléphone et journal de la TV (lu par ADB), à copier. */
    private fun report(host: String) {
        val tvLog = runCatching { TvAdb(filesDir).mirrorLog(host) }.getOrElse { "journal de la TV illisible (${it.message})" }
        _report.value = buildString {
            appendLine("Rapport de duplication OmniZap")
            appendLine("Images envoyées : $framesSent")
            synchronized(steps) { steps.forEach { appendLine(it) } }
            appendLine("— Journal de la TV —")
            append(tvLog.trim().ifEmpty { "(vide)" })
        }
    }

    private fun fail(reason: String) {
        _status.value = "Duplication impossible : $reason"
        main.post { Toast.makeText(applicationContext, "Duplication impossible : $reason", Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        stopping = true
        runCatching { server?.close() }
        runCatching { client?.close() }
        display?.release()
        display = null
        projection?.stop()
        projection = null
        // Arrêt demandé : on ferme aussi l'écran de la TV. Échec : on y laisse le message d'erreur.
        if (!failed) tvHost?.let { host -> thread { runCatching { TvAdb(filesDir).stopMirror(host) } } }
        _running.value = false
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Duplication de l'écran", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("Écran dupliqué sur la TV")
            .setContentText("Touchez « Arrêter » pour arrêter la duplication")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build())
            .build()
    }

    private fun phoneIpv4(): String? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.firstNotNullOfOrNull { (it.address as? Inet4Address)?.hostAddress }
    }

    companion object {
        private const val MAGIC = 0x4D495252 // "MIRR"
        private const val CHANNEL = "mirror"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "stop"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
        private val _report = MutableStateFlow<String?>(null)
        /** Rapport détaillé du dernier échec (téléphone et TV), ou null. */
        val report: StateFlow<String?> = _report.asStateFlow()
        private val _status = MutableStateFlow<String?>(null)
        val status: StateFlow<String?> = _status.asStateFlow()

        fun stop(context: Context) {
            context.startService(Intent(context, MirrorService::class.java).setAction(ACTION_STOP))
        }

        /** Tailles d'image essayées (largeur, hauteur, débit) : horizontales, comme une vidéo de TV. */
        private val SIZES = listOf(Triple(1920, 1080, 8_000_000), Triple(1280, 720, 5_000_000))

        /** Même machine, que l'adresse soit écrite en IPv4 ou en IPv6 « ::ffff:a.b.c.d ». */
        fun sameHost(address: java.net.InetAddress?, host: String): Boolean {
            address ?: return false
            val text = address.hostAddress.orEmpty().removePrefix("::ffff:")
            return text == host || runCatching { java.net.InetAddress.getByName(host) == address }.getOrDefault(false)
        }
    }
}
