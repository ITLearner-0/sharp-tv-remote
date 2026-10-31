package io.github.itlearner0.tvremote.quick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import io.github.itlearner0.tvremote.R
import io.github.itlearner0.tvremote.protocol.Keys
import io.github.itlearner0.tvremote.ui.MainActivity

/**
 * Télécommande dans la notification (visible aussi sur l'écran verrouillé) :
 * marche, volume, muet, lecture / pause, sans ouvrir l'application.
 */
object RemoteNotification {
    private const val CHANNEL = "telecommande"
    private const val ID = 1
    private const val PREF = "notification_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("tv", Context.MODE_PRIVATE).getBoolean(PREF, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("tv", Context.MODE_PRIVATE).edit().putBoolean(PREF, enabled).apply()
        if (enabled) show(context) else hide(context)
    }

    /** Affiche (ou met à jour) la notification si l'utilisateur l'a activée. */
    fun refresh(context: Context) {
        if (isEnabled(context)) show(context)
    }

    fun show(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Télécommande", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Boutons de télécommande dans les notifications et sur l'écran verrouillé"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val tv = QuickRemote.currentTv(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(icon: Int, title: String, intent: PendingIntent) =
            Notification.Action.Builder(Icon.createWithResource(context, icon), title, intent).build()

        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle(tv?.name ?: "OmniZap")
            .setContentText("Télécommande")
            .setContentIntent(open)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(action(R.drawable.ic_q_power, "Marche", QuickActionReceiver.powerIntent(context)))
            .addAction(action(R.drawable.ic_q_vol_down, "Volume −", QuickActionReceiver.keyIntent(context, Keys.VOLUME_DOWN)))
            .addAction(action(R.drawable.ic_q_vol_up, "Volume +", QuickActionReceiver.keyIntent(context, Keys.VOLUME_UP)))
            .addAction(action(R.drawable.ic_q_mute, "Muet", QuickActionReceiver.keyIntent(context, Keys.VOLUME_MUTE)))
            .addAction(action(R.drawable.ic_q_play_pause, "Lecture / pause", QuickActionReceiver.keyIntent(context, Keys.MEDIA_PLAY_PAUSE)))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()
        runCatching { manager.notify(ID, notification) }
    }

    /** false si l'utilisateur a bloqué les notifications de l'application ou de ce canal. */
    fun canShow(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}

/** Remet la notification après un redémarrage du téléphone. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) RemoteNotification.refresh(context)
    }
}
