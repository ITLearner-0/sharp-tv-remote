package io.github.itlearner0.tvremote.data

import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

/** Application installée sur la TV, telle que lue par ADB. */
data class InstalledApp(
    val packageName: String,
    /** Composant à lancer (paquet/activité). */
    val component: String,
    /** true : application conçue pour la TV ; false : application de téléphone installée sur la TV. */
    val tvApp: Boolean,
)

/**
 * Accès « débogage » (ADB, port 5555) à la TV : liste complète des
 * applications, adresse MAC, et commandes de secours. Appels bloquants.
 * La TV demande d'autoriser ce téléphone à la première connexion.
 */
class TvAdb(private val keyDir: File) {

    fun scanApps(host: String): List<InstalledApp> = withAdb(host) { adb ->
        val tvApps = launchable(adb, "android.intent.category.LEANBACK_LAUNCHER")
        val phoneApps = launchable(adb, "android.intent.category.LAUNCHER")
        val apps = LinkedHashMap<String, InstalledApp>()
        for ((pkg, component) in tvApps) apps[pkg] = InstalledApp(pkg, component, tvApp = true)
        for ((pkg, component) in phoneApps) apps.putIfAbsent(pkg, InstalledApp(pkg, component, tvApp = false))
        if (apps.isEmpty()) {
            // Anciennes versions d'Android sans « query-activities » : applications ajoutées.
            val pkg = Regex("""^package:([\w.]+)""", RegexOption.MULTILINE)
            for (m in pkg.findAll(adb.shell("pm list packages -3").output)) {
                val name = m.groupValues[1]
                apps[name] = InstalledApp(name, "", tvApp = true)
            }
        }
        apps.values.toList()
    }

    /** Adresse MAC de l'interface réseau qui porte l'adresse IP [host]. */
    fun macAddress(host: String): String? = withAdb(host) { adb ->
        val addresses = adb.shell("ip -o -4 addr show").output
        val iface = addresses.lineSequence()
            .firstOrNull { " $host/" in it }
            ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)
        val candidates = listOfNotNull(iface, "eth0", "wlan0").distinct()
        candidates.firstNotNullOfOrNull { name ->
            adb.shell("cat /sys/class/net/$name/address").output.trim().uppercase()
                .takeIf { Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}").matches(it) && it != "00:00:00:00:00:00" }
        }
    }

    fun launch(host: String, app: InstalledApp) {
        withAdb(host) { adb ->
            if (app.component.isNotEmpty()) {
                adb.shell("am start -n ${app.component}")
            } else {
                adb.shell("monkey -p ${app.packageName} 1")
            }
        }
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

    private fun launchable(adb: Dadb, category: String): List<Pair<String, String>> {
        val output = adb.shell(
            "cmd package query-activities --brief -a android.intent.action.MAIN -c $category",
        ).output
        return Regex("""^\s*([\w.]+)/([\w.$]+)""", RegexOption.MULTILINE)
            .findAll(output)
            .map { it.groupValues[1] to "${it.groupValues[1]}/${it.groupValues[2]}" }
            .toList()
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
}
