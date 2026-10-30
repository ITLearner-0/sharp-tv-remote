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
)

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

    fun findByHost(host: String): SavedTv? = all().firstOrNull { it.host == host }

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
                    .put("serviceName", tv.serviceName ?: ""),
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
