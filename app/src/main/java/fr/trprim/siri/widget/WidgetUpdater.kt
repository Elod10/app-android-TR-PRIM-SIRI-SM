package fr.trprim.siri.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import fr.trprim.siri.data.Departure
import fr.trprim.siri.data.Directions
import fr.trprim.siri.data.PrimClient
import fr.trprim.siri.data.PrimException
import fr.trprim.siri.data.Prefs
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetState
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
 * des widgets : il recalcule les temps d'attente affichés et relance une requête PRIM quand
 * l'intervalle d'actualisation choisi est écoulé. Comme l'alarme ne réveille pas l'appareil,
 * aucune requête n'est faite écran éteint.
 */
object WidgetUpdater {

    const val ACTION_REFRESH = "fr.trprim.siri.action.REFRESH"
    const val ACTION_TICK = "fr.trprim.siri.action.TICK"

    private const val TAG = "TRWidget"
    private const val TICK_INTERVAL_MS = 60_000L
    private const val MAX_STORED_DEPARTURES = 12
    private val RENDER_QUALITIES = listOf(1f, 0.7f, 0.5f)

    private val mutex = Mutex()

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(WidgetRenderer.providerComponent(context))

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
                    renderScale = quality,
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
     * @param force true pour interroger PRIM même si l'intervalle n'est pas écoulé (action utilisateur)
     */
    suspend fun refresh(context: Context, ids: IntArray, force: Boolean) {
        mutex.withLock {
            val prefs = Prefs(context)
            val now = System.currentTimeMillis()
            // Écran éteint (ex. rafraîchissement périodique déclenché par Android) : aucune requête,
            // sauf action explicite de l'utilisateur.
            val screenOn = isInteractive(context)
            val targets = ids.filter { id ->
                prefs.config(id) != null &&
                    (force || (screenOn && needsRefresh(prefs.state(id), prefs.refreshMinutes, now)))
            }
            ids.filter { it !in targets }.forEach { render(context, it) }
            if (targets.isEmpty()) return
            if (!prefs.hasApiKey) {
                targets.forEach { render(context, it) }
                return
            }

            targets.forEach { render(context, it, refreshing = true) }
            val client = PrimClient(prefs.apiKey, formats = prefs.primFormats)
            coroutineScope {
                // Requêtes partagées entre widgets portant sur le même point d'arrêt et la même ligne.
                val requests = HashMap<Pair<String, String>, Deferred<Result<List<Departure>>>>()
                fun fetch(stopId: String, lineId: String): Deferred<Result<List<Departure>>> =
                    requests.getOrPut(stopId to lineId) {
                        async(Dispatchers.IO) { runCatching { client.departures(stopId, lineId) } }
                    }

                val perWidget = targets.mapNotNull { id ->
                    val config = prefs.config(id) ?: return@mapNotNull null
                    val stopIds = config.directions.keys.ifEmpty { config.stop.stopIds.toSet() }
                    Triple(id, config, stopIds.map { fetch(it, config.line.id) })
                }
                perWidget.forEach { (id, config, deferred) ->
                    val results = deferred.awaitAll()
                    val state = buildState(config, results, prefs.state(id), System.currentTimeMillis())
                    prefs.saveState(id, state)
                    render(context, id)
                }
            }
        }
    }

    private fun buildState(
        config: WidgetConfig,
        results: List<Result<List<Departure>>>,
        previous: WidgetState?,
        now: Long,
    ): WidgetState {
        val successes = results.filter { it.isSuccess }.flatMap { it.getOrDefault(emptyList()) }
        val errors = results.mapNotNull { it.exceptionOrNull() }
        errors.forEach { Log.w(TAG, "Erreur PRIM : ${it.message}") }

        if (results.isNotEmpty() && errors.size == results.size) {
            return WidgetState(
                fetchedAt = previous?.fetchedAt ?: 0L,
                attemptedAt = now,
                departures = previous?.departures.orEmpty(),
                error = errorMessage(errors.first()),
            )
        }
        val filtered = Directions.upcoming(Directions.filter(successes, config.directions), now)
        return WidgetState(
            fetchedAt = now,
            attemptedAt = now,
            departures = filtered.take(MAX_STORED_DEPARTURES),
            error = if (errors.isNotEmpty() && filtered.isEmpty()) errorMessage(errors.first()) else null,
        )
    }

    private fun errorMessage(t: Throwable): String = when (t) {
        is PrimException -> t.message ?: "Erreur PRIM"
        else -> "Erreur : ${t.message ?: t.javaClass.simpleName}"
    }

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
        // RTC (non réveillant) : rien n'est exécuté tant que l'écran est éteint.
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + TICK_INTERVAL_MS, pending)
    }

    fun cancelTick(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    /** Demande une actualisation immédiate (depuis l'application). */
    fun requestRefresh(context: Context, widgetId: Int? = null) {
        val intent = Intent(context, DeparturesWidgetProvider::class.java).setAction(ACTION_REFRESH)
        if (widgetId != null) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        context.sendBroadcast(intent)
    }
}
