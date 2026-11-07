package fr.gotraceo.live.data

// Choix de l'accès aux données : sans clé, le cache de Tracéo, qui interroge lui-même PRIM ; avec une
// clé, PRIM et l'open data IDFM en direct.
// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

/** Passages renvoyés par une source, avec la date du relevé quand elle ne répond pas en direct. */
data class PassageSnapshot(
    val departures: List<Departure>,
    /** Date du relevé de la source ; null si elle répond en direct ou n'a aucun relevé. */
    val dataAt: Long? = null,
    /** Faux si le relevé est ancien ; null si la source répond en direct. */
    val fresh: Boolean? = null,
    /** Date à laquelle Tracéo devrait servir son prochain relevé ; null si inconnue ou en direct. */
    val nextAt: Long? = null,
    /** Vrai si la réponse vient du cache de Tracéo, y compris en repli quand le quota PRIM est atteint. */
    val viaTraceo: Boolean = false,
)

/** Une source de passages. Les erreurs sont levées en exceptions au message lisible par l'utilisateur. */
fun interface PassageSource {
    fun passages(stopId: String, lineId: String): PassageSnapshot
}

/** Ce qu'un widget doit faire pour obtenir ses passages. */
enum class PassageRoute {
    TRACEO,
    PRIM,

    /** Arrêt du référentiel IDFM sans clé : Tracéo ne le connaît pas, aucun appel n'est fait. */
    RECONFIGURE,
}

object DataSources {

    /** L'instantané de Tracéo est relevé toutes les 2 minutes en journée : inutile d'aller plus vite. */
    const val TRACEO_MIN_REFRESH_MINUTES = 2
    private const val TRACEO_CACHE_PREFIX = "traceo_"

    fun route(stopIds: Collection<String>, hasKey: Boolean): PassageRoute = when {
        hasKey -> PassageRoute.PRIM
        stopIds.isNotEmpty() && stopIds.all { TraceoClient.isTraceoStop(it) } -> PassageRoute.TRACEO
        else -> PassageRoute.RECONFIGURE
    }

    /**
     * Source des passages d'un widget.
     *
     * Avec une clé dont le quota du jour est épuisé ([primPaused]), ou dès que PRIM le refuse, les
     * arrêts connus de Tracéo passent par son cache ; les autres, créés avec la clé, attendent le
     * lendemain en gardant leurs derniers passages.
     */
    fun passageSource(
        apiKey: String,
        formats: PrimClient.FormatCache = PrimClient.FormatCache.InMemory(),
        usage: PrimClient.UsageListener = PrimClient.UsageListener.NONE,
        primPaused: Boolean = false,
        traceo: PassageSource = TraceoClient(),
    ): PassageSource {
        if (apiKey.isBlank()) return traceo
        if (primPaused) return PassageSource { stopId, lineId -> quotaFallback(traceo, stopId, lineId) }
        val prim = PrimClient(apiKey, formats = formats, usage = usage)
        return PassageSource { stopId, lineId ->
            try {
                prim.passages(stopId, lineId)
            } catch (e: PrimException) {
                if (e.kind != PrimException.Kind.QUOTA) throw e
                quotaFallback(traceo, stopId, lineId)
            }
        }
    }

    /** Message des widgets créés avec la clé, que Tracéo ne connaît pas, quand le quota est épuisé. */
    const val QUOTA_WAIT_MESSAGE = "Quota PRIM du jour atteint : ce widget reprendra demain."

    private fun quotaFallback(traceo: PassageSource, stopId: String, lineId: String): PassageSnapshot {
        if (!TraceoClient.isTraceoStop(stopId)) throw PrimException(PrimException.Kind.QUOTA, QUOTA_WAIT_MESSAGE)
        return traceo.passages(stopId, lineId)
    }

    /** Source du format À proximité : une requête par ligne sans clé, une par zone avec une clé. */
    fun proximitySource(
        apiKey: String,
        formats: PrimClient.FormatCache = PrimClient.FormatCache.InMemory(),
        usage: PrimClient.UsageListener = PrimClient.UsageListener.NONE,
        primPaused: Boolean = false,
        traceo: PassageSource = TraceoClient(),
    ): (NearbyLine) -> Result<PassageSnapshot> {
        val byLine: (NearbyLine) -> Result<PassageSnapshot> = { nearby ->
            runCatching { traceo.passages(nearby.stopId, nearby.line.id) }
        }
        // Les arrêts de l'index local sont des zones connues de Tracéo : le repli marche pour toutes.
        if (apiKey.isBlank() || primPaused) return byLine
        val prim = PrimClient(apiKey, formats = formats, usage = usage)
        val zones = PrimZonePassages { stopId -> prim.departures(stopId, null) }
        return { nearby ->
            val result = zones.passages(nearby)
            val quota = (result.exceptionOrNull() as? PrimException)?.kind == PrimException.Kind.QUOTA
            if (quota) byLine(nearby) else result
        }
    }

