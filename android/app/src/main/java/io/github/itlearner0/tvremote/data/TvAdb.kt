package io.github.itlearner0.tvremote.data

import dadb.AdbKeyPair
import io.github.itlearner0.tvremote.protocol.AppListParser
import io.github.itlearner0.tvremote.protocol.ListedApp
import io.github.itlearner0.tvremote.protocol.MacSources
import dadb.Dadb
import java.io.File

/** Application installée sur la TV, telle que lue par ADB. */
data class InstalledApp(
    val packageName: String,
    /** Composant à lancer (paquet/activité). */
    val component: String,
    /** true : application conçue pour la TV ; false : application de téléphone installée sur la TV. */
    val tvApp: Boolean,
    /** Vrai nom de l'application (lu par l'application d'aide), ou null. */
    val label: String? = null,
)

/**
 * Accès « débogage » (ADB, port 5555) à la TV : liste complète des
 * applications, adresse MAC, et commandes de secours. Appels bloquants.
 * La TV demande d'autoriser ce téléphone à la première connexion.
 */
class TvAdb(private val keyDir: File) {

    /**
     * Liste exacte des applications que l'écran d'accueil de la TV peut lancer,
     * avec leurs vrais noms et icônes : une petite application d'aide est
     * installée, interrogée, puis aussitôt désinstallée.
     */
    fun listWithHelper(host: String, helperApk: File): List<ListedApp> = withAdb(host) { adb ->
        adb.install(helperApk, "-r")
        try {
            val output = adb.shell("am instrument -r -w $HELPER_PACKAGE/.AppLister").allOutput
            val apps = AppListParser.parse(output)
            check(apps.isNotEmpty()) { "l'application d'aide n'a renvoyé aucune application" }
            apps
        } finally {
            runCatching { adb.shell("pm uninstall $HELPER_PACKAGE") }
            viewerReady -= host
        }
    }

    /**
     * Visionneuse de la TV (application d'aide, sans Google Cast) : affiche la
     * photo ou lit la vidéo à l'adresse [url], servie par le téléphone.
     * Installe l'application d'aide si besoin. Renvoie null, ou l'erreur de la TV.
     */
    fun showMedia(host: String, helperApk: File?, url: String, mimeType: String): String? = withAdb(host) { adb ->
        ensureHelper(adb, host, helperApk)?.let { return@withAdb it }
        startHelper(adb, host, "Viewer", "-a android.intent.action.VIEW -d '$url' -t '$mimeType'")
    }

    /**
     * Duplication de l'écran : ouvre sur la TV le récepteur de l'application
     * d'aide, qui se connecte au téléphone ([phoneHost]:[port]). Renvoie null, ou l'erreur.
     */
    fun startMirror(host: String, helperApk: File?, phoneHost: String, port: Int): String? = withAdb(host) { adb ->
        ensureHelper(adb, host, helperApk)?.let { return@withAdb it }
        startHelper(adb, host, "Mirror", "--es host $phoneHost --ei port $port")
    }

    /** Dernières lignes du journal du récepteur de duplication (erreurs de la TV). */
    fun mirrorLog(host: String): String = withAdb(host) { adb ->
        runCatching { adb.shell("logcat -d -v brief -s OmniZapMirror:V AndroidRuntime:E | tail -n 25").allOutput }
            .getOrElse { "journal de la TV illisible (${it.message})" }
    }

    fun stopMirror(host: String) {
        withAdb(host) { adb -> adb.shell("am start -n $HELPER_PACKAGE/.Mirror --es cmd stop") }
    }

    /** Installe l'application d'aide si elle manque ou est ancienne. Renvoie null, ou l'erreur. */
    private fun ensureHelper(adb: Dadb, host: String, helperApk: File?): String? {
        if (host in viewerReady) return null
        val installed = runCatching { adb.shell("dumpsys package $HELPER_PACKAGE | grep versionCode").allOutput }.getOrDefault("")
        if ("versionCode=$HELPER_VERSION " !in "$installed ") {
            helperApk ?: return "application d'aide absente de l'APK"
            runCatching { adb.install(helperApk, "-r") }.exceptionOrNull()?.let {
                return "installation de l'application d'aide refusée (${it.message})"
            }
        }
        viewerReady += host
        return null
    }

    private fun startHelper(adb: Dadb, host: String, activity: String, args: String): String? {
        val out = runCatching { adb.shell("am start -n $HELPER_PACKAGE/.$activity $args").allOutput }
            .getOrElse { "Error: ${it.message}" }
        if ("Error" !in out && "Exception" !in out) return null
        viewerReady -= host
        return out.lines().firstOrNull { "Error" in it || "Exception" in it }?.trim() ?: out.trim()
    }

    /** Commande à la visionneuse : « playpause », « seek » (avec [deltaMs]) ou « stop ». */
    fun viewerCommand(host: String, command: String, deltaMs: Int = 0) {
        withAdb(host) { adb ->
            adb.shell("am start -n $HELPER_PACKAGE/.Viewer --es cmd $command --ei delta $deltaMs")
        }
    }

    /**
     * Applications réellement installées et activées sur la TV :
     * - toutes celles installées par l'utilisateur (liste officielle des paquets),
     *   quelle que soit leur façon de se lancer ;
     * - les applications préinstallées qui ont une icône de lancement.
     */
    fun scanApps(host: String): List<InstalledApp> = withAdb(host) { adb ->
        val tvLaunchers = launchable(adb, "android.intent.category.LEANBACK_LAUNCHER")
        val phoneLaunchers = launchable(adb, "android.intent.category.LAUNCHER")
        val userInstalled = packages(adb, "pm list packages -3 -e")
        val enabled = packages(adb, "pm list packages -e").ifEmpty { userInstalled + tvLaunchers.keys + phoneLaunchers.keys }
        enabled
            .filter { it in userInstalled || it in tvLaunchers || it in phoneLaunchers }
            .map { pkg ->
                InstalledApp(
                    packageName = pkg,
                    component = tvLaunchers[pkg] ?: phoneLaunchers[pkg].orEmpty(),
                    // Seules les applications TV s'ouvrent par la télécommande ; les autres par ADB.
                    tvApp = pkg in tvLaunchers,
                )
            }
    }

