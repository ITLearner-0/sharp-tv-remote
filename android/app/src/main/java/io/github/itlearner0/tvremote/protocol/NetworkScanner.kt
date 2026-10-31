package io.github.itlearner0.tvremote.protocol

import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

/** Android TV trouvée par la recherche directe. */
data class ScannedTv(val host: String, val name: String?, val mac: String?)

/**
 * Recherche directe des Android TV : sans attendre leur annonce sur le réseau
 * (mDNS, souvent lente), on teste le port de télécommande sur toutes les
 * adresses du réseau local du téléphone. Appels bloquants.
 */
object NetworkScanner {

    /** Adresses du /24 du téléphone (hors adresse du téléphone, du réseau et de diffusion). */
    fun candidateHosts(phoneIp: String): List<String> {
        val parts = phoneIp.split('.')
        if (parts.size != 4) return emptyList()
        val prefix = parts.take(3).joinToString(".")
        return (1..254).map { "$prefix.$it" }.filter { it != phoneIp }
    }

    /** Adresses parmi [hosts] dont le port [port] accepte une connexion. */
    fun findOpen(
        hosts: List<String>,
        port: Int = RemoteSession.REMOTE_PORT,
        timeoutMs: Int = 600,
        parallelism: Int = 64,
    ): List<String> {
        if (hosts.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(parallelism.coerceAtMost(hosts.size))
        try {
            val tasks = hosts.map { host ->
                Callable {
                    runCatching {
                        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                        host
                    }.getOrNull()
                }
            }
            return pool.invokeAll(tasks, timeoutMs * 4L + 2_000, TimeUnit.MILLISECONDS)
                .mapNotNull { future -> runCatching { future.get() }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * Nom et adresse MAC d'une Android TV, lus dans le certificat de son port
     * d'appairage (accessible sans être appairé).
     */
    fun identify(host: String, identity: ClientIdentity, timeoutMs: Int = 3_000): ScannedTv {
        val certificate = runCatching {
            (identity.sslContext().socketFactory.createSocket() as SSLSocket).use { s ->
                s.connect(InetSocketAddress(host, PairingSession.PAIRING_PORT), timeoutMs)
                s.soTimeout = timeoutMs
                s.startHandshake()
                s.session.peerCertificates.firstOrNull() as? X509Certificate
            }
        }.getOrNull()
        val subject = certificate?.subjectX500Principal?.name
        return ScannedTv(host, subject?.let(::parseNameFromSubject), subject?.let(RemoteSession::parseMacFromSubject))
    }

    /** Recherche complète : ports ouverts, puis identification de chaque TV. */
    fun scan(phoneIps: List<String>, identity: ClientIdentity): List<ScannedTv> {
        val hosts = phoneIps.flatMap(::candidateHosts).distinct()
        val open = findOpen(hosts)
        if (open.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(open.size.coerceAtMost(8))
        try {
            return pool.invokeAll(open.map { host -> Callable { identify(host, identity) } }, 10, TimeUnit.SECONDS)
                .mapNotNull { runCatching { it.get() }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * Nom de la TV d'après le sujet de son certificat, par exemple
     * « CN=atvremote/darcy/darcy/SHIELD Android TV/XX:XX:… » → « SHIELD Android TV ».
     */
    fun parseNameFromSubject(subject: String): String? {
        val qualifier = Regex("""(?:DNQ|DNQUALIFIER|2\.5\.4\.46)=([^,]+)""", RegexOption.IGNORE_CASE)
            .find(subject)?.groupValues?.get(1)
        if (!qualifier.isNullOrBlank() && !qualifier.startsWith("#")) return qualifier.split('/').last().trim().ifBlank { null }
        val cn = Regex("""CN=([^,]+)""").find(subject)?.groupValues?.get(1) ?: return null
        val parts = cn.split('/').filter { it.isNotBlank() }
        val name = if (parts.size > 1) parts[parts.size - 2] else parts.firstOrNull()
        return name?.takeIf { it != "atvremote" && RemoteSession.parseMacFromSubject(it) == null }
    }
}
