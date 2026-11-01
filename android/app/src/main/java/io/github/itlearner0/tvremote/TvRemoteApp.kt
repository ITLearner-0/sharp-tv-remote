package io.github.itlearner0.tvremote

import io.github.itlearner0.tvremote.i18n.tr

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
        // Langue choisie dans OmniZap (avant Android 13 ; ensuite, Android l'applique lui-même).
        runCatching { io.github.itlearner0.tvremote.i18n.AppLanguage.apply(this) }
        // OmniZap Pro : essai gratuit et achat Google Play.
        runCatching { io.github.itlearner0.tvremote.pro.Pro.init(this) }
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
        return tr("OmniZap {1} — {2}\n", version, date) +
            tr("Téléphone : {1} {2}, Android {3} (API {4})\n", Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT) +
            tr("Fil : {1}\n\n", thread.name) + trace.take(12_000)
    }

    companion object {
        fun crashFile(app: Application): File = File(app.filesDir, "dernier-plantage.txt")
    }
}