    private fun packages(adb: Dadb, command: String): Set<String> =
        Regex("""^package:([\w.]+)""", RegexOption.MULTILINE)
            .findAll(runCatching { adb.shell(command).output }.getOrDefault(""))
            .map { it.groupValues[1] }
            .toSet()

    /**
     * Adresse MAC de l'interface réseau qui porte l'adresse IP [host].
     * Plusieurs méthodes, car les versions récentes d'Android en restreignent
     * certaines. [log] reçoit le résultat de chaque essai (diagnostic).
     */
    fun macAddress(host: String, log: MutableList<String> = mutableListOf()): String? = withAdb(host) { adb ->
        fun sh(command: String): String = runCatching { adb.shell(command).allOutput }.getOrDefault("")

        val iface = sh("ip -o -4 addr show").lineSequence()
            .firstOrNull { " $host/" in it }
            ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)
        log += "interface de $host : ${iface ?: "inconnue"}"
        val interfaces = listOfNotNull(iface, "eth0", "wlan0").distinct()
        val attempts = interfaces.flatMap { name ->
            listOf("cat /sys/class/net/$name/address", "ip link show $name")
        } + listOf(
            "dumpsys ethernet | grep -iE 'hwaddr|mac'",
            "dumpsys wifi | grep -iE 'mac address|macaddress|factory mac'",
            "getprop | grep -iE 'mac'",
        )
        for (command in attempts) {
            val mac = MacSources.firstMac(sh(command))
            log += "$command → ${mac ?: "rien"}"
            if (mac != null) return@withAdb mac
        }
        null
    }

    /**
     * Ouvre une application sur la TV. Essaie l'activité connue, puis laisse
     * Android choisir l'activité de lancement TV, puis téléphone.
     * Renvoie true si la TV a confirmé le lancement.
     */
    fun launch(host: String, app: InstalledApp): Boolean = withAdb(host) { adb ->
        fun sh(command: String) = runCatching { adb.shell(command).allOutput }.getOrDefault("Error")
        if (app.component.isNotEmpty()) {
            val out = sh("am start -n ${app.component}")
            if ("Error" !in out && "Exception" !in out) return@withAdb true
        }
        val pkg = app.packageName
        for (category in listOf("android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER")) {
            val out = sh("monkey -p $pkg -c $category 1")
            if ("Events injected: 1" in out) return@withAdb true
        }
        false
    }

    /** Lance une recherche sur la TV (écran de recherche Google de la TV). */
    fun globalSearch(host: String, query: String): Boolean = withAdb(host) { adb ->
        val quoted = "'" + query.replace("'", "'\\''") + "'"
        for (action in listOf("android.search.action.GLOBAL_SEARCH", "android.intent.action.WEB_SEARCH", "android.intent.action.SEARCH")) {
            val out = runCatching { adb.shell("am start -a $action --es query $quoted").allOutput }.getOrDefault("Error")
            if ("Error" !in out && "Exception" !in out) return@withAdb true
        }
        false
    }

    /**
     * Tape [text] dans le champ actif de la TV (commande Android « input text »).
     * [text] doit être en ASCII : la commande ne gère pas les accents.
     */
    fun inputText(host: String, text: String) {
        val escaped = text.replace("'", "'\\''").replace(" ", "%s")
        withAdb(host) { adb -> adb.shell("input text '$escaped'") }
    }

    fun keyEvent(host: String, keyCode: Int) {
        withAdb(host) { adb -> adb.shell("input keyevent $keyCode") }
    }

    /** Paquet → activité de lancement, pour une catégorie d'icône (TV ou téléphone). */
    private fun launchable(adb: Dadb, category: String): Map<String, String> {
        val output = runCatching {
            adb.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c $category").output
        }.getOrDefault("")
        val result = LinkedHashMap<String, String>()
        for (m in Regex("""([\w.]+)/([\w.$]+)""").findAll(output)) {
            result.putIfAbsent(m.groupValues[1], "${m.groupValues[1]}/${m.groupValues[2]}")
        }
        return result
    }

    private fun <T> withAdb(host: String, block: (Dadb) -> T): T {
        val privateKey = File(keyDir, "adbkey")
        val publicKey = File(keyDir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            keyDir.mkdirs()
            AdbKeyPair.generate(privateKey, publicKey)
        }
        val keyPair = AdbKeyPair.read(privateKey, publicKey)
        // Délai de lecture large : la TV attend que l'utilisateur autorise ce téléphone.
        return Dadb.create(host, 5555, keyPair, 10_000, 70_000).use(block)
    }

    companion object {
        const val HELPER_PACKAGE = "io.github.itlearner0.tvremote.helper"
        /** versionCode de tvhelper/build.gradle.kts. */
        private const val HELPER_VERSION = 5
        /** TV sur lesquelles la visionneuse est déjà installée (pendant cette session). */
        private val viewerReady: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        /** Application d'aide embarquée (assets/tvhelper.apk), copiée pour être installée. */
        fun helperApk(context: android.content.Context): File? = runCatching {
            val file = File(context.cacheDir, "tvhelper.apk")
            context.assets.open("tvhelper.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        }.getOrNull()
    }
}
