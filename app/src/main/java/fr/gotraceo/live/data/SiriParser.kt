package fr.gotraceo.live.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Analyse des réponses SIRI Lite « StopMonitoring » de l'API PRIM (format JSON).
 *
 * Structure attendue :
 * ```
 * { "Siri": { "ServiceDelivery": { "StopMonitoringDelivery": [ {
 *     "Status": "true",
 *     "ErrorCondition": { "ErrorInformation": { "ErrorText": "..." } },
 *     "MonitoredStopVisit": [ { "MonitoringRef": {"value": "..."},
 *        "MonitoredVehicleJourney": { "LineRef": {"value": "STIF:Line::C01742:"},
 *            "DirectionName": [{"value": "..."}], "DestinationName": [{"value": "..."}],
 *            "MonitoredCall": { "ExpectedArrivalTime": "2024-01-01T10:00:00.000Z", ... } } } ]
 * } ] } } }
 * ```
 * Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.
 */
object SiriParser {

    data class Result(
        val departures: List<Departure>,
        /** Message d'erreur SIRI (ErrorCondition) s'il y en a un. */
        val errorText: String?,
        /** Faux si la réponse n'a pas pu être reconnue comme une réponse SIRI. */
        val recognized: Boolean,
    )

    fun parse(body: String, stopId: String = ""): Result {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            return Result(emptyList(), null, recognized = false)
        }
        val delivery = root.optJSONObject("Siri")?.optJSONObject("ServiceDelivery")
            ?: return Result(emptyList(), null, recognized = false)

        val deliveries = asArray(delivery.opt("StopMonitoringDelivery"))
        val departures = ArrayList<Departure>()
        var error: String? = null
        for (i in 0 until deliveries.length()) {
            val d = deliveries.optJSONObject(i) ?: continue
            errorText(d)?.let { if (error == null) error = it }
            val visits = asArray(d.opt("MonitoredStopVisit"))
            for (j in 0 until visits.length()) {
                val visit = visits.optJSONObject(j) ?: continue
                parseVisit(visit, stopId)?.let(departures::add)
            }
        }
        if (error == null) {
            // Certaines erreurs sont remontées au niveau ServiceDelivery.
            error = errorText(delivery)
        }
        return Result(departures, error, recognized = true)
    }

    private fun errorText(o: JSONObject): String? {
        val cond = o.optJSONObject("ErrorCondition") ?: return null
        val info = cond.optJSONObject("ErrorInformation")
        val text = info?.let { text(it, "ErrorText") }
            ?: text(cond, "Description")
            ?: cond.keys().asSequence().firstOrNull()
        return text?.takeIf { it.isNotBlank() } ?: "Erreur SIRI"
    }

    private fun parseVisit(visit: JSONObject, stopId: String): Departure? {
        val journey = visit.optJSONObject("MonitoredVehicleJourney") ?: return null
        val call = journey.optJSONObject("MonitoredCall") ?: JSONObject()

        val expectedArrival = time(call, "ExpectedArrivalTime")
        val expectedDeparture = time(call, "ExpectedDepartureTime")
        val aimedArrival = time(call, "AimedArrivalTime")
        val aimedDeparture = time(call, "AimedDepartureTime")
        if (expectedArrival == null && expectedDeparture == null && aimedArrival == null && aimedDeparture == null) {
            return null
        }

        val destination = text(journey, "DestinationName")
            ?: text(call, "DestinationDisplay")
            ?: text(journey, "DirectionName")
            ?: "?"
        val status = text(call, "DepartureStatus")?.takeIf { it.isNotBlank() }
            ?: text(call, "ArrivalStatus")?.takeIf { it.isNotBlank() }
        val platform = text(call, "ArrivalPlatformName")
            ?: text(call, "DeparturePlatformName")
            ?: text(call, "PlatformTraversal")

        return Departure(
            lineRef = text(journey, "LineRef"),
            destination = destination.trim(),
            directionName = text(journey, "DirectionName")?.trim(),
            directionRef = text(journey, "DirectionRef")?.trim(),
            expectedArrival = expectedArrival,
            expectedDeparture = expectedDeparture,
            aimedArrival = aimedArrival,
            aimedDeparture = aimedDeparture,
            atStop = call.optBoolean("VehicleAtStop", false),
            platform = platform?.trim()?.takeIf { it.isNotEmpty() && it != "unknown" },
            status = status,
            journeyNote = text(journey, "JourneyNote")?.trim()?.takeIf { it.isNotEmpty() },
            stopId = stopId,
        )
    }

    /**
     * Les champs texte SIRI Lite peuvent être une chaîne, un objet `{"value": "..."}`
     * ou un tableau de tels objets (NaturalLanguageString).
     */
    internal fun text(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        return valueOf(o.opt(key))
    }

    private fun valueOf(v: Any?): String? = when (v) {
        null, JSONObject.NULL -> null
        is String -> v.takeIf { it.isNotBlank() }
        is JSONObject -> valueOf(v.opt("value"))
        is JSONArray -> (0 until v.length()).asSequence().mapNotNull { valueOf(v.opt(it)) }.firstOrNull()
        is Number, is Boolean -> v.toString()
        else -> null
    }

    private fun asArray(v: Any?): JSONArray = when (v) {
        is JSONArray -> v
        is JSONObject -> JSONArray().put(v)
        else -> JSONArray()
    }

    private fun time(o: JSONObject, key: String): Long? = parseTime(text(o, key))

    fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                Instant.parse(s).toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }
}
