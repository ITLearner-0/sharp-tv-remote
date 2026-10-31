package io.github.itlearner0.tvremote.cast

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
    fun open(context: Context) {
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
            "Déroulez les réglages rapides et touchez « Caster » ou « Smart View », puis choisissez la TV",
            Toast.LENGTH_LONG,
        ).show()
    }
}
