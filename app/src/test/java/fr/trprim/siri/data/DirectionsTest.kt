package fr.trprim.siri.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionsTest {

    private val now = 1_700_000_000_000L

    private fun dep(stopId: String, destination: String, inMinutes: Int, direction: String? = null) = Departure(
        lineRef = "STIF:Line::C01374:",
        destination = destination,
        directionName = direction,
        directionRef = null,
        expectedArrival = now + inMinutes * 60_000L,
        expectedDeparture = null,
        aimedArrival = null,
        aimedDeparture = null,
        atStop = false,
        platform = null,
        status = null,
        journeyNote = null,
        stopId = stopId,
    )

    @Test
    fun onePointPerDirection() {
        val stop = Stop("Châtelet", "Paris", listOf("IDFM:22092", "IDFM:463158"))
        val results = mapOf(
            "IDFM:22092" to listOf(dep("IDFM:22092", "Porte de Clignancourt", 2), dep("IDFM:22092", "Porte de Clignancourt", 6)),
            "IDFM:463158" to listOf(dep("IDFM:463158", "Bagneux - Lucie Aubrac", 3)),
        )
        val options = Directions.buildOptions(stop, results)
        assertEquals(2, options.size)
        assertEquals("Vers Porte de Clignancourt", options[0].title)
        assertEquals("Vers Bagneux - Lucie Aubrac", options[1].title)

        val selection = Directions.toSelection(options, listOf(options[0]))
        assertEquals(mapOf("IDFM:22092" to emptySet<String>()), selection)
        assertEquals("→ Porte de Clignancourt", Directions.label(options, listOf(options[0]), stop))
        assertEquals(Directions.ALL_DIRECTIONS, Directions.label(options, options, stop))

        val filtered = Directions.filter(results.values.flatten(), selection)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.stopId == "IDFM:22092" })
    }

    @Test
    fun destinationsOfASingleStopArea() {
        val zda = "IDFM:monomodalStopPlace:473921"
        val stop = Stop("Châtelet les Halles", "Paris", listOf(zda))
        val deps = listOf(
            dep(zda, "Boissy-Saint-Léger", 2),
            dep(zda, "Saint-Germain-en-Laye", 3),
            dep(zda, "Marne-la-Vallée Chessy", 5),
            dep(zda, "Boissy-Saint-Léger", 12),
        )
        val options = Directions.buildOptions(stop, mapOf(zda to deps))
        assertEquals(3, options.size)

        val chosen = options.filter { it.key == "Boissy-Saint-Léger" || it.key == "Marne-la-Vallée Chessy" }
        val selection = Directions.toSelection(options, chosen)
        assertEquals(setOf("Boissy-Saint-Léger", "Marne-la-Vallée Chessy"), selection[zda])

        val filtered = Directions.filter(deps, selection)
        assertEquals(3, filtered.size)
        assertTrue(filtered.none { it.destination == "Saint-Germain-en-Laye" })
    }

    @Test
    fun directionNameGroupsShortTurns() {
        val zda = "IDFM:monomodalStopPlace:1"
        val stop = Stop("Gare", null, listOf(zda))
        val deps = listOf(
            dep(zda, "Terminus Nord", 2, direction = "Nord"),
            dep(zda, "Arrêt intermédiaire", 4, direction = "Nord"),
            dep(zda, "Terminus Sud", 5, direction = "Sud"),
        )
        val options = Directions.buildOptions(stop, mapOf(zda to deps))
        assertEquals(listOf("Nord", "Sud"), options.map { it.key })
        assertEquals("Destinations : Terminus Nord, Arrêt intermédiaire", options[0].subtitle)
    }

    @Test
    fun unknownDirectionKeepsAllPassages() {
        val stop = Stop("Mairie", "Montreuil", listOf("IDFM:1", "IDFM:2"))
        val options = Directions.buildOptions(stop, mapOf("IDFM:1" to emptyList(), "IDFM:2" to null))
        assertEquals(2, options.size)
        assertNull(options[0].key)
        assertEquals("Point d'arrêt 1", options[0].title)
        val selection = Directions.toSelection(options, listOf(options[1]))
        assertEquals(mapOf("IDFM:2" to emptySet<String>()), selection)
    }

    @Test
    fun upcomingDropsPastAndSorts() {
        val deps = listOf(
            dep("a", "Later", 9),
            dep("a", "Past", -5),
            dep("a", "Soon", 1),
        )
        val upcoming = Directions.upcoming(deps, now)
        assertEquals(listOf("Soon", "Later"), upcoming.map { it.destination })
    }

    @Test
    fun widgetConfigRoundTrip() {
        val config = WidgetConfig(
            widgetId = 42,
            mode = TransportMode.METRO,
            line = Line("C01374", "4", "4", "metro", network = "Métro", operator = "RATP", color = "BE418D", textColor = "FFFFFF"),
            stop = Stop("Châtelet", "Paris", listOf("IDFM:22092", "IDFM:463158")),
            directions = mapOf("IDFM:22092" to emptySet(), "IDFM:463158" to setOf("Bagneux")),
            directionLabel = "→ Porte de Clignancourt",
        )
        assertEquals(config, WidgetConfig.fromJson(config.toJson()))
    }
}
