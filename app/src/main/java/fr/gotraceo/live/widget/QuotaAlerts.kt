package fr.gotraceo.live.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.PrimClient
import fr.gotraceo.live.data.PrimDay
import fr.gotraceo.live.ui.KeyActivity
import java.text.NumberFormat
import java.util.Locale

/**
 * Compte les requêtes de la clé PRIM et prévient, une fois par jour, à 80 % du quota puis au
 * passage sur Tracéo.
 */
object QuotaAlerts {

    private const val CHANNEL_ID = "prim_quota"
    private const val NOTIFICATION_ID = 0x5157

    fun listener(context: Context): PrimClient.UsageListener {
        val app = context.applicationContext
        val prefs = Prefs(app)
        return object : PrimClient.UsageListener {
            override fun onResponse(remainingDay: Int?, limitDay: Int?) {
                val today = PrimDay.today()
                val usage = prefs.updatePrimUsage { it.recorded(today, remainingDay, limitDay) }
                when {
                    usage.isExhausted(today) -> fallbackOnce(app, prefs, today)
                    usage.needsWarning(today) -> {
                        prefs.updatePrimUsage { it.copy(warnedDay = today) }
                        notify(app, app.getString(R.string.quota_warning_title), app.getString(
                            R.string.quota_warning_body, number(usage.used), number(usage.quota),
                        ))
                    }
                }
            }

            override fun onQuotaExceeded() {
                val today = PrimDay.today()
                prefs.updatePrimUsage { it.exceeded(today) }
                fallbackOnce(app, prefs, today)
            }
        }
    }

    private fun fallbackOnce(context: Context, prefs: Prefs, today: String) {
        var first = false
        prefs.updatePrimUsage { usage ->
            first = usage.fallbackNoticeDay != today
            usage.copy(fallbackNoticeDay = today)
        }
        if (first) {
            notify(context, context.getString(R.string.quota_fallback_title), context.getString(R.string.quota_fallback_body))
        }
    }

    fun number(value: Int): String = NumberFormat.getIntegerInstance(Locale.FRANCE).format(value)

    private fun notify(context: Context, title: String, body: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.quota_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.quota_channel_description)
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, Intent(context, KeyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_departures)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Autorisation des notifications retirée entre-temps : l'écran de la clé reste à jour.
        }
    }
}
