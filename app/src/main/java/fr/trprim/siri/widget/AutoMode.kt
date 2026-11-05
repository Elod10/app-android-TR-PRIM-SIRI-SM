package fr.trprim.siri.widget

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.util.Log
import fr.trprim.siri.data.AnchorDecision
import fr.trprim.siri.data.AnchorPolicy
import fr.trprim.siri.data.AutoMessage
import fr.trprim.siri.data.AutoState
import fr.trprim.siri.data.DataSources
import fr.trprim.siri.data.Directions
import fr.trprim.siri.data.GeoPoint
import fr.trprim.siri.data.NearbyLine
import fr.trprim.siri.data.NearbyRanking
import fr.trprim.siri.data.NearbyRules
import fr.trprim.siri.data.PassageSource
import fr.trprim.siri.data.PositionFix
import fr.trprim.siri.data.PositionReader
import fr.trprim.siri.data.Prefs
import fr.trprim.siri.data.PrimException
import fr.trprim.siri.data.StopIndexStore
import fr.trprim.siri.data.TraceoException
import fr.trprim.siri.data.TransportMode
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.data.WidgetStates
import fr.trprim.siri.data.WidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone

/**
 * Mode automatique : un widget ordinaire dont la ligne et l'arrêt sont réécrits quand on le touche.
 * Le dessin et l'actualisation restent ceux des autres widgets, ce qui garde tous les formats compatibles.
 */
object AutoMode {

    /** Après une mise à jour, un second toucher ouvre le choix pendant cette durée. */
    const val CHOICE_WINDOW_MS = 30_000L

    private const val TAG = "TRWidget"
    private const val INDEX_FILE = "stop_index.bin"

    fun store(context: Context): StopIndexStore =
        StopIndexStore(File(context.applicationContext.noBackupFilesDir, INDEX_FILE))

    /** Classement autour d'un point, sans réseau. Null si l'index n'existe pas encore. */
    fun ranking(context: Context, point: GeoPoint): NearbyRanking? {
        val prefs = Prefs(context)
        val area = store(context).around(point.lat, point.lon, NearbyRules.READ_RADIUS_M) ?: return null
        val paris = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
        return NearbyRules.rank(
            candidates = NearbyRules.linesAround(area, point),
            favorites = prefs.favoriteLines,
            pickedLineId = prefs.placeChoices.lineAt(point),
            nightService = NearbyRules.isNightService(paris.get(Calendar.HOUR_OF_DAY), paris.get(Calendar.MINUTE)),
            modes = DataSources.modes(prefs.hasApiKey).flatMap { it.apiModes }.toSet(),
        )
    }

    fun configFor(widgetId: Int, nearby: NearbyLine, theme: WidgetTheme): WidgetConfig {
        val stop = nearby.toStop()
        return WidgetConfig(
            widgetId = widgetId,
            mode = TransportMode.entries.firstOrNull { it.includes(nearby.line) } ?: TransportMode.BUS,
            line = nearby.line,
            stop = stop,
            directions = Directions.allDirections(stop),
            directionLabel = Directions.ALL_DIRECTIONS,
            theme = theme,
            auto = true,
        )
    }

    /** Enregistre le choix fait pendant la configuration du widget. */
    fun saveConfiguration(
        context: Context,
        widgetId: Int,
        nearby: NearbyLine,
        fix: PositionFix,
        theme: WidgetTheme,
        changedByUser: Boolean,
    ) {
        val prefs = Prefs(context)
        if (changedByUser) {
            prefs.placeChoices = prefs.placeChoices.remember(fix.point, nearby.line.id, System.currentTimeMillis())
        }
        prefs.saveConfig(configFor(widgetId, nearby, theme))
        prefs.saveAuto(widgetId, AutoState(anchor = fix.point, last = fix.point, distanceM = nearby.distanceM))
    }

    /** Toucher du widget, hors fenêtre de choix : relit la position et met à jour le widget. */
    suspend fun onTap(context: Context, widgetId: Int) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val config = prefs.config(widgetId) ?: return
        if (!config.auto) {
            WidgetUpdater.refresh(app, intArrayOf(widgetId), force = true)
            return
        }
        WidgetUpdater.render(app, widgetId, refreshing = true)

        val started = SystemClock.elapsedRealtime()
        val fix = PositionReader(app).read()
        val current = prefs.auto(widgetId)
        val decision = AnchorPolicy.decide(current.anchor, fix)
        Log.i(TAG, "Position en ${SystemClock.elapsedRealtime() - started} ms, précision ${fix?.accuracyM} m : $decision")

        // Une position trop imprécise ne remplace pas la dernière position fiable.
        val point = if (decision == AnchorDecision.IMPRECISE) current.last ?: fix?.point else fix?.point ?: current.last
        val ranking = withContext(Dispatchers.IO) { point?.let { rankingEnsuringIndex(app, it) } }
        var message = when (decision) {
            AnchorDecision.NO_FIX -> AutoMessage.NO_POSITION
            AnchorDecision.IMPRECISE -> AutoMessage.IMPRECISE
            else -> null
        }
        var anchor = current.anchor
        var shownLineId = config.line.id
        var applied = false
        if (decision == AnchorDecision.MOVED && point != null) {
            val shown = applyBest(app, prefs, config, ranking)
            if (shown == null) {
                message = AutoMessage.NO_STOP
            } else {
                anchor = point
                shownLineId = shown.line.id
                applied = true
            }
        }
        if (!applied) WidgetUpdater.refresh(app, intArrayOf(widgetId), force = true)

