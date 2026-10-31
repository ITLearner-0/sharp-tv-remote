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
        _status.value = "Ouverture du récepteur sur ${tv.name}…"
        thread(name = "mirror-server") { serve(tv.host) }
        return START_NOT_STICKY
    }

    private fun serve(host: String) {
        try {
            val phone = phoneIpv4() ?: error("Wi-Fi introuvable : le téléphone doit être sur le même réseau que la TV")
            val srv = ServerSocket(0).also { server = it }
            srv.soTimeout = 30_000
            TvAdb(filesDir).startMirror(host, TvAdb.helperApk(this), phone, srv.localPort)
                ?.let { error("la TV n'a pas ouvert le récepteur : $it") }
            // N'accepte que la TV : personne d'autre sur le réseau ne peut voir l'écran.
            var socket: Socket
            while (true) {
                socket = srv.accept()
                if (socket.inetAddress?.hostAddress == host) break
                socket.close()
            }
            srv.close()
            client = socket
            socket.tcpNoDelay = true
            _status.value = "Écran du téléphone affiché sur la TV"
            streaming = true
            stream(DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 1 shl 16)))
        } catch (e: Exception) {
            if (streaming && e is java.io.IOException) _status.value = "Duplication arrêtée sur la TV"
            else if (!stopping) fail(e.message ?: e.javaClass.simpleName)
        } finally {
            main.post { stopSelf() }
        }
    }

    /** Encode l'écran et envoie les images ; recommence avec la nouvelle taille si l'écran tourne. */
    private fun stream(out: DataOutputStream) {
        out.writeInt(MAGIC)
        val dm = getSystemService(DisplayManager::class.java)
        val screen = dm.getDisplay(Display.DEFAULT_DISPLAY)
        while (!stopping) {
            val metrics = DisplayMetrics().also {
                @Suppress("DEPRECATION")
                screen.getRealMetrics(it)
            }
            // Taille réduite si l'encodeur du téléphone refuse la précédente.
            var configured: Triple<MediaCodec, Int, Int>? = null
            var lastError: Exception? = null
            for (max in intArrayOf(1280, 960, 720)) {
                val (w, h) = videoSize(metrics.widthPixels, metrics.heightPixels, max)
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    // Image répétée quand l'écran est immobile : la TV reste à jour.
                    setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
                }
                val candidate = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                try {
                    candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    configured = Triple(candidate, w, h)
                    break
                } catch (e: Exception) {
                    candidate.release()
                    lastError = e
                }
            }
            val (codec, w, h) = configured ?: throw IllegalStateException("encodeur vidéo indisponible", lastError)
            val input = codec.createInputSurface()
            codec.start()
            val vd = display
            if (vd == null) {
                display = projection?.createVirtualDisplay(
                    "telecommande-tv", w, h, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, input, null, null,
                ) ?: error("capture de l'écran arrêtée")
            } else {
                vd.resize(w, h, metrics.densityDpi)
                vd.surface = input
            }
            out.writeInt(0)
            out.writeInt(w)
            out.writeInt(h)
            out.flush()
            val landscape = w > h
            val info = MediaCodec.BufferInfo()
            try {
                while (!stopping) {
                    val index = codec.dequeueOutputBuffer(info, 100_000)
                    if (index >= 0) {
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
                        }
                        codec.releaseOutputBuffer(index, false)
                    }
                    // Rotation de l'écran : nouvel encodeur à la nouvelle taille.
                    @Suppress("DEPRECATION")
                    screen.getRealMetrics(metrics)
                    if ((metrics.widthPixels > metrics.heightPixels) != landscape) break
                }
            } finally {
                display?.surface = null
                runCatching { codec.stop() }
                codec.release()
                input.release()
            }
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
        tvHost?.let { host -> thread { runCatching { TvAdb(filesDir).stopMirror(host) } } }
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
        private val _status = MutableStateFlow<String?>(null)
        val status: StateFlow<String?> = _status.asStateFlow()

        fun stop(context: Context) {
            context.startService(Intent(context, MirrorService::class.java).setAction(ACTION_STOP))
        }

        /** Taille de la vidéo : [max] px au plus sur le grand côté, multiples de 16. */
        fun videoSize(width: Int, height: Int, max: Int = 1280): Pair<Int, Int> {
            val longest = maxOf(width, height)
            val scale = if (longest > max) max.toDouble() / longest else 1.0
            fun even(v: Double) = maxOf(16, (v.toInt() / 16) * 16)
            return even(width * scale) to even(height * scale)
        }
    }
}
