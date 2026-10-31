package io.github.itlearner0.tvremote.protocol

import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/** La TV ne reconnaît pas cet appareil : il faut (re)faire l'appairage. */
class PairingRequiredException(message: String) : IOException(message)

data class DeviceInfo(val manufacturer: String, val model: String)

data class VolumeInfo(val level: Int, val max: Int, val muted: Boolean)

/** État de la TV, envoyé par celle-ci au fil de l'eau. */
data class TvState(
    val isOn: Boolean = false,
    val currentApp: String = "",
    val device: DeviceInfo? = null,
    val volume: VolumeInfo? = null,
    /** Fonctions annoncées par la TV (masque de bits du protocole), pour le diagnostic. */
    val features: Int = 0,
)

/**
 * Connexion de télécommande (port 6466).
 *
 * [connect] bloque jusqu'à ce que la TV soit prête. Un fil de lecture répond
 * ensuite aux messages de la TV (configuration, pings) et publie son état via
 * [onState]. [onClosed] est appelé une seule fois quand la connexion se ferme.
 */
class RemoteSession(
    private val host: String,
    private val identity: ClientIdentity,
    private val onState: (TvState) -> Unit = {},
    private val onClosed: (Throwable?) -> Unit = {},
    private val port: Int = REMOTE_PORT,
    private val timeoutMs: Int = 10_000,
) : Closeable {
    private var socket: SSLSocket? = null
    private val ready = CountDownLatch(1)
    @Volatile private var voiceBegin: java.util.concurrent.ArrayBlockingQueue<Int>? = null
    private val writeLock = Any()
    @Volatile private var closed = false
    @Volatile private var receivedAny = false
    @Volatile private var activeFeatures = FEATURES
    @Volatile private var imeCounter = 0
    @Volatile private var imeFieldCounter = 0
    @Volatile var state = TvState()
        private set

    /** Certificat de la TV : son sujet contient son adresse MAC. */
    var serverCertificate: X509Certificate? = null
        private set

    val macAddress: String?
        get() = serverSubject?.let(::parseMacFromSubject)

    /** Sujet du certificat de la TV (diagnostic). */
    val serverSubject: String?
        get() = serverCertificate?.subjectX500Principal?.name

    fun connect() {
        val s = identity.sslContext().socketFactory.createSocket() as SSLSocket
        socket = s
        try {
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
        } catch (e: IOException) {
            close() // TV éteinte ou injoignable
            throw e
        }
        try {
            s.startHandshake()
        } catch (e: SocketTimeoutException) {
            close()
            throw e
        } catch (e: IOException) {
            // La TV accepte la connexion puis refuse notre certificat (alerte TLS ou
            // coupure, selon le minutage) : elle ne connaît pas encore ce téléphone.
            close()
            throw PairingRequiredException("La TV demande un appairage")
        }
        serverCertificate = s.session.peerCertificates.firstOrNull() as? X509Certificate

        var failure: Throwable? = null
        thread(name = "tv-remote-reader", isDaemon = true) {
            try {
                // La TV envoie un ping toutes les 5 s : au-delà de 16 s, la connexion est morte.
                s.soTimeout = IDLE_TIMEOUT_MS
                while (!closed) {
                    val message = ProtoMessage(readDelimited(s.inputStream))
                    receivedAny = true
                    handle(message)
                }
            } catch (e: Throwable) {
                // Une TV qui ne connaît pas notre certificat coupe la connexion
                // avant d'envoyer le moindre message (alerte TLS ou simple fermeture).
                failure = if (ready.count > 0 && (e is SSLException || !receivedAny)) {
                    PairingRequiredException("La TV demande un appairage")
                } else e
            } finally {
                ready.countDown()
                val wasOpen = !closed
                close()
                if (wasOpen) onClosed(failure)
            }
        }
        if (!ready.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)) {
            close()
            throw SocketTimeoutException("La TV ne répond pas")
        }
        if (closed) throw failure as? IOException ?: IOException("Connexion fermée par la TV", failure)
    }

    fun sendKey(keyCode: Int, direction: Int = Keys.SHORT) = send {
        message(10) { int(1, keyCode); int(2, direction) }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        val position = text.length - 1
        send {
            message(21) {
                int(1, imeCounter)
                int(2, imeFieldCounter)
                message(3) {
                    int(1, 1)
                    message(2) { int(1, position); int(2, position); string(3, text) }
                }
            }
        }
    }

    /** Ouvre une application : nom de paquet (ex. com.netflix.ninja) ou lien. */
    fun launchApp(packageOrLink: String) {
        val link = if (packageOrLink.contains("://")) packageOrLink else "market://launch?id=$packageOrLink"
        send { message(90) { string(1, link) } }
    }

    val isOpen: Boolean get() = !closed && socket != null

    /** La TV accepte la voix (micro du téléphone → Assistant Google de la TV). */
    val voiceSupported: Boolean get() = activeFeatures and FEATURE_VOICE != 0

    /**
     * Ouvre une session vocale : la touche Recherche est envoyée, la TV répond
     * par un numéro de session. Renvoie ce numéro, ou null si la TV ne répond pas.
     */
    fun startVoice(timeoutMs: Long = 2_500): Int? {
        val queue = java.util.concurrent.ArrayBlockingQueue<Int>(1)
        voiceBegin = queue
        try {
            sendKey(Keys.SEARCH)
            val id = queue.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
            send { message(30) { int(1, id) } }
            return id
        } finally {
            voiceBegin = null
        }
    }

    /**
     * Envoie de l'audio PCM 16 bits mono 8 000 Hz. La TV refuse les morceaux
     * de plus de 20 Ko et de moins de 8 Ko : découpage et complément par du silence.
     */
    fun sendVoice(sessionId: Int, pcm: ByteArray) {
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + VOICE_CHUNK_MAX, pcm.size)
            var chunk = pcm.copyOfRange(offset, end)
            if (chunk.size < VOICE_CHUNK_MIN) chunk = chunk.copyOf(VOICE_CHUNK_MIN)
            send { message(31) { int(1, sessionId); bytes(2, chunk) } }
            offset = end
        }
    }

    fun endVoice(sessionId: Int) = send { message(32) { int(1, sessionId) } }

    override fun close() {
        closed = true
        runCatching { socket?.close() }
    }

    private fun send(build: ProtoWriter.() -> Unit) {
        val s = socket
        if (closed || s == null) throw IOException("TV non connectée")
        synchronized(writeLock) { writeDelimited(s.outputStream, ProtoWriter().apply(build).toByteArray()) }
    }

    private fun handle(msg: ProtoMessage) {
        when {
            msg.has(1) -> { // remote_configure
                val configure = msg.message(1)!!
                val info = configure.message(2)
                activeFeatures = FEATURES and configure.int(1)
                updateState {
                    copy(device = DeviceInfo(info?.string(2).orEmpty(), info?.string(1).orEmpty()), features = configure.int(1))
                }
                send {
                    message(1) {
                        int(1, activeFeatures)
                        message(2) {
                            int(3, 1)
                            string(4, "1")
                            string(5, "atvremote")
                            string(6, "1.0.0")
                        }
                    }
                }
            }
            msg.has(2) -> send { message(2) { int(1, activeFeatures) } } // remote_set_active
            msg.has(8) -> send { message(9) { int(1, msg.message(8)!!.int(1)) } } // ping
            msg.has(20) -> { // remote_ime_key_inject : application au premier plan
                val app = msg.message(20)!!.message(1)?.string(12).orEmpty()
                if (app.isNotEmpty()) updateState { copy(currentApp = app) }
            }
            msg.has(21) -> { // remote_ime_batch_edit : compteurs pour la saisie de texte
                val edit = msg.message(21)!!
                imeCounter = edit.int(1)
                imeFieldCounter = edit.int(2)
            }
            msg.has(40) -> { // remote_start : TV prête, allumée ou en veille
                updateState { copy(isOn = msg.message(40)!!.bool(1)) }
                ready.countDown()
            }
            msg.has(30) -> voiceBegin?.offer(msg.message(30)!!.int(1)) // remote_voice_begin
            msg.has(50) -> { // remote_set_volume_level
                val volume = msg.message(50)!!
                updateState { copy(volume = VolumeInfo(volume.int(7), volume.int(6), volume.bool(8))) }
            }
        }
    }

    private inline fun updateState(change: TvState.() -> TvState) {
        state = state.change()
        onState(state)
    }

    companion object {
        const val REMOTE_PORT = 6466
        private const val IDLE_TIMEOUT_MS = 16_000
        // PING | KEY | IME | VOICE | POWER | VOLUME | APP_LINK
        private const val FEATURES = 1 or 2 or 4 or 8 or 32 or 64 or 512
        const val FEATURE_VOICE = 8
        const val VOICE_SAMPLE_RATE = 8_000
        private const val VOICE_CHUNK_MIN = 8 * 1024
        private const val VOICE_CHUNK_MAX = 20 * 1024
        const val FEATURE_POWER = 32
        // « \x3A » = deux-points : sur Android (ICU), « [: … :] » dans une expression a un sens spécial.
        private val MAC_SEPARATED = Regex("(?<![0-9A-Fa-f])([0-9A-Fa-f]{2}[\\x3A-]){5}[0-9A-Fa-f]{2}(?![0-9A-Fa-f])")
        private val MAC_BARE = Regex("(?<=[/=])[0-9A-Fa-f]{12}(?=$|[/,])")

        /**
         * Adresse MAC inscrite par la TV dans son certificat, par exemple
         * « CN=atvremote/darcy/darcy/SHIELD Android TV/XX:XX:XX:XX:XX:XX ».
         */
        fun parseMacFromSubject(subject: String): String? {
            val raw = MAC_SEPARATED.find(subject)?.value ?: MAC_BARE.find(subject)?.value ?: return null
            val digits = raw.filter { it.isLetterOrDigit() }.uppercase()
            if (digits == "000000000000" || digits == "FFFFFFFFFFFF") return null
            return digits.chunked(2).joinToString(":")
        }
    }
}