    /** Vrai si l'échec vient du réseau : inutile alors d'interroger d'autres lignes. */
    fun isNetworkFailure(error: Throwable?): Boolean = when (error) {
        null -> false
        is TraceoException -> error.kind == TraceoException.Kind.NETWORK
        is PrimException -> error.kind == PrimException.Kind.NETWORK
        else -> true
    }

    /** Intervalle d'actualisation réellement appliqué pour le réglage choisi par l'utilisateur. */
    fun refreshMinutes(chosen: Int, hasKey: Boolean): Int =
        if (!hasKey && chosen == 1) TRACEO_MIN_REFRESH_MINUTES else chosen

    /** Intervalles à proposer : seulement ceux qui s'appliquent tels quels avec cette source. */
    fun refreshChoices(all: List<Int>, hasKey: Boolean): List<Int> =
        all.filter { refreshMinutes(it, hasKey) == it }

    /** Modes proposés à la configuration : le câble et le funiculaire n'existent pas chez Tracéo. */
    fun modes(hasKey: Boolean): List<TransportMode> =
        TransportMode.entries.filter { hasKey || it != TransportMode.OTHER }

    /** Tracéo sert toutes ses lignes d'un coup : un seul fichier, filtré ensuite par mode. */
    fun linesCacheName(mode: TransportMode, hasKey: Boolean): String =
        if (hasKey) "lines_" + mode.apiModes.joinToString("_") + ".json" else TRACEO_CACHE_PREFIX + "lines.json"

    /** Les identifiants d'arrêt diffèrent selon la source : leurs fichiers ne doivent pas se mélanger. */
    fun stopsCacheName(lineId: String, hasKey: Boolean): String {
        val name = "stops_" + lineId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json"
        return if (hasKey) name else TRACEO_CACHE_PREFIX + name
    }
}

/** Calcule l'état d'un widget à partir des réponses de sa source. */
object WidgetStates {

    private const val MAX_STORED_DEPARTURES = 12

    /**
     * @param results une réponse par point d'arrêt interrogé
     * @param removeTerminusArrivals vrai pour Tracéo, qui inclut les arrivées au terminus
     */
    fun from(
        config: WidgetConfig,
        results: List<Result<PassageSnapshot>>,
        previous: WidgetState?,
        now: Long,
        removeTerminusArrivals: Boolean,
    ): WidgetState {
        val snapshots = results.mapNotNull { it.getOrNull() }
        val errors = results.mapNotNull { it.exceptionOrNull() }

        if (results.isNotEmpty() && errors.size == results.size) {
            // Un 429 ou un 503 ne dure pas : on garde les dernières données. Un 404 ne passera pas :
            // on les retire pour afficher tout de suite le message demandant de reconfigurer.
            val unknownStop = errors.any { it is TraceoException && it.kind == TraceoException.Kind.NOT_FOUND }
            return WidgetState(
                fetchedAt = previous?.fetchedAt ?: 0L,
                attemptedAt = now,
                departures = if (unknownStop) emptyList() else previous?.departures.orEmpty(),
                error = errorMessage(errors.first()),
                dataAt = previous?.dataAt,
                fresh = previous?.fresh,
            )
        }
        val received = snapshots.flatMap { it.departures }
        // Tracéo inclut les arrivées au terminus, y compris en repli d'une clé dont le quota est épuisé.
        val viaTraceo = removeTerminusArrivals || snapshots.any { it.viaTraceo }
        val shown = if (viaTraceo) Directions.withoutTerminusArrivals(received, config.stop.name) else received
        val filtered = Directions.upcoming(Directions.filter(shown, config.directions), now)
        return WidgetState(
            fetchedAt = now,
            attemptedAt = now,
            departures = filtered.take(MAX_STORED_DEPARTURES),
            error = if (errors.isNotEmpty() && filtered.isEmpty()) errorMessage(errors.first()) else null,
            // Plusieurs relevés : on annonce le plus ancien, et « récent » seulement si tous le sont.
            dataAt = snapshots.mapNotNull { it.dataAt }.minOrNull(),
            fresh = snapshots.mapNotNull { it.fresh }.takeIf { it.isNotEmpty() }?.all { it },
        )
    }

    fun errorMessage(t: Throwable): String = when (t) {
        is PrimException -> t.message ?: "Erreur PRIM"
        is TraceoException -> t.message ?: "Tracéo est indisponible."
        else -> "Erreur : ${t.message ?: t.javaClass.simpleName}"
    }
}
