package io.github.itlearner0.tvremote.cast

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import io.github.itlearner0.tvremote.i18n.tr
import java.io.DataInputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Son de « Voir la TV » : l'application d'aide de la TV capte le son des applications
 * (pas celui des applications qui l'interdisent, comme Netflix) et l'envoie ici.
 * Le son est retardé d'environ [DELAY_MS] pour rester à peu près calé sur l'image,
 * elle-même un peu en retard (encodage sur la TV).
 */
class TvAudioPlayer(
    private val adb: io.github.itlearner0.tvremote.data.TvAdb,
    private val host: String,
    private val helperApk: () -> File?,
    private val onStatus: (String?) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    private var worker: Thread? = null

    // Diagnostic, côté téléphone.
    @Volatile private var lastStart: String = "pas encore lancé"
    @Volatile private var connections = 0
    @Volatile private var connectFailures = 0
    @Volatile private var bytes = 0L
    @Volatile private var peak = 0
    @Volatile private var lastError: String? = null

    fun phoneReport(): String = buildString {
        appendLine("Téléphone : démarrage sur la TV = $lastStart")
        appendLine("Connexions au son de la TV : $connections réussie(s), $connectFailures échec(s)")
        appendLine("Son reçu : $bytes octets, niveau max $peak (0 = silence)")
        lastError?.let { appendLine("Dernière erreur : $it") }
    }

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "tv-audio") { loop() }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { socket?.close() }
        worker?.interrupt()
        worker = null
        thread(name = "tv-audio-stop") { runCatching { adb.stopTvAudio(host) } }
    }

    private fun loop() {
        var started = false
        var failures = 0
        while (running && failures < 3) {
            if (!started) {
                onStatus(tr("Son : démarrage sur la TV…"))
                val error = runCatching { adb.startTvAudio(host, helperApk()) }.getOrElse { it.message ?: it.javaClass.simpleName }
                lastStart = error ?: "accepté"
                if (error != null) {
                    onStatus(tr("Son indisponible : {1}", error))
                    return
                }
                started = true
            }
            val received = runCatching { play() }.getOrElse { e ->
                lastError = e.message ?: e.javaClass.simpleName
                if (!running) return
                onStatus(tr("Son interrompu : {1}", e.message ?: e.javaClass.simpleName))
                0L
            }
            if (!running) return
            if (received == 0L) {
                failures++
                // Le service de la TV s'arrête sans téléphone : on le relance.
                started = failures < 2
                Thread.sleep(800)
            } else {
                failures = 0
            }
        }
        if (running) onStatus(tr("Son indisponible : la TV ne l'envoie pas (Android 10 ou plus requis)"))
    }

    /** Reçoit et joue le son jusqu'à la coupure ; renvoie le nombre d'octets reçus. */
    private fun play(): Long {
        // Le service de la TV met un instant à démarrer.
        var s: Socket? = null
        for (attempt in 0 until 10) {
            if (!running) return 0
            s = runCatching { Socket().apply { connect(InetSocketAddress(host, PORT), 1500) } }.getOrNull()
            if (s != null) break
            Thread.sleep(400)
        }
        val connected = s ?: run {
            connectFailures++
            lastError = "port $PORT de la TV injoignable"
            return 0
        }
        connections++
        socket = connected
        connected.use { sock ->
            sock.soTimeout = 5000
            sock.receiveBufferSize = 256 * 1024
            val input = DataInputStream(sock.getInputStream().buffered(64 * 1024))
            require(input.readInt() == MAGIC) { tr("réponse inattendue de la TV") }
            val rate = input.readInt()
            val channels = input.readInt()
            val bytesPerSecond = rate * channels * 2
            val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
                AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                maxOf(min * 4, bytesPerSecond),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            var total = 0L
            var silentBytes = 0L
            var warnedSilence = false
            onStatus(null)
            try {
                // Retard volontaire : on remplit la réserve avant de jouer.
                val delay = ByteArray(bytesPerSecond * DELAY_MS / 1000 / 4 * 4)
                track.write(delay, 0, delay.size)
                track.play()
                val frameBytes = channels * 2
                var writtenFrames = (delay.size / frameBytes).toLong()
                val maxLagFrames = rate.toLong() * (DELAY_MS + 400) / 1000
                val chunk = ByteArray(bytesPerSecond / 50)
                while (running) {
                    // Morceaux entiers (20 ms) : AudioTrack n'accepte que des échantillons complets.
                    val n = try {
                        input.readFully(chunk)
                        chunk.size
                    } catch (e: java.io.EOFException) {
                        -1
                    }
                    if (n < 0) break
                    total += n
                    bytes += n
                    // Retard qui s'accumule (réseau, horloges) : on saute ce morceau.
                    val lag = writtenFrames - (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
                    if (lag <= maxLagFrames) {
                        track.write(chunk, 0, n)
                        writtenFrames += n / frameBytes
                    }
                    if (isSilent(chunk, n)) silentBytes += n else {
                        silentBytes = 0
                        if (warnedSilence) {
                            warnedSilence = false
                            onStatus(null)
                        }
                    }
                    if (!warnedSilence && silentBytes > bytesPerSecond * 6L) {
                        warnedSilence = true
                        onStatus(
                            tr("Aucun son capté : la source actuelle ne le permet pas ") +
                                tr("(application protégée, ou son de la TV en direct non accessible)."),
                        )
                    }
                }
            } finally {
                runCatching { track.pause() }
                runCatching { track.flush() }
                track.release()
                socket = null
            }
            return total
        }
    }

    private fun isSilent(pcm: ByteArray, length: Int): Boolean {
        var i = 0
        var silent = true
        while (i + 1 < length) {
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val level = kotlin.math.abs(sample)
            if (level > peak) peak = level
            if (sample > 40 || sample < -40) silent = false
            i += 8
        }
        return silent
    }

    companion object {
        const val PORT = 47321
        private const val MAGIC = 0x4F5A4155 // "OZAU"
        /** Retard du son, pour suivre l'image de la TV (encodée puis décodée). */
        const val DELAY_MS = 350
    }
}
