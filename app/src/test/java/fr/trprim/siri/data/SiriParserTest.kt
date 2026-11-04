package fr.trprim.siri.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SiriParserTest {

    private val metroResponse = """
        {"Siri":{"ServiceDelivery":{"ResponseTimestamp":"2024-05-02T10:00:00.000Z","ProducerRef":"IVTR_HUB",
         "StopMonitoringDelivery":[{"ResponseTimestamp":"2024-05-02T10:00:00.000Z","Version":"2.0","Status":"true",
          "MonitoredStopVisit":[
           {"RecordedAtTime":"2024-05-02T09:59:50.000Z","ItemIdentifier":"x1",
            "MonitoringRef":{"value":"STIF:StopPoint:Q:22092:"},
            "MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01374:"},
             "DirectionName":[{"value":"Porte de Clignancourt"}],
             "DestinationRef":{"value":"STIF:StopPoint:Q:22078:"},
             "DestinationName":[{"value":"Porte de Clignancourt"}],
             "MonitoredCall":{"StopPointName":[{"value":"Châtelet"}],"VehicleAtStop":false,
              "DestinationDisplay":[{"value":"Porte de Clignancourt"}],
              "ExpectedArrivalTime":"2024-05-02T10:02:00.000Z","ExpectedDepartureTime":"2024-05-02T10:02:20.000Z",
              "DepartureStatus":"onTime"}}},
           {"RecordedAtTime":"2024-05-02T09:59:50.000Z","ItemIdentifier":"x2",
            "MonitoringRef":{"value":"STIF:StopPoint:Q:22092:"},
            "MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01374:"},
             "DestinationName":[{"value":"Porte de Clignancourt"}],
             "MonitoredCall":{"VehicleAtStop":true,"ExpectedDepartureTime":"2024-05-02T10:00:30+02:00"}}}
          ]}]}}}
    """.trimIndent()

    private val rerResponse = """
        {"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"Status":"true","MonitoredStopVisit":[
          {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01742:"},
            "DirectionRef":{"value":"Aller"},
            "DestinationName":[{"value":"Marne-la-Vallée Chessy"}],
            "JourneyNote":[{"value":"QIKI"}],
            "MonitoredCall":{"AimedArrivalTime":"2024-05-02T10:05:00.000Z","ExpectedArrivalTime":"2024-05-02T10:08:00.000Z",
              "ArrivalPlatformName":{"value":"2"},"ArrivalStatus":"delayed"}}},
          {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01742:"},
            "DestinationName":[{"value":"Boissy-Saint-Léger"}],
            "MonitoredCall":{"ExpectedDepartureTime":"2024-05-02T10:10:00.000Z","DepartureStatus":"cancelled"}}},
          {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01742:"},
            "DestinationName":[{"value":"Sans horaire"}],
            "MonitoredCall":{}}}
        ]}]}}}
    """.trimIndent()

    @Test
    fun parsesMetroVisits() {
        val result = SiriParser.parse(metroResponse, "IDFM:22092")
        assertTrue(result.recognized)
        assertNull(result.errorText)
        assertEquals(2, result.departures.size)

        val first = result.departures[0]
        assertEquals("STIF:Line::C01374:", first.lineRef)
        assertEquals("Porte de Clignancourt", first.destination)
        assertEquals("Porte de Clignancourt", first.directionName)
        assertEquals(Instant.parse("2024-05-02T10:02:00Z").toEpochMilli(), first.displayTime)
        assertEquals(Instant.parse("2024-05-02T10:02:20Z").toEpochMilli(), first.leaveTime)
        assertEquals("onTime", first.status)
        assertEquals("IDFM:22092", first.stopId)
        assertFalse(first.atStop)

        val second = result.departures[1]
        assertTrue(second.atStop)
        assertNull(second.directionName)
        assertEquals("Porte de Clignancourt", second.directionKey)
        // Heure avec décalage horaire : 10:00:30+02:00 = 08:00:30Z
        assertEquals(Instant.parse("2024-05-02T08:00:30Z").toEpochMilli(), second.displayTime)
    }

    @Test
    fun parsesRerDetails() {
        val result = SiriParser.parse(rerResponse)
        assertEquals("Le passage sans horaire est ignoré", 2, result.departures.size)

        val delayed = result.departures[0]
        assertEquals("Aller", delayed.directionRef)
        assertEquals("2", delayed.platform)
        assertEquals("QIKI", delayed.journeyNote)
        assertEquals("delayed", delayed.status)
        assertEquals(3, delayed.delayMinutes)

        val cancelled = result.departures[1]
        assertTrue(cancelled.isCancelled)
        assertEquals(0, cancelled.delayMinutes)
    }

    @Test
    fun extractsSiriError() {
        val body = """
            {"Siri":{"ServiceDelivery":{"ResponseTimestamp":"2024-05-02T10:00:00Z","StopMonitoringDelivery":[
              {"Status":"false","ErrorCondition":{"ErrorInformation":{"ErrorText":"La requête contient des identifiants qui sont inconnus"}}}
            ]}}}
        """.trimIndent()
        val result = SiriParser.parse(body)
        assertTrue(result.recognized)
        assertTrue(result.departures.isEmpty())
        assertEquals("La requête contient des identifiants qui sont inconnus", result.errorText)
    }

    @Test
    fun rejectsNonSiriBodies() {
        assertFalse(SiriParser.parse("<html>Bad gateway</html>").recognized)
        assertFalse(SiriParser.parse("""{"message":"Invalid authentication credentials"}""").recognized)
    }

    @Test
    fun departureJsonRoundTrip() {
        val d = SiriParser.parse(rerResponse, "IDFM:monomodalStopPlace:470195").departures[0]
        assertEquals(d, Departure.fromJson(d.toJson()))
    }
}
