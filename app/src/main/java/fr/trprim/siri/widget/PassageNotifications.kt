package fr.trprim.siri.widget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Log
import fr.trprim.siri.R
import fr.trprim.siri.data.Line
import fr.trprim.siri.data.PositionReader
import fr.trprim.siri.data.Prefs
import fr.trprim.siri.ui.MainActivity
import fr.trprim.siri.ui.UiText
import java.time.ZonedDateTime
import kotlin.math.max

/**
 * Notification d'un widget : les mêmes passages, lisibles sur l'écran de verrouillage de tous les
 * téléphones, même ceux qui n'y acceptent pas de widget. Elle suit le widget : elle est mise à jour
 * chaque fois qu'il est redessiné, donc sans requête de plus, et seulement pendant sa plage horaire.
 */
object PassageNotifications {

    const val ACTION_DISMISSED = "fr.trprim.siri.action.NOTIFICATION_DISMISSED"

    private const val CHANNEL_ID = "passages"

    /** Les notifications des widgets ont une étiquette : celle du service de position n'en a pas. */
    private const val NOTIFICATION_TAG = "widget"
    private const val TAG = "TRWidget"
    private const val ICON_DP = 40f

    /** Vrai si Android laisse l'application afficher cette notification. */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return granted && manager.areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    /** Active ou retire la notification d'un widget. */
    fun setEnabled(context: Context, widgetId: Int, enabled: Boolean) {
        Prefs(context).setNotifies(widgetId, enabled)
        if (enabled) update(context, widgetId) else cancel(context, widgetId)
    }

    /** Affiche ou met à jour la notification d'un widget, si elle est activée. */
    fun update(context: Context, widgetId: Int) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (!prefs.notifies(widgetId) || !canPost(app)) return
        try {
            val manager = app.getSystemService(NotificationManager::class.java) ?: return
            // Hors de la plage horaire, ou balayée pendant celle-ci : la notification attend la suivante.
            val waiting = !prefs.inTimeWindow(widgetId) || System.currentTimeMillis() < prefs.notifyHiddenUntil(widgetId)
            val notification = if (waiting) null else build(app, prefs, widgetId)
            if (notification == null) {
                manager.cancel(NOTIFICATION_TAG, widgetId)
            } else {
                manager.notify(NOTIFICATION_TAG, widgetId, notification)
            }
        } catch (e: RuntimeException) {
            // Une notification refusée ne doit pas empêcher le widget de s'afficher.
            Log.w(TAG, "Échec de la notification du widget $widgetId", e)
        }
    }

    fun cancel(context: Context, widgetId: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_TAG, widgetId)
    }

    /**
     * Notification balayée. Avec une plage horaire, elle revient à la plage suivante ; sans plage,
     * l'option est désactivée, comme le recommande Android pour une notification qu'on écarte.
     */
    fun onDismissed(context: Context, widgetId: Int) {
        val prefs = Prefs(context)
        val end = prefs.timeWindow(widgetId)?.currentEnd(ZonedDateTime.now())
        if (end != null) {
            prefs.hideNotifyUntil(widgetId, end.toInstant().toEpochMilli())
        } else {
            prefs.setNotifies(widgetId, false)
        }
    }

    private fun build(context: Context, prefs: Prefs, widgetId: Int): Notification? {
        val manager = AppWidgetManager.getInstance(context)
        val format = WidgetProviders.formatOf(manager.getAppWidgetInfo(widgetId)?.provider?.className)
        val proximity = format == WidgetFormat.PROXIMITE
        val config = prefs.config(widgetId) ?: return null
        val now = System.currentTimeMillis()
        // La taille du widget fixe le nombre de lignes connues du format À proximité.
        val (width, height) = WidgetRenderer.widgetSize(context, manager.getAppWidgetOptions(widgetId))
        val model = if (proximity) {
            WidgetModels.buildProximity(config, prefs.proximity(widgetId), false, width, height, now, night = true)
        } else {
            WidgetModels.build(config, prefs.state(widgetId), prefs.hasApiKey, false, width, height, now, format)
        }
        val lineTitle = if (proximity) null else UiText.lineTitle(config.line)
        val content = NotificationContent.build(model, lineTitle) { noticeText(context, it) } ?: return null

        createChannel(context)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_departures)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(content.subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent(context, widgetId))
            .setDeleteIntent(dismissIntent(context, widgetId))
            .addAction(
                action(context, R.drawable.ic_refresh, R.string.notification_refresh, WidgetRenderer.refreshPendingIntent(context, widgetId)),
            )
        if (content.lines.size > 1) {
            builder.setStyle(Notification.InboxStyle().also { style -> content.lines.forEach { style.addLine(it) } })
        } else {
            builder.setStyle(Notification.BigTextStyle().bigText(content.lines.first()))
        }
        if (!proximity) {
            builder.setLargeIcon(badgeIcon(context, config.line))
            builder.setColor(Palettes.lineColors(config.line).first)
        }
        // Toucher le bouton donne accès à la position, comme toucher le widget : rien n'est lu sans lui.
        if ((config.auto || proximity) && PositionReader(context).hasPreciseAccess) {
            builder.addAction(action(context, R.drawable.ic_stat_location, R.string.notification_locate, locateIntent(context, widgetId)))
        }
        return builder.build()
    }

    private fun noticeText(context: Context, notice: Notice): String? = when (notice.kind) {
        NoticeKind.NOT_CONFIGURED -> null
        NoticeKind.RECONFIGURE -> context.getString(R.string.widget_reconfigure_title)
        NoticeKind.LOADING -> context.getString(R.string.widget_loading)
        NoticeKind.NO_DEPARTURE -> context.getString(R.string.widget_no_departure)
        NoticeKind.ERROR -> notice.detail ?: context.getString(R.string.notification_error)
    }

    /** Sans son ni vibration : la notification change chaque minute et ne doit jamais déranger. */
    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            // Une notification « silencieuse » peut être masquée sur l'écran de verrouillage.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun action(context: Context, icon: Int, label: Int, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, icon), context.getString(label), intent).build()

    /** La pastille de la ligne, centrée dans un carré pour ne pas être déformée. */
    private fun badgeIcon(context: Context, line: Line): Icon {
        val badge = BadgeRenderer.render(context, line, ICON_DP)
        val side = max(badge.width, badge.height)
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).drawBitmap(badge, (side - badge.width) / 2f, (side - badge.height) / 2f, null)
        return Icon.createWithBitmap(square)
    }

    private fun openIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("trprim://notification/open/$widgetId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, DeparturesWidgetProvider::class.java)
            .setAction(ACTION_DISMISSED)
            .setData(Uri.parse("trprim://notification/dismissed/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun locateIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, AutoLocateService::class.java)
            .setData(Uri.parse("trprim://notification/locate/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AutoLocateService.EXTRA_FROM_NOTIFICATION, true)
        return PendingIntent.getForegroundService(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
