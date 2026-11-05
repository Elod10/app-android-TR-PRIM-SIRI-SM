package fr.trprim.siri.data

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
    private const val TRACEO_MIN_REFRESH_MINUTES = 2
    private const val TRACEO_CACHE_PREFIX = "traceo_"

    fun route(stopIds: Collection<String>, hasKey: Boolean): PassageRoute = when {
        hasKey -> PassageRoute.PRIM
        stopIds.isNotEmpty() && stopIds.all { TraceoClient.isTraceoStop(it) } -> PassageRoute.TRACEO
        else -> PassageRoute.RECONFIGURE
    }

    fun passageSource(
        apiKey: String,
        formats: PrimClient.FormatCache = PrimClient.FormatCache.InMemory(),
    ): PassageSource = if (apiKey.isBlank()) TraceoClient() else PrimClient(apiKey, formats = formats)

    /** Source du format À proximité : une requête par ligne sans clé, une par zone avec une clé. */
    fun proximitySource(
        apiKey: String,
        formats: PrimClient.FormatCache = PrimClient.FormatCache.InMemory(),
    ): (NearbyLine) -> Result<PassageSnapshot> {
        if (apiKey.isBlank()) {
            val traceo = TraceoClient()
            return { nearby -> runCatching { traceo.passages(nearby.stopId, nearby.line.id) } }
        }
        val prim = PrimClient(apiKey, formats = formats)
        return PrimZonePassages { stopId -> prim.departures(stopId, null) }::passages
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
        val shown = if (removeTerminusArrivals) Directions.withoutTerminusArrivals(received, config.stop.name) else received
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
