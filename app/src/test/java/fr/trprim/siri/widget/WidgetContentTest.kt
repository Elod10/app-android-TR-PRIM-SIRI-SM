package fr.trprim.siri.widget

import fr.trprim.siri.data.Departure
import fr.trprim.siri.data.Line
import fr.trprim.siri.data.Stop
import fr.trprim.siri.data.TransportMode
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.data.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentTest {

    private val now = 1_700_000_000_000L
    private val metro = Line("C01371", "1", "1", "metro", color = "FFBE00", textColor = "000000")
    private val rer = Line("C01742", "RER A", "A", "rail", network = "RER", color = "EB2132", textColor = "FFFFFF")
    private val tram = Line("C01389", "T3a", "T3a", "tram", color = "FF7E2E", textColor = "000000")
    private val transilien = Line("C01730", "P", "P", "rail", network = "Transilien", color = "F28E42", textColor = "000000")

    private fun dep(
        destination: String,
        inMinutes: Int,
        direction: String? = null,
        atStop: Boolean = false,
        status: String? = null,
        delay: Int = 0,
        platform: String? = null,
        mission: String? = null,
        stopId: String = "IDFM:1",
    ) = Departure(
        lineRef = null,
        destination = destination,
        directionName = direction,
        directionRef = null,
        expectedArrival = now + inMinutes * 60_000L,
        expectedDeparture = null,
        aimedArrival = now + (inMinutes - delay) * 60_000L,
        aimedDeparture = null,
        atStop = atStop,
        platform = platform,
        status = status,
        journeyNote = mission,
        stopId = stopId,
    )

    private fun config(line: Line, directions: Map<String, Set<String>>, theme: WidgetTheme = WidgetTheme.AUTO) = WidgetConfig(
        widgetId = 1,
        mode = TransportMode.METRO,
        line = line,
        stop = Stop("Bastille", "Paris", listOf("IDFM:1", "IDFM:2")),
        directions = directions,
        directionLabel = "",
        theme = theme,
    )

    private val allDirections = mapOf("IDFM:1" to emptySet<String>(), "IDFM:2" to emptySet<String>())
    private val oneDirection = mapOf("IDFM:1" to setOf("Château de Vincennes"))

    // --- Actualisation ----------------------------------------------------------------------

    @Test
    fun tickFallsOnTheNextMinuteWhateverTheMomentItIsScheduled() {
        val minute = 60_000L
        val start = 28_000_000L * minute
        // Reprogrammer pendant la même minute ne repousse pas l'échéance.
        assertEquals(start + minute, WidgetUpdater.nextTickAt(start))
        assertEquals(start + minute, WidgetUpdater.nextTickAt(start + 1L))
        assertEquals(start + minute, WidgetUpdater.nextTickAt(start + minute - 1L))
        // Une fois déclenché, le tick suivant tombe sur la minute d'après.
        assertEquals(start + 2L * minute, WidgetUpdater.nextTickAt(start + minute))
    }

    // --- Choix du format ---------------------------------------------------------------------

    @Test
    fun formatFollowsSizeLineAndDirection() {
        fun select(w: Float, h: Float, rail: Boolean, single: Boolean) = WidgetFormat.select(w, h, rail, single)

        assertEquals(WidgetFormat.PASTILLE, select(84f, 92f, rail = false, single = true))
        assertEquals(WidgetFormat.PASTILLE, select(84f, 192f, rail = true, single = false))
        assertEquals(WidgetFormat.UN_SENS, select(172f, 92f, rail = true, single = true))
        assertEquals(WidgetFormat.UN_SENS, select(172f, 92f, rail = false, single = false))
        assertEquals(WidgetFormat.GRAND_CHIFFRE, select(172f, 192f, rail = false, single = true))
        assertEquals(WidgetFormat.TRAIN, select(172f, 192f, rail = true, single = true))
        assertEquals(WidgetFormat.DEUX_SENS, select(172f, 192f, rail = false, single = false))
        assertEquals(WidgetFormat.DEUX_SENS, select(172f, 192f, rail = true, single = false))
        assertEquals(WidgetFormat.FRISE, select(348f, 92f, rail = false, single = true))
        assertEquals(WidgetFormat.COMPACT, select(348f, 92f, rail = false, single = false))
        assertEquals(WidgetFormat.DEUX_SENS_LARGE, select(348f, 192f, rail = false, single = false))
        assertEquals(WidgetFormat.LISTE, select(348f, 192f, rail = true, single = false))
        assertEquals(WidgetFormat.LISTE, select(348f, 192f, rail = false, single = true))
        assertEquals(WidgetFormat.LISTE, select(348f, 292f, rail = false, single = false))
    }

    @Test
    fun eachWidgetEntryImposesItsFormat() {
        assertEquals(WidgetFormat.PASTILLE, WidgetProviders.formatOf(PastilleWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.UN_SENS, WidgetProviders.formatOf(UnSensWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.GRAND_CHIFFRE, WidgetProviders.formatOf(GrandChiffreWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.FRISE, WidgetProviders.formatOf(FriseWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.COMPACT, WidgetProviders.formatOf(CompactWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.DEUX_SENS, WidgetProviders.formatOf(DeuxSensWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.DEUX_SENS_LARGE, WidgetProviders.formatOf(DeuxSensLargeWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.TRAIN, WidgetProviders.formatOf(TrainWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.LISTE, WidgetProviders.formatOf(ListeWidgetProvider::class.java.name))
        // L'entrée automatique, et les widgets déjà posés avec elle, n'imposent aucun format.
        assertNull(WidgetProviders.formatOf(DeparturesWidgetProvider::class.java.name))
        assertNull(WidgetProviders.formatOf(null))
    }

    @Test
    fun catalogOffersEveryFormatOnceAndTheAutomaticEntry() {
        val catalog = WidgetProviders.catalog
        assertEquals(WidgetFormat.entries.toSet(), catalog.mapNotNull { it.format }.toSet())
        assertEquals(WidgetFormat.entries.size + 1, catalog.size)
        assertEquals(catalog.size, catalog.map { it.provider }.toSet().size)
        assertEquals(DeparturesWidgetProvider::class.java, catalog.single { it.format == null }.provider)
    }

    @Test
    fun chosenFormatWinsOverSizeAndDirection() {
        val chosen = WidgetModels.build(
            config(metro, oneDirection), state(dep("A", 5)), hasApiKey = true, refreshing = false,
            widthDp = 348f, heightDp = 192f, now = now, fixedFormat = WidgetFormat.PASTILLE,
        )
        assertEquals(WidgetFormat.PASTILLE, chosen.format)
        assertTrue("La couleur automatique suit le format choisi", chosen.palette.isLine)

        val automatic = WidgetModels.build(
            config(metro, oneDirection), state(dep("A", 5)), hasApiKey = true, refreshing = false,
            widthDp = 84f, heightDp = 92f, now = now,
        )
        assertEquals(WidgetFormat.PASTILLE, automatic.format)
    }

    @Test
    fun twoDirectionFormatsAreIdentified() {
        val both = WidgetFormat.entries.filter { it.showsBothDirections }
        assertEquals(listOf(WidgetFormat.COMPACT, WidgetFormat.DEUX_SENS, WidgetFormat.DEUX_SENS_LARGE), both)
    }

    @Test
    fun oneDirectionFormatsAreIdentified() {
        val one = WidgetFormat.entries.filter { it.followsOneDirection }
        assertEquals(
            listOf(WidgetFormat.PASTILLE, WidgetFormat.UN_SENS, WidgetFormat.GRAND_CHIFFRE, WidgetFormat.FRISE, WidgetFormat.TRAIN),
            one,
        )
        // Seule la liste accepte les deux usages : elle indique la destination de chaque passage.
        val neither = WidgetFormat.entries.filter { !it.followsOneDirection && !it.showsBothDirections }
        assertEquals(listOf(WidgetFormat.LISTE), neither)
    }

    @Test
    fun singleDirectionDetection() {
        assertTrue(config(metro, oneDirection).isSingleDirection)
        assertFalse(config(metro, allDirections).isSingleDirection)
        // Un seul quai retenu parmi deux : un seul sens, même sans filtre de destination.
        assertTrue(config(metro, mapOf("IDFM:1" to emptySet<String>())).isSingleDirection)
        // Plusieurs destinations cochées (branches du RER).
        assertFalse(config(rer, mapOf("IDFM:1" to setOf("Boissy-Saint-Léger", "Marne-la-Vallée"))).isSingleDirection)
    }

    // --- Passages ----------------------------------------------------------------------------

    @Test
    fun waitKinds() {
        fun kind(d: Departure, mode: String = "metro") = WidgetContent.passage(d, now, mode).kind

        assertEquals(WaitKind.MINUTES, kind(dep("A", 5)))
        assertEquals(WaitKind.CLOCK, kind(dep("A", 75)))
        assertEquals(WaitKind.IMMINENT, kind(dep("A", 0)))
        assertEquals(WaitKind.AT_STOP, kind(dep("A", 1, atStop = true)))
        assertEquals(WaitKind.CANCELLED, kind(dep("A", 1, atStop = true, status = "cancelled")))
    }

    @Test
    fun passageDetailsDependOnMode() {
        val train = WidgetContent.passage(dep("Boissy", 2, platform = "1", mission = "NOTE"), now, "rail")
        assertEquals("Voie", train.platformLabel)
        assertEquals("NOTE", train.mission)
        assertEquals("À quai", train.atStopLabel)

        val bus = WidgetContent.passage(dep("Vincennes", 2, platform = "B", mission = "XXXX"), now, "bus")
        assertEquals("Quai", bus.platformLabel)
        assertNull(bus.mission)
        assertEquals("À l'arrêt", bus.atStopLabel)
    }

    @Test
    fun delayIsFlaggedFromTwoMinutes() {
        assertFalse(WidgetContent.passage(dep("A", 5, delay = 1), now, "rail").isDelayed)
        assertTrue(WidgetContent.passage(dep("A", 5, delay = 3), now, "rail").isDelayed)
        assertFalse(WidgetContent.passage(dep("A", 5, delay = 3, status = "cancelled"), now, "rail").isDelayed)
    }

    @Test
    fun thenLabels() {
        val five = WidgetContent.passage(dep("A", 5), now, "metro")
        assertEquals("puis 5", five.thenLabel())
        assertEquals("puis 5 min", five.thenLabel(withUnit = true))
        assertEquals("puis <1", WidgetContent.passage(dep("A", 0), now, "metro").thenLabel())
    }

    @Test
    fun thenLabelKeepsOnlyTheMinutesWhenSpaceIsShort() {
        val next = WidgetContent.passage(dep("A", 36), now, "metro")
        assertEquals("puis 36", next.thenLabelFitting { it.length <= 7 })
        assertEquals("36", next.thenLabelFitting { it.length <= 2 })
        assertNull(next.thenLabelFitting { it.length <= 1 })
    }

    // --- Sens --------------------------------------------------------------------------------

    private fun passages(vararg departures: Departure) = departures.map { WidgetContent.passage(it, now, "metro") }

    @Test
    fun groupsKeepTwoDirectionsInStableOrder() {
        val soonVincennes = passages(
            dep("La Défense (Grande Arche)", 2),
            dep("Château de Vincennes", 1),
            dep("Château de Vincennes", 5),
        )
        val soonDefense = passages(
            dep("Château de Vincennes", 2),
            dep("La Défense (Grande Arche)", 1),
        )
        val labels = listOf("Château de Vincennes", "La Défense")
        assertEquals(labels, WidgetContent.directionGroups(soonVincennes).map { it.label })
        assertEquals(labels, WidgetContent.directionGroups(soonDefense).map { it.label })
    }

    @Test
    fun severalDestinationsOnTwoPlatformsMakeTwoDirections() {
        val groups = WidgetContent.directionGroups(
            passages(
                dep("Boissy-Saint-Léger", 2, stopId = "E"),
                dep("Nanterre Préfecture", 2, stopId = "W"),
                dep("Poissy", 16, stopId = "W"),
                dep("Marne-la-Vallée", 3, stopId = "E"),
            ),
        )
        assertEquals(2, groups.size)
        assertEquals(setOf("E", "W"), groups.map { it.first.stopId }.toSet())
    }

    @Test
    fun nextPassageStaysInDirectionAndSkipsCancelled() {
        val list = passages(
            dep("Vincennes", 1),
            dep("La Défense", 2),
            dep("Vincennes", 4, status = "cancelled"),
            dep("Vincennes", 6),
        )
        assertEquals(6, WidgetContent.nextPassage(list, list.first())?.minutes)
    }

    @Test
    fun railKeepsFourStopsWithinTheHour() {
        val list = passages(
            dep("A", 1), dep("A", 5), dep("A", 8), dep("A", 11), dep("A", 14),
        )
        assertEquals(listOf(1, 5, 8, 11), WidgetContent.railPassages(list, list.first()).map { it.minutes })

        val far = passages(dep("A", 40), dep("A", 90))
        assertEquals(listOf(40), WidgetContent.railPassages(far, far.first()).map { it.minutes })
    }

    @Test
    fun railCentersMatchTheDesign() {
        assertEquals(
            listOf(74f, 170f, 242f, 314f),
            WidgetContent.railCenters(listOf(1, 5, 8, 11), 74f, 314f, 10, 28f),
        )
    }

    @Test
    fun railCentersKeepLabelsApartAndDropWhatDoesNotFit() {
        // Passages très rapprochés : les points sont espacés, ceux qui débordent disparaissent.
        val centers = WidgetContent.railCenters(listOf(0, 1, 2, 30), 0f, 60f, 30, 28f)
        assertEquals(listOf(0f, 28f, 56f), centers)
        // Une seule valeur : pas de division par zéro.
        assertEquals(listOf(10f), WidgetContent.railCenters(listOf(5), 10f, 100f, 0, 28f))
        // Le deuxième point ne touche pas le grand chiffre.
        assertEquals(listOf(74f, 120f), WidgetContent.railCenters(listOf(1, 2), 74f, 314f, 100, 28f, 120f))
    }

    // --- Noms --------------------------------------------------------------------------------

    @Test
    fun shortNameRemovesParenthesis() {
        assertEquals("La Défense", WidgetContent.shortName("La Défense (Grande Arche)"))
        assertEquals("Poissy", WidgetContent.shortName("Poissy"))
    }

    @Test
    fun compactNamePicksFirstVersionThatFits() {
        fun compact(name: String, max: Int) = WidgetContent.compactName(name) { it.length <= max }

        assertEquals("Poissy", compact("Poissy", 10))
        assertEquals("La Défense", compact("La Défense (Grande Arche)", 10))
        assertEquals("Vincennes", compact("Château de Vincennes", 10))
        assertEquals("Boissy", compact("Boissy-Saint-Léger", 10))
        assertEquals("Nanterre", compact("Nanterre Préfecture", 10))
        assertEquals("Clignancourt", compact("Porte de Clignancourt", 12))
    }

    @Test
    fun ellipsizeAndWrap() {
        val width: (String) -> Float = { it.length.toFloat() }
        assertEquals("Poissy", WidgetContent.ellipsize("Poissy", 10f, width))
        assertEquals("Boissy…", WidgetContent.ellipsize("Boissy-Saint-Léger", 7f, width))
        assertEquals(
            listOf("Aucun passage", "prévu pour le", "moment"),
            WidgetContent.wrapLines("Aucun passage prévu pour le moment", 13f, 3, width),
        )
        assertEquals(
            listOf("Aucun passage", "prévu pour l…"),
            WidgetContent.wrapLines("Aucun passage prévu pour le moment", 13f, 2, width),
        )
    }

    // --- Thèmes ------------------------------------------------------------------------------

    @Test
    fun autoThemeUsesLineColorOnBigNumberFormatsOnly() {
        fun isLine(format: WidgetFormat, theme: WidgetTheme = WidgetTheme.AUTO) = Palettes.resolve(theme, format, metro).isLine

        assertTrue(isLine(WidgetFormat.PASTILLE))
        assertTrue(isLine(WidgetFormat.GRAND_CHIFFRE))
        assertTrue(isLine(WidgetFormat.DEUX_SENS_LARGE))
        assertFalse(isLine(WidgetFormat.UN_SENS))
        assertFalse(isLine(WidgetFormat.FRISE))
        assertFalse(isLine(WidgetFormat.COMPACT))
        assertFalse(isLine(WidgetFormat.DEUX_SENS))
        assertFalse(isLine(WidgetFormat.TRAIN))
        assertFalse(isLine(WidgetFormat.LISTE))

        assertFalse(isLine(WidgetFormat.PASTILLE, WidgetTheme.DARK))
        assertTrue(isLine(WidgetFormat.FRISE, WidgetTheme.LINE))
        assertFalse("La liste n'a pas de thème couleur de ligne", isLine(WidgetFormat.LISTE, WidgetTheme.LINE))
    }

    @Test
    fun lineColorIsDarkenedWhenSmallTextWouldBeUnreadable() {
        val palette = Palettes.resolve(WidgetTheme.LINE, WidgetFormat.PASTILLE, rer)
        assertTrue(Palettes.contrast(palette.background, palette.text) >= 4.5)
    }

    @Test
    fun unreadableTextColorFromReferentialIsReplaced() {
        val yellow = Palettes.parseColor("FFBE00")!!
        val on = Palettes.onColor(yellow, Palettes.parseColor("FFFFFF"))
        assertTrue(Palettes.contrast(yellow, on) >= 4.5)
    }

    @Test
    fun parseColorAcceptsReferentialFormats() {
        assertEquals(0xFFEB2132.toInt(), Palettes.parseColor("EB2132"))
        assertEquals(0xFFEB2132.toInt(), Palettes.parseColor("#EB2132"))
        assertNull(Palettes.parseColor("rouge"))
        assertNull(Palettes.parseColor(null))
    }

    // --- Modèle ------------------------------------------------------------------------------

    private fun state(vararg departures: Departure, error: String? = null) =
        WidgetState(fetchedAt = now - 60_000L, attemptedAt = now, departures = departures.toList(), error = error)

    private fun model(
        config: WidgetConfig?,
        state: WidgetState?,
        hasApiKey: Boolean = true,
        width: Float = 348f,
        height: Float = 192f,
    ) = WidgetModels.build(config, state, hasApiKey, refreshing = false, widthDp = width, heightDp = height, now = now)

    @Test
    fun noticesWhenThereIsNothingToShow() {
        assertEquals(NoticeKind.NOT_CONFIGURED, model(null, null).notice?.kind)
        assertEquals(NoticeKind.MISSING_KEY, model(config(metro, allDirections), state(dep("A", 2)), hasApiKey = false).notice?.kind)
        assertEquals(NoticeKind.LOADING, model(config(metro, allDirections), null).notice?.kind)
        assertEquals(NoticeKind.NO_DEPARTURE, model(config(metro, allDirections), state()).notice?.kind)
        assertEquals(NoticeKind.ERROR, model(config(metro, allDirections), state(error = "Panne")).notice?.kind)
    }

    @Test
    fun failedRefreshKeepsOldPassagesAndFlagsThem() {
        val result = model(config(metro, allDirections), state(dep("A", 5), error = "Panne"))
        assertNull(result.notice)
        assertTrue(result.stale)
        assertEquals(1, result.passages.size)
    }

    @Test
    fun refreshingReplacesTheTime() {
        val result = WidgetModels.build(
            config(metro, allDirections), state(dep("A", 5)), hasApiKey = true, refreshing = true,
            widthDp = 348f, heightDp = 192f, now = now,
        )
        assertEquals("…", result.timeLabel)
    }

    @Test
    fun badgeShapesFollowTheMode() {
        fun badge(line: Line) = model(config(line, oneDirection), state()).line

        assertEquals(LineBadge("1", BadgeShape.CIRCLE), badge(metro))
        assertEquals(LineBadge("A", BadgeShape.CIRCLE), badge(rer))
        assertEquals(LineBadge("T3a", BadgeShape.TRAM), badge(tram))
        assertEquals(LineBadge("P", BadgeShape.SQUARE), badge(transilien))
        assertEquals(
            LineBadge("124", BadgeShape.BUS),
            badge(Line("C1", "124", "124", "bus")),
        )
    }

    @Test
    fun descriptionForScreenReaders() {
        val result = model(
            config(metro, allDirections),
            state(dep("Château de Vincennes", 1), dep("La Défense (Grande Arche)", 2), dep("Château de Vincennes", 5)),
        )
        assertEquals(
            "Ligne 1, Bastille. Vers Château de Vincennes : 1 minute, puis 5 minutes. Vers La Défense : 2 minutes.",
            WidgetContent.describe(result),
        )
    }
}
