package io.github.itlearner0.tvremote.protocol

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Fichier (photo, vidéo) servi à la TV. */
interface MediaSource {
    val name: String
    val mimeType: String
    val size: Long

    /** Flux du fichier à partir de [offset] octets. */
    fun open(offset: Long): InputStream
}

/**
 * Petit serveur HTTP local : la TV (Google Cast) vient y chercher les photos
 * et vidéos du téléphone. Gère les requêtes partielles (Range), indispensables
 * pour avancer ou reculer dans une vidéo. Les adresses contiennent un jeton
 * aléatoire : seuls les fichiers envoyés à la TV sont accessibles.
 */
class MediaHttpServer : Closeable {
    private val server = ServerSocket(0)
    private val token = ByteArray(12).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private val sources = ConcurrentHashMap<Int, MediaSource>()
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var closed = false

    val port: Int get() = server.localPort

    init {
        thread(name = "media-http", isDaemon = true) {
            while (!closed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute { runCatching { socket.use(::handle) } }
            }
        }
    }

    /** Publie [source] et renvoie son adresse pour la TV. */
    fun publish(host: String, index: Int, source: MediaSource): String {
        sources[index] = source
        val name = URLEncoder.encode(source.name.ifBlank { "media" }, "UTF-8").replace("+", "%20")
        return "http://$host:$port/$token/$index/$name"
    }

    fun clear() = sources.clear()

    override fun close() {
        closed = true
        runCatching { server.close() }
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 30_000
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val out = socket.getOutputStream()
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val segments = parts.getOrNull(1).orEmpty().substringBefore('?').trim('/').split('/')
        val source = segments.takeIf { it.size >= 2 && it[0] == token }?.get(1)?.toIntOrNull()?.let(sources::get)
        if (source == null || (method != "GET" && method != "HEAD")) {
            respond(out, "404 Not Found", mapOf("Content-Length" to "0"))
            return
        }
        val size = source.size
        val range = headers["range"]?.let { parseRange(it, size) }
        if (headers.containsKey("range") && range == null) {
            respond(out, "416 Range Not Satisfiable", mapOf("Content-Range" to "bytes */$size", "Content-Length" to "0"))
            return
        }
        val start = range?.first ?: 0L
        val end = range?.last ?: (size - 1)
        val length = (end - start + 1).coerceAtLeast(0)
        val common = mapOf(
            "Content-Type" to source.mimeType,
            "Content-Length" to length.toString(),
            "Accept-Ranges" to "bytes",
            "Access-Control-Allow-Origin" to "*",
        )
        val status = if (range != null) "206 Partial Content" else "200 OK"
        respond(out, status, if (range != null) common + ("Content-Range" to "bytes $start-$end/$size") else common)
        if (method == "HEAD" || length == 0L) return
        source.open(start).use { stream -> copy(stream, out, length) }
        out.flush()
    }

    private fun respond(out: OutputStream, status: String, headers: Map<String, String>) {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            for ((k, v) in headers) append(k).append(": ").append(v).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun copy(input: InputStream, out: OutputStream, length: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun readLine(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (bytes.size() == 0) null else bytes.toString("ISO-8859-1")
            if (b == '\n'.code) return bytes.toString("ISO-8859-1").trimEnd('\r')
            bytes.write(b)
            if (bytes.size() > 8 * 1024) return null
        }
    }

    companion object {
        /** « bytes=0-99 », « bytes=100- », « bytes=-50 » → intervalle d'octets, ou null s'il est invalide. */
        fun parseRange(header: String, size: Long): LongRange? {
            val spec = header.removePrefix("bytes=").substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0 || size <= 0) return null
            val first = spec.substring(0, dash).trim()
            val last = spec.substring(dash + 1).trim()
            val range = when {
                first.isEmpty() -> {
                    val suffix = last.toLongOrNull() ?: return null
                    (size - suffix).coerceAtLeast(0)..(size - 1)
                }
                else -> {
                    val start = first.toLongOrNull() ?: return null
                    val end = last.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
                    start..end
                }
            }
            return range.takeIf { it.first in 0 until size && it.last >= it.first }
        }
    }
}
