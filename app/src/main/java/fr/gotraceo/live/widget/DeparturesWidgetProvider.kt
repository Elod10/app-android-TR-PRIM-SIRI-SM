package fr.gotraceo.live.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import fr.gotraceo.live.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Widget « Prochains passages » : entrée automatique, et base des formats de [WidgetProviders]. */
open class DeparturesWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetUpdater.ACTION_REFRESH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val ids = if (id != AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf(id) else WidgetUpdater.widgetIds(context)
                runAsync(context) {
                    WidgetUpdater.refresh(context, ids, force = true)
                }
            }
            WidgetUpdater.ACTION_TICK -> runAsync(context) {
                val ids = WidgetUpdater.widgetIds(context)
                // Les widgets qui suivent la position la relisent d'abord, puis actualisent leurs passages.
                val followed = AutoMode.followedNow(context, ids, System.currentTimeMillis())
                followed.forEach { AutoMode.follow(context, it) }
                WidgetUpdater.refresh(context, ids.filter { it !in followed }.toIntArray(), force = false)
            }
            // Notification balayée : elle ne revient pas à l'actualisation suivante.
            PassageNotifications.ACTION_DISMISSED -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) PassageNotifications.onDismissed(context, id)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.render(context, it) }
        runAsync(context) {
            WidgetUpdater.refresh(context, appWidgetIds, force = false)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        WidgetUpdater.render(context, appWidgetId)
        // Un widget À proximité agrandi affiche plus de lignes : il faut leurs passages.
        if (AutoMode.wantsMoreLines(context, appWidgetId)) {
            runAsync(context) { WidgetUpdater.refresh(context, intArrayOf(appWidgetId), force = true) }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = Prefs(context)
        appWidgetIds.forEach {
            prefs.deleteWidget(it)
            PassageNotifications.cancel(context, it)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        // Appelé pour chaque format retiré : d'autres widgets peuvent encore avoir besoin du tick.
        WidgetUpdater.scheduleTick(context)
    }

    /**
     * Exécute un traitement asynchrone dans le cadre du broadcast (goAsync), en libérant le
     * broadcast au plus tard après [TIMEOUT_MS] même si une requête réseau traîne.
     */
    private fun runAsync(context: Context, block: suspend () -> Unit) {
        val appContext = context.applicationContext
        val pending: BroadcastReceiver.PendingResult = goAsync()
        val work = scope.async {
            try {
                block()
            } catch (t: Throwable) {
                Log.w("TRWidget", "Échec de l'actualisation", t)
            }
        }
        scope.launch {
            try {
                withTimeoutOrNull(TIMEOUT_MS) { work.await() }
            } finally {
                try {
                    WidgetUpdater.scheduleTick(appContext)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 40_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
