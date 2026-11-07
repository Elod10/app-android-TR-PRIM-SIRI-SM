package fr.gotraceo.live.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.PassageRoute
import fr.gotraceo.live.data.PassageSnapshot
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.RefreshSchedule
import fr.gotraceo.live.data.WidgetStates
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Récupération des passages et mise à jour des widgets.
 *
 * Un « tick » non réveillant (AlarmManager.RTC) est programmé chaque minute tant qu'il existe
 * des widgets : il recalcule les temps d'attente affichés et relance une requête (Tracéo sans clé,
 * PRIM avec une clé) quand l'intervalle d'actualisation choisi est écoulé. Comme l'alarme ne
 * réveille pas l'appareil, aucune requête n'est faite écran éteint.
 *
 * L'alarme est exacte : depuis Android 12, une alarme simple peut arriver jusqu'à une heure après
 * l'heure demandée, ce qui figerait les temps d'attente affichés.
 */
object WidgetUpdater {

    const val ACTION_REFRESH = "fr.gotraceo.live.action.REFRESH"
    const val ACTION_TICK = "fr.gotraceo.live.action.TICK"

    private const val TAG = "TRWidget"
    private const val MAX_PARALLEL_REQUESTS = 4
    private val RENDER_QUALITIES = listOf(1f, 0.7f, 0.5f)

    private val mutex = Mutex()

