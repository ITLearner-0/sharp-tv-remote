package io.github.itlearner0.tvremote.protocol

import java.net.HttpURLConnection
import java.net.URL

/** Sources d'adresse MAC d'une TV autres que son certificat. Appels bloquants. */
object MacSources {
    // « \x3A » = deux-points : sur Android (ICU), « [: … :] » dans une expression désigne une
    // propriété Unicode et rend l'expression invalide (alors qu'elle fonctionne en Java).
    private val MAC = Regex("""(?i)(?<![0-9a-f\x3A])(?:[0-9a-f]{2}[\x3A-]){5}[0-9a-f]{2}(?![0-9a-f\x3A])""")
    private val PLACEHOLDERS = setOf("00:00:00:00:00:00", "FF:FF:FF:FF:FF:FF", "02:00:00:00:00:00")

    fun normalize(mac: String): String = mac.replace('-', ':').uppercase()

    /** Adresse exploitable pour le réveil réseau (pas une valeur bidon renvoyée par Android). */
    fun isUsable(mac: String): Boolean = normalize(mac) !in PLACEHOLDERS

    /** Première adresse MAC exploitable trouvée dans [text]. */
    fun firstMac(text: String): String? = MAC.findAll(text).map { normalize(it.value) }.firstOrNull(::isUsable)

    /** Adresse annoncée dans la réponse JSON de Google Cast (« mac_address »). */
    fun parseCastInfo(json: String): String? =
        Regex(""""mac_address"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
            ?.let(::firstMac)

    /**
     * Les TV avec Google Cast intégré décrivent leur réseau sur le port 8008
     * (sans authentification sur beaucoup de modèles).
     */
    fun fromCast(host: String, timeoutMs: Int = 2_500): String? = runCatching {
        val connection = URL("http://$host:8008/setup/eureka_info?params=net,device_info").openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        try {
            if (connection.responseCode != 200) return null
            parseCastInfo(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
