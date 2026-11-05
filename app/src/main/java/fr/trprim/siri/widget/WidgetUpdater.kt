package fr.trprim.siri.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import fr.trprim.siri.data.DataSources
import fr.trprim.siri.data.PassageRoute
import fr.trprim.siri.data.PassageSnapshot
import fr.trprim.siri.data.Prefs
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.data.WidgetStates
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    const val ACTION_REFRESH = "fr.trprim.siri.action.REFRESH"
    const val ACTION_TICK = "fr.trprim.siri.action.TICK"

    private const val TAG = "TRWidget"
    private const val TICK_INTERVAL_MS = 60_000L
    private val RENDER_QUALITIES = listOf(1f, 0.7f, 0.5f)

    private val mutex = Mutex()

    fun widgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetProviders.components(context).flatMap { manager.getAppWidgetIds(it).asList() }.toIntArray()
    }

    /** Redessine un widget à partir de l'état en cache (sans réseau). */
    fun render(context: Context, widgetId: Int, refreshing: Boolean = false) {
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
            val refreshMinutes = DataSources.refreshMinutes(prefs.refreshMinutes, hasKey)
            // Écran éteint (ex. rafraîchissement périodique déclenché par Android) : aucune requête,
            // sauf action explicite de l'utilisateur.
            val screenOn = isInteractive(context)
            val targets = ids.filter { id ->
                val config = prefs.config(id)
                // Un widget à reconfigurer ne fait aucun appel, même à la demande : Tracéo répondrait 404.
                config != null && DataSources.route(config.stop.stopIds, hasKey) != PassageRoute.RECONFIGURE &&
                    (force || (screenOn && needsRefresh(prefs.state(id), refreshMinutes, now)))
            }
            ids.filter { it !in targets }.forEach { render(context, it) }
            if (targets.isEmpty()) return

            targets.forEach { render(context, it, refreshing = true) }
            val source = DataSources.passageSource(prefs.apiKey, prefs.primFormats)
            coroutineScope {
                // Requêtes partagées entre widgets portant sur le même point d'arrêt et la même ligne.
                val requests = HashMap<Pair<String, String>, Deferred<Result<PassageSnapshot>>>()
                fun fetch(stopId: String, lineId: String): Deferred<Result<PassageSnapshot>> =
                    requests.getOrPut(stopId to lineId) {
                        async(Dispatchers.IO) { runCatching { source.passages(stopId, lineId) } }
                    }

                val perWidget = targets.mapNotNull { id ->
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

    /** Modifie la configuration d'un widget sans croiser une actualisation en cours. */
    suspend fun <T> exclusive(block: () -> T): T = mutex.withLock { block() }

    private fun isInteractive(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun needsRefresh(state: WidgetState?, refreshMinutes: Int, now: Long): Boolean {
        if (state == null) return true
        if (refreshMinutes <= 0) return false
        // Marge de 15 s car les alarmes non exactes peuvent arriver un peu tôt ou tard.
        return now - state.attemptedAt >= refreshMinutes * 60_000L - 15_000L
    }

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
        val trigger = nextTickAt(System.currentTimeMillis())
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

    /**
     * Début de la minute suivante. Reprogrammer l'alarme pendant la même minute (ajout ou réglage
     * d'un widget) garde donc la même échéance au lieu de la repousser.
     */
    fun nextTickAt(now: Long): Long = (Math.floorDiv(now, TICK_INTERVAL_MS) + 1) * TICK_INTERVAL_MS

    private fun canUseExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Demande une actualisation immédiate (depuis l'application). */
    fun requestRefresh(context: Context, widgetId: Int? = null) {
        val intent = Intent(context, DeparturesWidgetProvider::class.java).setAction(ACTION_REFRESH)
        if (widgetId != null) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        context.sendBroadcast(intent)
    }
}
