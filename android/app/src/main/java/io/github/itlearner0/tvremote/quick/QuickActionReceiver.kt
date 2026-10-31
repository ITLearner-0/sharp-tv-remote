package io.github.itlearner0.tvremote.quick

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reçoit les appuis des boutons du widget et de la notification. */
class QuickActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val key = intent.getIntExtra(EXTRA_KEY, -1)
        QuickRemote.run(context, done = { pending.finish() }) { ctx ->
            if (intent.action == ACTION_POWER) QuickRemote.power(ctx) else if (key >= 0) QuickRemote.key(ctx, key)
        }
    }

    companion object {
        const val ACTION_KEY = "io.github.itlearner0.tvremote.KEY"
        const val ACTION_POWER = "io.github.itlearner0.tvremote.POWER"
        private const val EXTRA_KEY = "key"

        fun keyIntent(context: Context, keyCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            keyCode,
            Intent(context, QuickActionReceiver::class.java).setAction(ACTION_KEY).putExtra(EXTRA_KEY, keyCode),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun powerIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0x7FFF,
            Intent(context, QuickActionReceiver::class.java).setAction(ACTION_POWER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
