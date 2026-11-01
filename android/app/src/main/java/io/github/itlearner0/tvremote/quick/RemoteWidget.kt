package io.github.itlearner0.tvremote.quick

import io.github.itlearner0.tvremote.i18n.tr

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.itlearner0.tvremote.R
import io.github.itlearner0.tvremote.protocol.Keys
import io.github.itlearner0.tvremote.ui.MainActivity

/** Widget d'écran d'accueil : marche, volume, muet, lecture / pause. */
class RemoteWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    companion object {
        fun views(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_remote).apply {
            setTextViewText(R.id.widget_title, QuickRemote.currentTv(context)?.name ?: tr("OmniZap"))
            setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            setOnClickPendingIntent(R.id.widget_power, QuickActionReceiver.powerIntent(context))
            setOnClickPendingIntent(R.id.widget_vol_down, QuickActionReceiver.keyIntent(context, Keys.VOLUME_DOWN))
            setOnClickPendingIntent(R.id.widget_vol_up, QuickActionReceiver.keyIntent(context, Keys.VOLUME_UP))
            setOnClickPendingIntent(R.id.widget_mute, QuickActionReceiver.keyIntent(context, Keys.VOLUME_MUTE))
            setOnClickPendingIntent(R.id.widget_play, QuickActionReceiver.keyIntent(context, Keys.MEDIA_PLAY_PAUSE))
        }

        /** Met à jour le nom de la TV affiché sur les widgets (après un changement de TV). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, RemoteWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }
    }
}
