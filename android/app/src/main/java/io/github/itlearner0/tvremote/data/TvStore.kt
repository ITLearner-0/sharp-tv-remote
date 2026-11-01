package io.github.itlearner0.tvremote.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** TV enregistrée : tout ce qu'il faut pour la retrouver et la réveiller. */
data class SavedTv(
    val id: String,
    val name: String,
    val host: String,
    val mac: String? = null,
    /** Nom annoncé sur le réseau (mDNS) : permet de retrouver la TV si son adresse IP change. */
    val serviceName: String? = null,
    /** Jeu de codes infrarouges : TV infrarouge, ou infrarouge associé à une TV Wi-Fi. */
    val irCodeset: String? = null,
    /** Mode de commande : [CONTROL_AUTO], [CONTROL_NETWORK], [CONTROL_IR] ou [CONTROL_BT]. */
    val control: String = CONTROL_AUTO,
    /** Adresse Bluetooth de la TV : le téléphone s'y connecte comme télécommande Bluetooth. */
    val btAddress: String? = null,
) {
    /** TV commandée uniquement en Bluetooth (pas de connexion réseau). */
    val isBt: Boolean get() = btAddress != null && (control == CONTROL_BT || host.isBlank() && irCodeset == null)

    /** TV commandée uniquement par infrarouge (pas de connexion réseau). */
    val isIr: Boolean get() = irCodeset != null && !isBt && (host.isBlank() || control == CONTROL_IR)

    /** TV sans connexion réseau (infrarouge ou Bluetooth seulement). */
    val offline: Boolean get() = isIr || isBt

    /** TV Wi-Fi qui bascule sur l'infrarouge quand elle est injoignable par le réseau. */
    val irFallback: Boolean get() = irCodeset != null && !offline && control == CONTROL_AUTO

    /** TV Wi-Fi qui bascule sur le Bluetooth quand elle est injoignable par le réseau. */
    val btFallback: Boolean get() = btAddress != null && !offline && control == CONTROL_AUTO

    companion object {
        /** Wi-Fi, et infrarouge quand la TV ne répond pas par le réseau. */
        const val CONTROL_AUTO = "auto"
        const val CONTROL_NETWORK = "network"
        const val CONTROL_IR = "ir"
        const val CONTROL_BT = "bt"
    }
}

/**
 * Liste des TV enregistrées et TV sélectionnée, conservées jusqu'à ce que
 * l'utilisateur les modifie ou les supprime.
 */
class TvStore(private val prefs: SharedPreferences) {

    init {
        migrateSingleTv()
    }

    fun all(): List<SavedTv> {
        val json = prefs.getString(KEY_TVS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                SavedTv(
                    id = o.getString("id"),
                    name = o.optString("name", "TV"),
                    host = o.getString("host"),
                    mac = o.optString("mac").ifBlank { null },
                    serviceName = o.optString("serviceName").ifBlank { null },
                    irCodeset = o.optString("irCodeset").ifBlank { null },
                    control = o.optString("control").ifBlank { SavedTv.CONTROL_AUTO },
                    btAddress = o.optString("btAddress").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun current(): SavedTv? {
        val tvs = all()
        val id = prefs.getString(KEY_CURRENT, null)
        return tvs.firstOrNull { it.id == id } ?: tvs.firstOrNull()
    }

    fun select(id: String) {
        prefs.edit().putString(KEY_CURRENT, id).apply()
    }

    /** Ajoute ou met à jour une TV (même identifiant). */
    fun save(tv: SavedTv) {
        val tvs = all().filterNot { it.id == tv.id } + tv
        write(tvs)
    }

    fun remove(id: String) {
        write(all().filterNot { it.id == id })
        if (prefs.getString(KEY_CURRENT, null) == id) prefs.edit().remove(KEY_CURRENT).apply()
    }

    fun findByHost(host: String): SavedTv? = all().firstOrNull { it.host == host && host.isNotBlank() }

    fun newId(): String = UUID.randomUUID().toString()

    private fun write(tvs: List<SavedTv>) {
        val array = JSONArray()
        for (tv in tvs) {
            array.put(
                JSONObject()
                    .put("id", tv.id)
                    .put("name", tv.name)
                    .put("host", tv.host)
                    .put("mac", tv.mac ?: "")
                    .put("serviceName", tv.serviceName ?: "")
                    .put("irCodeset", tv.irCodeset ?: "")
                    .put("control", tv.control)
                    .put("btAddress", tv.btAddress ?: ""),
            )
        }
        prefs.edit().putString(KEY_TVS, array.toString()).apply()
    }

    /** Version précédente : une seule TV, enregistrée dans « host » et « mac ». */
    private fun migrateSingleTv() {
        val host = prefs.getString("host", null) ?: return
        if (prefs.contains(KEY_TVS)) return
        val tv = SavedTv(id = newId(), name = "TV", host = host, mac = prefs.getString("mac", null))
        write(listOf(tv))
        prefs.edit()
            .putString(KEY_CURRENT, tv.id)
            .remove("host")
            .remove("mac")
            .apply()
        AppCatalog.migrate(prefs, tv.id)
    }

    private companion object {
        const val KEY_TVS = "tvs"
        const val KEY_CURRENT = "current_tv"
    }
}
