package io.github.itlearner0.tvremote.data

import io.github.itlearner0.tvremote.i18n.tr

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class TvApp(
    val packageName: String,
    val label: String,
    /** Renseigné quand la liste vient d'ADB : permet de lancer les applications « téléphone ». */
    val installed: InstalledApp? = null,
)

/**
 * Applications d'une TV.
 *
 * Le protocole de télécommande ne sait pas lister les applications : la liste
 * complète est lue par ADB (débogage activé sur la TV), complétée par les
 * applications ouvertes sur la TV, retenues au fil de l'eau.
 */
class AppCatalog(private val prefs: SharedPreferences, tvId: String) {
    private val keySeen = "apps_seen_$tvId"
    private val keyInstalled = "apps_installed_$tvId"
    private val keyScanned = "apps_scanned_$tvId"
    private val keyList = "apps_list_$tvId"

    val isComplete: Boolean get() = installed().isNotEmpty()

    /** Heure de la dernière lecture de la liste sur la TV (0 : jamais). */
    val lastScan: Long get() = prefs.getLong(keyScanned, 0)

    /**
     * Après une lecture sur la TV : exactement les applications installées
     * (et celles ouvertes depuis). Sinon : applications courantes et celles déjà ouvertes.
     */
    fun list(): List<TvApp> {
        val installed = installed()
        val packages = installed.keys.ifEmpty { DEFAULT_APPS.toSet() } + seen()
        return packages
            .filterNot { pkg -> HIDDEN_PREFIXES.any { pkg.startsWith(it) } }
            .map { TvApp(it, installed[it]?.label ?: labelFor(it), installed[it]) }
            .sortedBy { it.label.lowercase() }
    }

    /** Retient une application qui vient d'être ouverte sur la TV. Renvoie true si elle est nouvelle. */
    fun remember(packageName: String): Boolean {
        if (packageName.isBlank() || HIDDEN_PREFIXES.any { packageName.startsWith(it) }) return false
        val seen = seen()
        if (packageName in seen) return false
        prefs.edit().putStringSet(keySeen, seen + packageName).apply()
        return true
    }

    /** Remplace la liste par celle lue sur la TV : les applications désinstallées disparaissent. */
    fun setInstalled(apps: Collection<InstalledApp>) {
        val list = JSONArray()
        for (app in apps) {
            list.put(
                JSONObject()
                    .put("p", app.packageName)
                    .put("c", app.component)
                    .put("t", app.tvApp)
                    .put("l", app.label ?: ""),
            )
        }
        prefs.edit()
            .putString(keyList, list.toString())
            .remove(keyInstalled)
            .remove(keySeen) // les applications « vues » peuvent avoir été désinstallées depuis
            .putLong(keyScanned, System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit().remove(keySeen).remove(keyInstalled).remove(keyScanned).remove(keyList).apply()
    }

    private fun seen(): Set<String> = prefs.getStringSet(keySeen, null).orEmpty()

    private fun installed(): Map<String, InstalledApp> {
        prefs.getString(keyList, null)?.let { json ->
            runCatching {
                val array = JSONArray(json)
                return List(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    InstalledApp(o.getString("p"), o.optString("c"), o.optBoolean("t"), o.optString("l").ifBlank { null })
                }.associateBy { it.packageName }
            }
        }
        return legacyInstalled()
    }

    /** Format des versions précédentes (sans les vrais noms). */
    private fun legacyInstalled(): Map<String, InstalledApp> =
        prefs.getStringSet(keyInstalled, null).orEmpty().mapNotNull { entry ->
            val parts = entry.split('|')
            if (parts.size == 3) InstalledApp(parts[0], parts[1], parts[2] == "tv") else null
        }.associateBy { it.packageName }

    companion object {
        /** Reprend les applications enregistrées par la version à une seule TV. */
        fun migrate(prefs: SharedPreferences, tvId: String) {
            val seen = prefs.getStringSet("apps_seen", null)
            prefs.edit().apply {
                if (seen != null) putStringSet("apps_seen_$tvId", seen)
                remove("apps_seen")
                remove("apps_installed") // ancien format sans composant : sera relu par ADB
            }.apply()
        }

        private val KNOWN_APPS = mapOf(
            "com.netflix.ninja" to tr("Netflix"),
            "com.google.android.youtube.tv" to tr("YouTube"),
            "com.google.android.youtube.tvkids" to tr("YouTube Kids"),
            "com.google.android.youtube.tvmusic" to tr("YouTube Music"),
            "com.amazon.amazonvideo.livingroom" to tr("Prime Video"),
            "com.disney.disneyplus" to tr("Disney+"),
            "com.spotify.tv.android" to tr("Spotify"),
            "deezer.android.tv" to tr("Deezer"),
            "com.canal.android.canal" to "myCANAL",
            "fr.francetv.pluzz" to "france.tv",
            "fr.tf1.mytf1" to tr("TF1+"),
            "fr.m6.m6replay.by" to tr("M6+"),
            "tv.arte.plus7" to "ARTE",
            "tv.molotov.app" to tr("Molotov"),
            "com.apple.atve.androidtv.appletv" to tr("Apple TV"),
            "com.cbs.ca" to tr("Paramount+"),
            "com.wbd.stream" to tr("Max"),
            "com.plexapp.android" to tr("Plex"),
            "org.xbmc.kodi" to tr("Kodi"),
            "org.videolan.vlc" to "VLC",
            "tv.twitch.android.app" to tr("Twitch"),
            "com.google.android.videos" to tr("Google TV"),
            "com.android.vending" to tr("Play Store"),
            "com.android.tv.settings" to tr("Paramètres"),
            "com.android.settings" to tr("Paramètres (avancés)"),
            "com.google.android.apps.mediashell" to tr("Chromecast"),
            "com.google.android.play.games" to tr("Play Jeux"),
            "com.android.chrome" to tr("Chrome"),
            "com.mediatek.wwtv.tvcenter" to tr("TV en direct"),
            "com.android.tv" to tr("TV en direct"),
            "com.google.android.tv" to tr("Chaînes en direct"),
        )

        private val DEFAULT_APPS = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.google.android.youtube.tvmusic",
            "com.spotify.tv.android",
        )

        private val HIDDEN_PREFIXES = listOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.android.systemui",
            "com.google.android.katniss",
            "com.google.android.tungsten",
            "com.google.android.backdrop",
            "com.google.android.inputmethod",
            "com.android.providers",
        )

        private val NOISE = setOf(
            "com", "org", "net", "fr", "tv", "android", "androidtv", "app", "apps", "google",
            "leanback", "atv", "client", "mobile", "player", "ninja", "livingroom", "main",
        )
        private val GENERIC = setOf("connect", "play", "plus", "live", "stream", "go", "now", "video", "videos", "media")

        fun labelFor(packageName: String): String {
            KNOWN_APPS[packageName]?.let { return it }
            val words = packageName.split('.', '_').filter { it.isNotEmpty() && it.lowercase() !in NOISE }
            if (words.isEmpty()) return packageName
            var name = words.last()
            if (words.size > 1 && (name.length <= 2 || name.lowercase() in GENERIC)) name = "${words[words.size - 2]} $name"
            return name.replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        }
    }
}
