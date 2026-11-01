package io.github.itlearner0.tvremote.brand

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Marque de TV pilotée par son protocole Wi-Fi propre. */
enum class Brand(val id: String, val label: String) {
    SAMSUNG("samsung", "Samsung"),
    LG("lg", "LG");

    companion object {
        fun of(id: String?): Brand? = entries.firstOrNull { it.id == id }
    }
}

/** La TV a refusé la demande d'accès (refus sur la TV, ou délai dépassé). */
class BrandRefusedException(message: String) : Exception(message)

/**
 * Télécommande Wi-Fi d'une TV Samsung ou LG. [open] se connecte ; à la première
 * connexion, la TV affiche une demande d'accès à accepter avec sa télécommande
 * d'origine, et la clé renvoyée est à conserver pour les fois suivantes.
 */
interface BrandRemote {
    val brand: Brand
    val isOpen: Boolean

    /** Se connecte (bloquant) et renvoie la clé d'accès à conserver (peut être celle déjà connue). */
    fun open(timeoutMs: Long = 60_000): String?

    /** false si la touche n'existe pas sur cette TV. */
    fun key(code: Int): Boolean
    fun text(value: String)
    fun launch(appId: String)
    fun powerOff()
    fun close()

    /** Adresse MAC de la TV, si elle la donne (pour l'allumer par le réseau). */
    fun macAddress(): String? = null

    /** Nom du modèle, si la TV le donne. */
    fun modelName(): String? = null

    companion object {
        fun create(brand: Brand, host: String, key: String?, clientName: String, onClosed: () -> Unit): BrandRemote =
            when (brand) {
                Brand.SAMSUNG -> SamsungRemote(host, key, clientName, onClosed)
                Brand.LG -> LgRemote(host, key, onClosed)
            }
    }
}

/**
 * Client HTTP / WebSocket pour les TV du réseau local : elles présentent un
 * certificat auto-signé, accepté ici (connexion limitée à l'adresse de la TV).
 */
internal object LanHttp {
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    val client: OkHttpClient by lazy {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    val quick: OkHttpClient by lazy {
        client.newBuilder().connectTimeout(1500, TimeUnit.MILLISECONDS).readTimeout(2500, TimeUnit.MILLISECONDS).build()
    }
}
