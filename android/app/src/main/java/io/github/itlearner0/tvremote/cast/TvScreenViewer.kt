package io.github.itlearner0.tvremote.cast

import io.github.itlearner0.tvremote.i18n.tr

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import io.github.itlearner0.tvremote.data.TvAdb
import io.github.itlearner0.tvremote.protocol.H264AccessUnits
import kotlin.concurrent.thread

/**
 * « Voir la TV » : affiche sur le téléphone l'écran de la TV, filmé par la TV
 * elle-même (screenrecord, par ADB) et décodé ici. L'enregistrement d'Android
 * s'arrête au bout de 3 minutes : il est relancé aussitôt.
 */
class TvScreenViewer(
    private val adb: TvAdb,
    private val host: String,
    private val onStatus: (String) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var stream: AutoCloseable? = null
    private var worker: Thread? = null

    fun start(surface: Surface) {
        if (running) return
        running = true
        worker = thread(name = "tv-screen") { loop(surface) }
    }

    fun stop() {
        if (!running) return
        running = false
        val open = stream
        worker?.interrupt()
        worker = null
        // Arrêt propre de l'enregistrement sur la TV, puis fermeture du flux.
        thread(name = "tv-screen-stop") {
            runCatching { adb.stopScreenRecord(host) }
            runCatching { open?.close() }
        }
    }


    private fun loop(surface: Surface) {
        val codec = runCatching {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT)
                // Tampon assez grand pour une image complète.
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024)
                configure(format, surface, null, 0)
                start()
            }
        }.getOrElse {
            onStatus(tr("Décodeur vidéo du téléphone indisponible ({1})", it))
            return
        }
        val info = MediaCodec.BufferInfo()
        val start = System.nanoTime()
        var failures = 0
        var shown = 0
        try {
            while (running) {
                onStatus(if (shown == 0) tr("Connexion à l'écran de la TV…") else tr("Écran de la TV en direct"))
                var received = 0
                try {
                    adb.screenStream(host, { stream = it }) { input ->
                        val splitter = H264AccessUnits { data, config ->
                            if (!running) return@H264AccessUnits
                            queue(codec, data, config, (System.nanoTime() - start) / 1000)
                            if (!config && ++shown == 1) onStatus(tr("Écran de la TV en direct"))
                            drain(codec, info)
                        }
                        val buffer = ByteArray(64 * 1024)
                        while (running) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            received += n
                            splitter.feed(buffer, 0, n)
                        }
                    }
                    failures = if (received > 0) 0 else failures + 1
                } catch (e: Exception) {
                    if (!running) break
                    // Flux coupé après avoir reçu des images (écran immobile trop longtemps, réseau) :
                    // simple relance. Sinon, c'est une vraie panne.
                    if (received > 0) {
                        failures = 0
                    } else {
                        failures++
                        onStatus(tr("Écran de la TV indisponible : {1}", e.message ?: e.javaClass.simpleName))
                    }
                }
                if (failures >= 3) {
                    onStatus(
                        tr("La TV n'envoie pas son écran. Vérifiez que le débogage ADB est autorisé ") +
                            tr("(il sert aussi à la liste des applications)."),
                    )
                    break
                }
                if (running) Thread.sleep(if (failures > 0) 1500 else 100)
            }
        } catch (_: InterruptedException) {
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun queue(codec: MediaCodec, data: ByteArray, config: Boolean, ptsUs: Long) {
        var index: Int
        while (true) {
            index = codec.dequeueInputBuffer(10_000)
            if (index >= 0 || !running) break
            drain(codec, MediaCodec.BufferInfo())
        }
        if (index < 0) return
        val buffer = codec.getInputBuffer(index) ?: return
        buffer.clear()
        if (buffer.remaining() < data.size) {
            codec.queueInputBuffer(index, 0, 0, ptsUs, 0)
            return
        }
        buffer.put(data)
        codec.queueInputBuffer(index, 0, data.size, ptsUs, if (config) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
    }

    private fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            if (index >= 0) codec.releaseOutputBuffer(index, true)
            else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
        }
    }

    companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720
    }
}