        val lines = ranking?.list.orEmpty()
        val distance = lines.firstOrNull { it.nearby.line.id == shownLineId }?.nearby?.distanceM ?: current.distanceM
        val until = System.currentTimeMillis() + CHOICE_WINDOW_MS
        prefs.saveAuto(
            widgetId,
            current.copy(
                anchor = anchor,
                last = point,
                distanceM = distance,
                choiceUntil = until,
                others = lines.count { it.nearby.line.id != shownLineId },
                message = message,
            ),
        )
        WidgetUpdater.render(app, widgetId)
        WidgetUpdater.scheduleChoiceEnd(app, widgetId, until)
    }

    private fun rankingEnsuringIndex(context: Context, point: GeoPoint): NearbyRanking? {
        val store = store(context)
        if (store.savedAt() == null) {
            try {
                store.download()
            } catch (e: IOException) {
                Log.w(TAG, "Index des arrêts indisponible", e)
                return null
            }
        }
        return ranking(context, point)
    }

    /** Affiche la meilleure ligne qui a un passage bientôt. Renvoie null si aucune ligne n'est proche. */
    private suspend fun applyBest(context: Context, prefs: Prefs, config: WidgetConfig, ranking: NearbyRanking?): NearbyLine? {
        val ranked = ranking?.list.orEmpty()
        if (ranked.isEmpty()) return null
        val hasKey = prefs.hasApiKey
        val source = DataSources.passageSource(prefs.apiKey, prefs.primFormats)
        val now = System.currentTimeMillis()
        var offline = false
        val chosen = withContext(Dispatchers.IO) {
            NearbyRules.pickActive(
                ranked,
                probe = { nearby ->
                    val next = configFor(config.widgetId, nearby, config.theme)
                    // Sans réseau, inutile d'attendre le délai d'expiration de chaque ligne.
                    val result = if (offline) {
                        Result.failure(IOException("Réseau indisponible"))
                    } else {
                        fetch(source, nearby)
                    }
                    if (isNetworkFailure(result.exceptionOrNull())) offline = true
                    next to WidgetStates.from(next, listOf(result), null, now, removeTerminusArrivals = !hasKey)
                },
                isActive = { (_, state) -> hasPassageSoon(state, now) },
            )
        } ?: return null
        val (next, state) = chosen.second
        WidgetUpdater.exclusive {
            prefs.saveConfig(next)
            prefs.saveState(next.widgetId, state)
        }
        return chosen.first.nearby
    }

    /** Choix fait dans la feuille : l'affiche et le retient pour ce lieu. */
    suspend fun choose(context: Context, widgetId: Int, nearby: NearbyLine) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val config = prefs.config(widgetId) ?: return
        val auto = prefs.auto(widgetId)
        val now = System.currentTimeMillis()
        auto.last?.let { prefs.placeChoices = prefs.placeChoices.remember(it, nearby.line.id, now) }
        val next = configFor(widgetId, nearby, config.theme)
        val source = DataSources.passageSource(prefs.apiKey, prefs.primFormats)
        val result = withContext(Dispatchers.IO) { fetch(source, nearby) }
        val state = WidgetStates.from(next, listOf(result), null, now, removeTerminusArrivals = !prefs.hasApiKey)
        WidgetUpdater.exclusive {
            prefs.saveConfig(next)
            prefs.saveState(widgetId, state)
        }
        prefs.saveAuto(
            widgetId,
            auto.copy(anchor = auto.last ?: auto.anchor, distanceM = nearby.distanceM, choiceUntil = 0L, others = 0, message = null),
        )
        WidgetUpdater.render(app, widgetId)
    }

    /** Ferme la fenêtre de choix : le prochain toucher relira la position. */
    fun closeChoice(context: Context, widgetId: Int) {
        val prefs = Prefs(context)
        prefs.saveAuto(widgetId, prefs.auto(widgetId).copy(choiceUntil = 0L, others = 0, message = null))
        WidgetUpdater.render(context, widgetId)
    }

    /** Met l'index à jour s'il est ancien : chaque semaine en Wi-Fi, chaque mois sur le réseau mobile. */
    fun refreshIndexIfStale(context: Context) {
        val app = context.applicationContext
        val store = store(app)
        val metered = app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
        if (store.savedAt() == null || !store.isStale(System.currentTimeMillis(), unmetered = !metered)) return
        try {
            val stats = store.download()
            Log.i(TAG, "Index des arrêts à jour : ${stats.stops} arrêts, ${stats.lines} lignes")
        } catch (e: IOException) {
            Log.w(TAG, "Mise à jour de l'index impossible : l'ancien reste en place", e)
        }
    }

    fun fetch(source: PassageSource, nearby: NearbyLine) =
        runCatching { source.passages(nearby.stopId, nearby.line.id) }

    private fun isNetworkFailure(error: Throwable?): Boolean = when (error) {
        null -> false
        is TraceoException -> error.kind == TraceoException.Kind.NETWORK
        is PrimException -> error.kind == PrimException.Kind.NETWORK
        else -> true
    }

    private fun hasPassageSoon(state: WidgetState, now: Long): Boolean {
        val limit = now + NearbyRules.ACTIVE_WITHIN_MINUTES * 60_000L
        return state.departures.any { !it.isCancelled && (it.displayTime ?: Long.MAX_VALUE) <= limit }
    }
}
