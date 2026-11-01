package io.github.itlearner0.tvremote.cast

import io.github.itlearner0.tvremote.i18n.tr

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * Duplication de l'écran : réservée au système Android. On ouvre son menu de
 * diffusion (« Caster », « Smart View » sur Samsung…).
 */
object ScreenMirroring {
    private const val GOOGLE_HOME = "com.google.android.apps.chromecast.app"

    /**
     * Les TV Android à Chromecast intégré ne gèrent souvent pas Miracast (utilisé par
     * « Smart View » de Samsung) : la duplication passe par Google Home → « Caster mon écran ».
     */
    fun open(context: Context) {
        context.packageManager.getLaunchIntentForPackage(GOOGLE_HOME)?.let { home ->
            context.startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(
                context,
                tr("Dans Google Home, touchez la TV puis « Caster mon écran »"),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        openSystemMenu(context)
    }

    /** Propose d'installer Google Home (nécessaire pour dupliquer l'écran vers Chromecast). */
    fun installGoogleHome(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$GOOGLE_HOME"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun isGoogleHomeInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(GOOGLE_HOME) != null

    /** Menu de diffusion du système (« Caster », « Smart View » / Miracast). */
    fun openSystemMenu(context: Context) {
        val intents = listOf(
            Intent("android.settings.CAST_SETTINGS"),
            Intent().setClassName("com.samsung.android.smartmirroring", "com.samsung.android.smartmirroring.CaptureActivity"),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Toast.makeText(
            context,
            tr("Déroulez les réglages rapides et touchez « Caster » ou « Smart View », puis choisissez la TV"),
            Toast.LENGTH_LONG,
        ).show()
    }
}
