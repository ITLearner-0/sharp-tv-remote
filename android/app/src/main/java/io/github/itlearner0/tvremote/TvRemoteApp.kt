package io.github.itlearner0.tvremote

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enregistre le détail d'un éventuel plantage : il est affiché au lancement
 * suivant pour que l'utilisateur puisse le transmettre (aucun envoi automatique).
 */
class TvRemoteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { crashFile(this).writeText(describe(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun describe(thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        return "Télécommande TV $version — $date\n" +
            "Téléphone : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Fil : ${thread.name}\n\n" + trace.take(12_000)
    }

    companion object {
        fun crashFile(app: Application): File = File(app.filesDir, "dernier-plantage.txt")
    }
}
