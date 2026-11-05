package fr.trprim.siri.widget

import fr.trprim.siri.data.Departure
import fr.trprim.siri.data.Line
import fr.trprim.siri.data.NearbyLine
import fr.trprim.siri.data.NearbyReason
import fr.trprim.siri.data.ProximityEntry
import fr.trprim.siri.data.ProximityState
import fr.trprim.siri.data.Stop
import fr.trprim.siri.data.TransportMode
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationContentTest {

    private val now = 1_700_000_000_000L
    private val metro = Line("C01371", "1", "1", "metro", color = "FFBE00", textColor = "000000")
    private val transilien = Line("C01730", "P", "P", "rail", network = "Transilien", color = "F28E42", textColor = "000000")

    private fun dep(destination: String, inMinutes: Int, delay: Int = 0, platform: String? = null, stopId: String = "IDFM:1") =
        Departure(
            lineRef = null,
            destination = destination,
            directionName = null,
            directionRef = null,
            expectedArrival = now + inMinutes * 60_000L,
            expectedDeparture = null,
            aimedArrival = now + (inMinutes - delay) * 60_000L,
            aimedDeparture = null,
            atStop = false,
            platform = platform,
            status = null,
            journeyNote = null,
            stopId = stopId,
        )

    private fun config(line: Line, stopIds: List<String> = listOf("IDFM:1", "IDFM:2")) = WidgetConfig(
        widgetId = 1,
        mode = TransportMode.METRO,
        line = line,
        stop = Stop("Bastille", "Paris", stopIds),
        directions = stopIds.associateWith { emptySet<String>() },
        directionLabel = "",
    )

    private fun state(vararg departures: Departure, error: String? = null, dataAt: Long? = null) = WidgetState(
        fetchedAt = now - 60_000L,
        attemptedAt = now,
        departures = departures.toList(),
        error = error,
        dataAt = dataAt,
        fresh = if (dataAt != null) true else null,
    )

    private fun text(config: WidgetConfig?, state: WidgetState?, hasApiKey: Boolean = true): NotificationText? {
        val model = WidgetModels.build(config, state, hasApiKey, refreshing = false, widthDp = 348f, heightDp = 192f, now = now)
        return NotificationContent.build(model, config?.line?.let(UiText::lineTitle)) { it.kind.name }
    }

    private fun model(config: WidgetConfig?, state: WidgetState?) =
        WidgetModels.build(config, state, hasApiKey = true, refreshing = false, widthDp = 348f, heightDp = 192f, now = now)

    private fun tile(config: WidgetConfig?, state: WidgetState?): TileText? =
        NotificationContent.tile(model(config, state), config?.line?.let(UiText::lineTitle)) { it.kind.name }

    @Test
    fun theTilePutsTheWaitFirst() {
        val one = tile(config(metro, listOf("IDFM:1")), state(dep("Château de Vincennes", 3), dep("Château de Vincennes", 9)))!!
        assertEquals("3 min, puis 9", one.label)
        assertEquals("Métro 1 → Vincennes", one.subtitle)
        assertEquals(true, one.active)

        val two = tile(config(metro), state(dep("Château de Vincennes", 3), dep("La Défense (Grande Arche)", 5, stopId = "IDFM:2")))!!
        assertEquals("Vincennes 3 min", two.label)
        assertEquals("La Défense 5 min", two.subtitle)
        assertEquals("Métro 1 · Bastille. Vers Château de Vincennes : 3 min. Vers La Défense : 5 min", two.description)
    }

    @Test
    fun aTileWithoutDeparturesLooksOff() {
        val loading = tile(config(metro), null)!!
        assertEquals("Métro 1", loading.label)
        assertEquals("LOADING", loading.subtitle)
        assertEquals(false, loading.active)
        assertNull(tile(null, null))
    }

    @Test
    fun theChipGivesTheVeryNextDeparture() {
        val departures = state(dep("Château de Vincennes", 7), dep("La Défense (Grande Arche)", 2, stopId = "IDFM:2"))
        assertEquals("2 min", NotificationContent.chip(model(config(metro), departures)))
        assertEquals("<1 min", NotificationContent.chip(model(config(metro), state(dep("La Défense", 0)))))
        assertNull(NotificationContent.chip(model(config(metro), state())))
    }

    @Test
    fun twoDirectionsFitOnOneLineWhenFolded() {
        val result = text(
            config(metro),
            state(dep("Château de Vincennes", 3), dep("La Défense (Grande Arche)", 5, stopId = "IDFM:2"), dep("Château de Vincennes", 9)),
        )!!
        assertEquals("Métro 1 · Bastille", result.title)
        assertEquals("Vincennes 3 min · La Défense 5 min", result.text)
        assertEquals(
            listOf("Vers Château de Vincennes : 3 min, puis 9 min", "Vers La Défense : 5 min"),
            result.lines,
        )
    }

    @Test
    fun trainsGiveTheirPlatformAndDelay() {
        val result = text(config(transilien, listOf("IDFM:1")), state(dep("Paris Nord", 4, delay = 3, platform = "2"), dep("Paris Nord", 19)))!!
        assertEquals("Vers Paris Nord : 4 min (voie 2, retard 3 min), puis 19 min", result.text)
        assertEquals(listOf(result.text), result.lines)
    }

    @Test
    fun noticesReplaceThePassages() {
        assertEquals("LOADING", text(config(metro), null)!!.text)
        assertEquals("NO_DEPARTURE", text(config(metro), state())!!.text)
        // Un widget à configurer n'a pas de notification.
        assertNull(text(null, null))
    }

    @Test
    fun theHeaderNamesTheSourceAndTheTime() {
        val traceo = "stop_area:IDFM:71673"
        val dataAt = now - 5 * 60_000L
        val snapshot = text(config(metro, listOf(traceo)), state(dep("La Défense", 5, stopId = traceo), dataAt = dataAt), hasApiKey = false)!!
        assertEquals("PRIM via Tracéo · relevé de ${DepartureFormat.clock(dataAt)}", snapshot.subText)

        val live = text(config(metro), state(dep("La Défense", 5)))!!
        assertEquals("Actualisé à ${DepartureFormat.clock(now - 60_000L)}", live.subText)

        val stale = text(config(metro), state(dep("La Défense", 5), error = "Panne"))!!
        assertEquals("Actualisation impossible, données de ${DepartureFormat.clock(now - 60_000L)}", stale.subText)
    }

    @Test
    fun nearbyLinesAreNamedInFull() {
        val entry = ProximityEntry(
            NearbyLine(metro, "Nation", "Paris", "71673", 33),
            NearbyReason.NEAREST,
            false,
            listOf(dep("Château de Vincennes", 3), dep("La Défense (Grande Arche)", 5)),
        )
        val state = ProximityState(now - 60_000L, now, listOf(entry))
        val model = WidgetModels.buildProximity(config(metro), state, false, 348f, 192f, now, true)
        val result = NotificationContent.build(model, null) { it.kind.name }!!
        assertEquals("À proximité", result.title)
        assertEquals(listOf("Métro 1, Nation · 33 m : Vincennes 3 min · La Défense 5 min"), result.lines)
    }
}
