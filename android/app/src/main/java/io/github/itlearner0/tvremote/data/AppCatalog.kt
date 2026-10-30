package io.github.itlearner0.tvremote.data

import android.content.SharedPreferences
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

data class TvApp(val packageName: String, val label: String)

/**
 * Applications de la TV.
 *
 * Le protocole de télécommande ne sait pas lister les applications : la liste
 * complète est lue par ADB (débogage activé sur la TV), complétée par les
 * applications ouvertes sur la TV, retenues au fil de l'eau.
 */
class AppCatalog(private val prefs: SharedPreferences) {

    val isComplete: Boolean get() = installed().isNotEmpty()

    fun list(): List<TvApp> {
        val packages = installed().ifEmpty { DEFAULT_APPS.toSet() } + seen()
        return packages
            .filterNot { pkg -> HIDDEN_PREFIXES.any { pkg.startsWith(it) } }
            .map { TvApp(it, labelFor(it)) }
            .sortedBy { it.label.lowercase() }
    }

    /** Retient une application qui vient d'être ouverte sur la TV. Renvoie true si elle est nouvelle. */
    fun remember(packageName: String): Boolean {
        if (packageName.isBlank() || HIDDEN_PREFIXES.any { packageName.startsWith(it) }) return false
        val seen = seen()
        if (packageName in seen) return false
        prefs.edit().putStringSet(KEY_SEEN, seen + packageName).apply()
        return true
    }

    fun setInstalled(packages: Collection<String>) {
        prefs.edit().putStringSet(KEY_INSTALLED, packages.toSet()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SEEN).remove(KEY_INSTALLED).apply()
    }

    private fun seen(): Set<String> = prefs.getStringSet(KEY_SEEN, null).orEmpty()

    private fun installed(): Set<String> = prefs.getStringSet(KEY_INSTALLED, null).orEmpty()

    companion object {
        private const val KEY_SEEN = "apps_seen"
        private const val KEY_INSTALLED = "apps_installed"

        private val KNOWN_APPS = mapOf(
            "com.netflix.ninja" to "Netflix",
            "com.google.android.youtube.tv" to "YouTube",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
            "com.google.android.youtube.tvmusic" to "YouTube Music",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "com.disney.disneyplus" to "Disney+",
            "com.spotify.tv.android" to "Spotify",
            "deezer.android.tv" to "Deezer",
            "com.canal.android.canal" to "myCANAL",
            "fr.francetv.pluzz" to "france.tv",
            "fr.tf1.mytf1" to "TF1+",
            "fr.m6.m6replay.by" to "M6+",
            "tv.arte.plus7" to "ARTE",
            "tv.molotov.app" to "Molotov",
            "com.apple.atve.androidtv.appletv" to "Apple TV",
            "com.cbs.ca" to "Paramount+",
            "com.wbd.stream" to "Max",
            "com.plexapp.android" to "Plex",
            "org.xbmc.kodi" to "Kodi",
            "org.videolan.vlc" to "VLC",
            "tv.twitch.android.app" to "Twitch",
            "com.google.android.videos" to "Google TV",
            "com.android.vending" to "Play Store",
            "com.android.tv.settings" to "Paramètres",
            "com.google.android.apps.mediashell" to "Chromecast",
            "com.google.android.play.games" to "Play Jeux",
            "com.android.chrome" to "Chrome",
            "com.mediatek.wwtv.tvcenter" to "TV en direct",
            "com.android.tv" to "TV en direct",
            "com.google.android.tv" to "Chaînes en direct",
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

        /**
         * Lit la liste des applications par ADB (port 5555). Bloquant ; la TV
         * demande d'autoriser l'appareil à la première connexion.
         */
        fun scanInstalledApps(host: String, keyDir: File): List<String> {
            val privateKey = File(keyDir, "adbkey")
            val publicKey = File(keyDir, "adbkey.pub")
            if (!privateKey.exists() || !publicKey.exists()) {
                keyDir.mkdirs()
                AdbKeyPair.generate(privateKey, publicKey)
            }
            val keyPair = AdbKeyPair.read(privateKey, publicKey)
            Dadb.create(host, 5555, keyPair, 10_000, 70_000).use { adb ->
                val launchable = adb.shell(
                    "cmd package query-activities --brief " +
                        "-a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER",
                ).output
                val packages = Regex("""^\s*([\w.]+)/""", RegexOption.MULTILINE)
                    .findAll(launchable).map { it.groupValues[1] }.toSet()
                if (packages.isNotEmpty()) return packages.toList()
                // Anciennes versions d'Android : applications ajoutées + applications connues.
                val pkg = Regex("""^package:([\w.]+)""", RegexOption.MULTILINE)
                val all = pkg.findAll(adb.shell("pm list packages").output).map { it.groupValues[1] }.toSet()
                val thirdParty = pkg.findAll(adb.shell("pm list packages -3").output).map { it.groupValues[1] }.toSet()
                return (thirdParty + KNOWN_APPS.keys.filter { it in all }).toList()
            }
        }
    }
}
