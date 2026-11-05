package fr.trprim.siri.widget

import android.appwidget.AppWidgetManager
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Mode automatique : un widget ordinaire dont la ligne et l'arrêt sont réécrits quand on le touche.
 * Le dessin et l'actualisation restent ceux des autres widgets, ce qui garde tous les formats compatibles.
 *
 * Un toucher relit la position. Deux touchers rapprochés ouvrent le choix des lignes proches.
 */
object AutoMode {

    private const val TAG = "TRWidget"
    private const val INDEX_FILE = "stop_index.bin"

    /** Widgets dont la position est en cours de lecture : la feuille de choix l'attend. */
    private val locating: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Depuis quand l'usager choisit une ligne : une mise à jour plus ancienne ne doit plus rien changer. */
    private val choosingSince = ConcurrentHashMap<Int, Long>()

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

    /**
     * Premier toucher : le prochain toucher du délai ouvrira le choix. Seule la cible du toucher est
     * changée, sans redessiner l'image, pour que le lanceur la reçoive le plus vite possible.
     */
    fun openDoubleTapWindow(context: Context, widgetId: Int, now: Long) {
        val prefs = Prefs(context)
        prefs.saveAuto(widgetId, prefs.auto(widgetId).copy(choiceUntil = now + AutoState.DOUBLE_TAP_MS))
        AppWidgetManager.getInstance(context)
            .partiallyUpdateAppWidget(widgetId, WidgetRenderer.doubleTapViews(context, widgetId))
    }

    /** Second toucher reçu par le service, avant que le lanceur ait pris en compte la nouvelle cible. */
    fun openChoice(context: Context, widgetId: Int) {
        try {
            context.startActivity(WidgetRenderer.choiceIntent(context, widgetId))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Ouverture du choix refusée", e)
        }
    }

    fun beginLocating(widgetId: Int) {
        locating.add(widgetId)
    }

    fun endLocating(widgetId: Int) {
        locating.remove(widgetId)
    }

    fun isLocating(widgetId: Int): Boolean = widgetId in locating

    /** La feuille de choix vient de s'ouvrir : le toucher précédent ne doit plus changer le widget. */
    fun markChoosing(widgetId: Int) {
        choosingSince[widgetId] = System.currentTimeMillis()
    }

    private fun tookOver(widgetId: Int, tappedAt: Long): Boolean = (choosingSince[widgetId] ?: 0L) >= tappedAt

    /** Toucher du widget : relit la position et met à jour le widget. */
    suspend fun onTap(context: Context, widgetId: Int, tappedAt: Long) {
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
        val before = prefs.auto(widgetId)
        val decision = AnchorPolicy.decide(before.anchor, fix)
        val moved = if (fix != null && before.anchor != null) before.anchor.distanceTo(fix.point) else null
        Log.i(
            TAG,
            "Position en ${SystemClock.elapsedRealtime() - started} ms, précision ${fix?.accuracyM} m, " +
                "à $moved m de l'arrêt choisi : $decision",
        )

        // Une position trop imprécise ne remplace pas la dernière position fiable.
        val point = if (decision == AnchorDecision.IMPRECISE) before.last ?: fix?.point else fix?.point ?: before.last
        prefs.saveAuto(widgetId, before.copy(last = point))
        endLocating(widgetId)
        if (tookOver(widgetId, tappedAt)) return

        val ranking = withContext(Dispatchers.IO) { point?.let { rankingEnsuringIndex(app, it) } }
        var message = when (decision) {
            AnchorDecision.NO_FIX -> AutoMessage.NO_POSITION
            AnchorDecision.IMPRECISE -> AutoMessage.IMPRECISE
            else -> null
        }
        var anchor = before.anchor
        var shownLineId = config.line.id
        var applied = false
        if (decision == AnchorDecision.MOVED && point != null) {
            val best = pickBest(prefs, config, ranking)
            if (tookOver(widgetId, tappedAt)) return
            if (best == null) {
                message = AutoMessage.NO_STOP
            } else {
                WidgetUpdater.exclusive {
                    prefs.saveConfig(best.config)
                    prefs.saveState(widgetId, best.state)
                }
                anchor = point
                shownLineId = best.nearby.line.id
                applied = true
            }
        }
        if (!applied) WidgetUpdater.refresh(app, intArrayOf(widgetId), force = true)
        if (tookOver(widgetId, tappedAt)) return

        val lines = ranking?.list.orEmpty()
        val latest = prefs.auto(widgetId)
        prefs.saveAuto(
            widgetId,
            latest.copy(
                anchor = anchor,
                last = point,
                distanceM = lines.firstOrNull { it.nearby.line.id == shownLineId }?.nearby?.distanceM ?: latest.distanceM,
                hintUntil = System.currentTimeMillis() + AutoState.HINT_MS,
                others = lines.count { it.nearby.line.id != shownLineId },
                message = message,
            ),
        )
        WidgetUpdater.render(app, widgetId)
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

    private class Picked(val nearby: NearbyLine, val config: WidgetConfig, val state: WidgetState)

    /** La meilleure ligne qui a un passage bientôt, ou null si aucune ligne n'est proche. */
    private suspend fun pickBest(prefs: Prefs, config: WidgetConfig, ranking: NearbyRanking?): Picked? {
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
        return Picked(chosen.first.nearby, next, state)
    }

    /** Choix fait dans la feuille : l'affiche et le retient pour ce lieu. */
    suspend fun choose(context: Context, widgetId: Int, nearby: NearbyLine) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val config = prefs.config(widgetId) ?: return
        val now = System.currentTimeMillis()
        prefs.auto(widgetId).last?.let { prefs.placeChoices = prefs.placeChoices.remember(it, nearby.line.id, now) }
        val next = configFor(widgetId, nearby, config.theme)
        val source = DataSources.passageSource(prefs.apiKey, prefs.primFormats)
        val result = withContext(Dispatchers.IO) { fetch(source, nearby) }
        val state = WidgetStates.from(next, listOf(result), null, now, removeTerminusArrivals = !prefs.hasApiKey)
        WidgetUpdater.exclusive {
            prefs.saveConfig(next)
            prefs.saveState(widgetId, state)
        }
        val auto = prefs.auto(widgetId)
        prefs.saveAuto(
            widgetId,
            auto.copy(
                anchor = auto.last ?: auto.anchor,
                distanceM = nearby.distanceM,
                choiceUntil = 0L,
                hintUntil = 0L,
                others = 0,
                message = null,
            ),
        )
        WidgetUpdater.render(app, widgetId)
    }

    /** Ferme la fenêtre du double toucher : le prochain toucher relira la position. */
    fun closeChoice(context: Context, widgetId: Int) {
        val prefs = Prefs(context)
        prefs.saveAuto(widgetId, prefs.auto(widgetId).copy(choiceUntil = 0L, hintUntil = 0L, message = null))
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
