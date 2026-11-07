package fr.gotraceo.live.data

import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Erreur lors d'un appel à Tracéo, avec un message lisible par l'utilisateur.
 *
 * C'est une [IOException] pour que le référentiel se replie sur son cache périmé, comme avec l'open data.
 */
class TraceoException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind { BAD_REQUEST, NOT_FOUND, RATE_LIMITED, UNAVAILABLE, SERVER, NETWORK, INVALID_RESPONSE }
}

/**
 * Client de Tracéo (https://gotraceo.fr), accès par défaut aux données PRIM quand l'usager n'a pas de
 * clé : Tracéo interroge PRIM et sert ses lignes, ses arrêts et son cache de passages SIRI ET.
 * Aucune clé ni en-tête particulier.
 *
 * Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.
 *
 * @param warmedLines lignes dont les arrêts ont déjà été redemandés après un 503
 */
class TraceoClient(
    private val baseUrl: String = BASE_URL,
    private val warmedLines: MutableSet<String> = warmedInThisProcess,
) : PassageSource {

    companion object {
        const val BASE_URL = "https://gotraceo.fr"
        const val SOURCE_NAME = "Tracéo"

        /** Version complète de la mention, quand la place le permet : les données restent celles de PRIM. */
        const val SOURCE_DETAIL = "PRIM via Tracéo"

        private const val LINE_PREFIX = "line:IDFM:"
        private const val STOP_PREFIX = "stop_area:IDFM:"

        /** Délai de lecture des listes : `/api/lines` pèse environ 32 Ko, `/api/stations` peut partir à froid. */
        private const val LIST_READ_TIMEOUT_MS = 30_000
        private const val PASSAGES_READ_TIMEOUT_MS = 12_000

        private val warmedInThisProcess: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** Vrai si l'identifiant est une zone d'arrêt de Tracéo, et non un arrêt du référentiel IDFM. */
        fun isTraceoStop(stopId: String): Boolean = stopId.startsWith(STOP_PREFIX)

        private fun lineUrn(lineId: String): String = LINE_PREFIX + Referential.bareLineId(lineId)
    }

    /** Toutes les lignes, triées comme celles du référentiel. */
    @Throws(TraceoException::class)
    fun lines(): List<Line> {
        val root = get("$baseUrl/api/lines", LIST_READ_TIMEOUT_MS, "Liste des lignes introuvable chez Tracéo.")
        val lines = root.optJSONArray("lines") ?: throw invalidResponse()
        return (0 until lines.length())
            .mapNotNull { i -> lines.optJSONObject(i)?.let(::parseLine) }
            .sortedWith(Referential.lineComparator)
    }

    /**
     * Zones d'arrêt d'une ligne. À n'appeler qu'à la configuration, jamais dans la boucle
     * d'actualisation : à froid, cet appel coûte du quota à Tracéo.
     */
    @Throws(TraceoException::class)
    fun stops(lineId: String): List<Stop> {
        val url = "$baseUrl/api/stations?line=" + Http.encode(lineUrn(lineId))
        val root = get(url, LIST_READ_TIMEOUT_MS, "Cette ligne est inconnue de Tracéo.")
        val stations = root.optJSONArray("stations") ?: throw invalidResponse()
        return (0 until stations.length()).mapNotNull { i -> stations.optJSONObject(i)?.let(::parseStop) }
    }

    /**
     * Prochains passages d'une ligne à une zone d'arrêt.
     *
     * Un 503 peut venir d'arrêts absents du cache de Tracéo : les demander une fois par ligne et par
     * processus le remplit, sans insister si la route est coupée.
     */
    @Throws(TraceoException::class)
    fun departures(stopId: String, lineId: String): PassageSnapshot = try {
        requestPassages(stopId, lineId)
    } catch (e: TraceoException) {
        if (e.kind != TraceoException.Kind.UNAVAILABLE || !warmedLines.add(Referential.bareLineId(lineId))) throw e
        try {
            stops(lineId)
        } catch (stationsError: TraceoException) {
            throw e
        }
        requestPassages(stopId, lineId)
    }

    override fun passages(stopId: String, lineId: String): PassageSnapshot = departures(stopId, lineId)

    private fun requestPassages(stopId: String, lineId: String): PassageSnapshot {
        val line = lineUrn(lineId)
        val url = "$baseUrl/api/widget/passages?line=" + Http.encode(line) + "&stop=" + Http.encode(stopId)
        val root = get(url, PASSAGES_READ_TIMEOUT_MS, "Arrêt ou ligne inconnus de Tracéo : reconfigurez ce widget.")
        val passages = root.optJSONArray("passages") ?: throw invalidResponse()
        return PassageSnapshot(
            departures = (0 until passages.length()).mapNotNull { i ->
                passages.optJSONObject(i)?.let { parsePassage(it, stopId, line) }
            },
            dataAt = SiriParser.parseTime(root.optStringOrNull("fetchedAt")),
            fresh = root.optBoolean("fresh", false),
            // Absent des anciennes versions de Tracéo, null quand la collecte est suspendue.
            nextAt = SiriParser.parseTime(root.optStringOrNull("nextAt")),
            viaTraceo = true,
        )
    }

    private fun get(url: String, readTimeoutMs: Int, notFoundMessage: String): JSONObject {
        val response = try {
            Http.get(url, readTimeoutMs = readTimeoutMs)
        } catch (e: IOException) {
            throw TraceoException(TraceoException.Kind.NETWORK, "Erreur réseau : ${e.message ?: e.javaClass.simpleName}", e)
        }
        when (response.code) {
            in 200..299 -> Unit
            400 -> throw TraceoException(TraceoException.Kind.BAD_REQUEST, "Requête refusée par Tracéo (HTTP 400).")
            404 -> throw TraceoException(TraceoException.Kind.NOT_FOUND, notFoundMessage)
            429 -> throw TraceoException(
                TraceoException.Kind.RATE_LIMITED,
                "Trop de requêtes vers Tracéo : nouvel essai à la prochaine actualisation.",
            )
            503 -> throw TraceoException(TraceoException.Kind.UNAVAILABLE, "Tracéo est momentanément indisponible.")
            else -> throw TraceoException(TraceoException.Kind.SERVER, "Erreur de Tracéo (HTTP ${response.code}).")
        }
        return try {
            JSONObject(response.body)
        } catch (e: Exception) {
            throw invalidResponse(e)
        }
    }

    private fun invalidResponse(cause: Throwable? = null) =
        TraceoException(TraceoException.Kind.INVALID_RESPONSE, "Réponse inattendue de Tracéo.", cause)

    private fun parseLine(o: JSONObject): Line? {
        val id = o.optStringOrNull("id")?.let(Referential::bareLineId) ?: return null
        val code = o.optStringOrNull("code")
        val name = o.optStringOrNull("name") ?: code ?: id
        val modeKey = o.optJSONObject("mode")?.optStringOrNull("key").orEmpty()
        return Line(
            id = id,
            name = name,
            shortName = code ?: name,
            // Comme dans le référentiel IDFM : Line.isRer() sépare ensuite RER et Transilien.
            mode = if (modeKey == "rer" || modeKey == "transilien") "rail" else modeKey,
            subMode = o.optStringOrNull("submode"),
            network = o.optStringOrNull("network"),
            operator = o.optJSONArray("operators").toStringList().firstOrNull(),
            color = o.optStringOrNull("color")?.let(Referential::cleanColor),
            textColor = o.optStringOrNull("textColor")?.let(Referential::cleanColor),
        )
    }

    /** Ce sont des zones d'arrêt, pas des quais : un seul identifiant par arrêt, et pas de commune. */
    private fun parseStop(o: JSONObject): Stop? {
        val id = o.optStringOrNull("id") ?: return null
        val name = o.optStringOrNull("name") ?: return null
        return Stop(name = name, town = null, stopIds = listOf(id))
    }

    private fun parsePassage(o: JSONObject, stopId: String, line: String): Departure? {
        val expected = SiriParser.parseTime(o.optStringOrNull("expected"))
        val aimed = SiriParser.parseTime(o.optStringOrNull("aimed"))
        if (expected == null && aimed == null) return null
        return Departure(
            lineRef = line,
            destination = o.optStringOrNull("destination")?.trim() ?: "?",
            // Pas de nom de direction : les sens se regroupent par destination.
            directionName = null,
            directionRef = o.optStringOrNull("direction"),
            expectedArrival = expected,
            expectedDeparture = null,
            aimedArrival = aimed,
            aimedDeparture = null,
            atStop = o.optBoolean("atStop"),
            platform = null,
            status = o.optStringOrNull("status"),
            journeyNote = o.optStringOrNull("mission"),
            stopId = stopId,
        )
    }
}