    fun widgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetProviders.components(context).flatMap { manager.getAppWidgetIds(it).asList() }.toIntArray()
    }

    /** Redessine un widget, sa notification et la tuile, à partir de l'état en cache (sans réseau). */
    fun render(context: Context, widgetId: Int, refreshing: Boolean = false) {
        draw(context, widgetId, refreshing)
        // Pendant une actualisation, la notification et la tuile gardent les passages précédents.
        if (!refreshing) {
            PassageNotifications.update(context, widgetId)
            QuickTile.update(context, widgetId)
        }
    }

    private fun draw(context: Context, widgetId: Int, refreshing: Boolean) {
        val prefs = Prefs(context)
        val manager = AppWidgetManager.getInstance(context)
        for (quality in RENDER_QUALITIES) {
            try {
                val views = WidgetRenderer.build(
                    context = context,
                    widgetId = widgetId,
                    config = prefs.config(widgetId),
                    state = prefs.state(widgetId),
                    hasApiKey = prefs.hasApiKey,
                    refreshing = refreshing,
                    options = manager.getAppWidgetOptions(widgetId),
                    fixedFormat = WidgetProviders.formatOf(manager.getAppWidgetInfo(widgetId)?.provider?.className),
                    renderScale = quality,
                    auto = prefs.auto(widgetId),
                    proximity = prefs.proximity(widgetId),
                )
                manager.updateAppWidget(widgetId, views)
                return
            } catch (e: RuntimeException) {
                // Le widget est une image : si elle est trop lourde pour le lanceur, on l'allège.
                Log.w(TAG, "Échec de l'affichage du widget $widgetId (définition $quality)", e)
            }
        }
    }

    fun renderAll(context: Context) {
        widgetIds(context).forEach { render(context, it) }
    }

    /**
     * Actualise les widgets indiqués.
     * @param force true pour interroger la source même si l'intervalle n'est pas écoulé (action utilisateur)
     */
    suspend fun refresh(context: Context, ids: IntArray, force: Boolean) {
        mutex.withLock {
            val prefs = Prefs(context)
            val now = System.currentTimeMillis()
            val hasKey = prefs.hasApiKey
            val timing = Timing.of(prefs)
            // Écran éteint (ex. rafraîchissement périodique déclenché par Android) : aucune requête,
            // sauf action explicite de l'utilisateur.
            val screenOn = isInteractive(context)
            val nearby = ids.filter { WidgetProviders.isProximity(context, it) }
            val due = ids.associateWith { timing.dueAt(context, prefs, it) }
            val dueNow = ids.any { id -> due[id]?.let { it <= now } == true }
            // En direct, les widgets presque dus partent avec les autres : ils restent ensuite alignés.
            // Avec Tracéo, inutile : tous attendent le même relevé, annoncé par `nextAt`.
            val alignUntil = if (dueNow && !timing.followsTraceo) now + timing.refreshMinutes * 60_000L / 2 else now
            val targets = ids.filter { id ->
                val config = prefs.config(id)
                // Un widget à reconfigurer ne fait aucun appel, même à la demande : Tracéo répondrait 404.
                config != null && DataSources.route(config.stop.stopIds, hasKey) != PassageRoute.RECONFIGURE &&
                    (force || (screenOn && due[id]?.let { it <= alignUntil } == true))
            }
            ids.filter { it !in targets }.forEach { render(context, it) }
            if (targets.isEmpty()) return
            Log.i(TAG, "Actualisation de ${targets.size} widget(s) via ${if (timing.followsTraceo) "Tracéo" else "PRIM"}")

            targets.forEach { render(context, it, refreshing = true) }
            targets.filter { it in nearby }.forEach { id ->
                AutoMode.refreshProximity(context, id)
                render(context, id)
            }
            val lineTargets = targets.filter { it !in nearby }
            if (lineTargets.isEmpty()) return
            val source = Sources.passages(context)
            coroutineScope {
                // Requêtes partagées entre widgets portant sur le même point d'arrêt et la même ligne,
                // et jamais plus de quelques-unes à la fois : pas de rafale vers la source.
                val requests = HashMap<Pair<String, String>, Deferred<Result<PassageSnapshot>>>()
                val parallel = Semaphore(MAX_PARALLEL_REQUESTS)
                fun fetch(stopId: String, lineId: String): Deferred<Result<PassageSnapshot>> =
                    requests.getOrPut(stopId to lineId) {
                        async(Dispatchers.IO) { parallel.withPermit { runCatching { source.passages(stopId, lineId) } } }
                    }

                val perWidget = lineTargets.mapNotNull { id ->
                    val config = prefs.config(id) ?: return@mapNotNull null
                    val stopIds = config.directions.keys.ifEmpty { config.stop.stopIds.toSet() }
                    Triple(id, config, stopIds.map { fetch(it, config.line.id) })
                }
                perWidget.forEach { (id, config, deferred) ->
                    val results = deferred.awaitAll()
                    results.mapNotNull { it.exceptionOrNull() }.forEach { Log.w(TAG, "Erreur de la source : ${it.message}") }
                    val state = WidgetStates.from(
                        config, results, prefs.state(id), System.currentTimeMillis(),
                        removeTerminusArrivals = !hasKey,
                    )
                    prefs.saveState(id, state)
                    render(context, id)
                }
            }
        }
    }

    /** Vrai si l'actualisation automatique de ce widget est due : écran allumé et échéance passée. */
    fun isDue(context: Context, widgetId: Int, now: Long): Boolean {
        if (!isInteractive(context)) return false
        val prefs = Prefs(context)
        return Timing.of(prefs).dueAt(context, prefs, widgetId)?.let { it <= now } == true
    }

    /** Réglages qui fixent les échéances : source, intervalle, relevé annoncé par Tracéo et décalage. */
    private class Timing(
        val followsTraceo: Boolean,
        val refreshMinutes: Int,
        private val traceoNextAt: Long,
        private val offsetMs: Long,
    ) {
        fun dueAt(context: Context, prefs: Prefs, widgetId: Int): Long? {
            val attemptedAt = if (WidgetProviders.isProximity(context, widgetId)) {
                prefs.proximity(widgetId)?.attemptedAt
            } else {
                prefs.state(widgetId)?.attemptedAt
            }
            return RefreshSchedule.dueAt(attemptedAt, refreshMinutes, followsTraceo, traceoNextAt, offsetMs)
        }

        companion object {
            fun of(prefs: Prefs): Timing {
                val usesPrim = prefs.usesPrim
                return Timing(
                    followsTraceo = !usesPrim,
                    refreshMinutes = DataSources.refreshMinutes(prefs.refreshMinutes, usesPrim),
                    traceoNextAt = prefs.traceoNextAt,
                    offsetMs = prefs.refreshOffsetMs,
                )
            }
        }
    }

    /** Modifie la configuration d'un widget sans croiser une actualisation en cours. */
    suspend fun <T> exclusive(block: () -> T): T = mutex.withLock { block() }

    private fun isInteractive(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true


    // --- Planification du tick -------------------------------------------------------------

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, DeparturesWidgetProvider::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun scheduleTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = tickIntent(context)
        if (widgetIds(context).isEmpty()) {
            alarmManager.cancel(pending)
            return
        }
        val now = System.currentTimeMillis()
        val prefs = Prefs(context)
        val timing = Timing.of(prefs)
        // Le tick de chaque minute, ou plus tôt si un widget est dû avant (prochain relevé de Tracéo).
        val nextDue = widgetIds(context).asList().mapNotNull { timing.dueAt(context, prefs, it) }.filter { it > now }.minOrNull()
        val trigger = minOf(RefreshSchedule.nextTickAt(now, prefs.refreshOffsetMs), nextDue ?: Long.MAX_VALUE)
        // RTC (non réveillant) : rien n'est exécuté tant que l'écran est éteint.
        try {
            if (canUseExactAlarms(alarmManager)) {
                alarmManager.setExact(AlarmManager.RTC, trigger, pending)
            } else {
                alarmManager.set(AlarmManager.RTC, trigger, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Alarme exacte refusée", e)
            alarmManager.set(AlarmManager.RTC, trigger, pending)
        }
    }


    private fun canUseExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Vrai si les temps d'attente ne peuvent pas suivre la minute : depuis Android 14, l'alarme
     * exacte doit être autorisée par l'usager (« Alarmes et rappels »).
     */
    fun needsExactAlarmAccess(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return !canUseExactAlarms(alarmManager)
    }

    /** Demande une actualisation immédiate (depuis l'application). */
    fun requestRefresh(context: Context, widgetId: Int? = null) {
        val intent = Intent(context, DeparturesWidgetProvider::class.java).setAction(ACTION_REFRESH)
        if (widgetId != null) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        context.sendBroadcast(intent)
    }
}
