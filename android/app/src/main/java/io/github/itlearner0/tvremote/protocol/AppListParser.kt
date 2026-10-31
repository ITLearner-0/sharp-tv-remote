package io.github.itlearner0.tvremote.protocol

/** Application décrite par l'application d'aide installée temporairement sur la TV. */
data class ListedApp(
    val packageName: String,
    val label: String,
    val component: String,
    val tvApp: Boolean,
    val system: Boolean,
    /** Icône PNG encodée en base64, ou null. */
    val iconBase64: String?,
)

/** Lit la sortie de « am instrument -r -w » produite par l'application d'aide. */
object AppListParser {
    private const val STATUS = "INSTRUMENTATION_STATUS: "
    private const val STATUS_CODE = "INSTRUMENTATION_STATUS_CODE: "

    fun parse(output: String): List<ListedApp> {
        val apps = ArrayList<ListedApp>()
        var current = HashMap<String, String>()
        for (line in output.lineSequence()) {
            when {
                line.startsWith(STATUS) -> {
                    val entry = line.removePrefix(STATUS)
                    val eq = entry.indexOf('=')
                    if (eq > 0) current[entry.substring(0, eq)] = entry.substring(eq + 1).trim()
                }
                line.startsWith(STATUS_CODE) -> {
                    val pkg = current["pkg"]
                    if (pkg != null) {
                        apps += ListedApp(
                            packageName = pkg,
                            label = current["label"].orEmpty().ifBlank { pkg },
                            component = current["component"].orEmpty(),
                            tvApp = current["tv"] == "1",
                            system = current["system"] == "1",
                            iconBase64 = current["icon"]?.takeIf { it.isNotBlank() },
                        )
                    }
                    current = HashMap()
                }
            }
        }
        return apps
    }

    /** true si la sortie indique que l'instrumentation s'est déroulée jusqu'au bout. */
    fun completed(output: String): Boolean = "INSTRUMENTATION_CODE: -1" in output
}
