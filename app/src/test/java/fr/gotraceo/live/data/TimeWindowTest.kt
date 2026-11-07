package fr.gotraceo.live.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeWindowTest {

    private val paris = ZoneId.of("Europe/Paris")

    /** Le 3 novembre 2026 est un mardi. */
    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, paris)

    private val tuesdayAfternoon = TimeWindow(setOf(DayOfWeek.TUESDAY), 16 * 60, 16 * 60 + 30)

    @Test
    fun aWindowCoversItsDaysFromStartToEnd() {
        assertFalse(tuesdayAfternoon.isActive(at(3, 15, 59)))
        assertTrue(tuesdayAfternoon.isActive(at(3, 16, 0)))
        assertTrue(tuesdayAfternoon.isActive(at(3, 16, 29)))
        // La fin est exclue : à 16:30, la plage est finie.
        assertFalse(tuesdayAfternoon.isActive(at(3, 16, 30)))
        // Le mercredi n'est pas choisi.
        assertFalse(tuesdayAfternoon.isActive(at(4, 16, 10)))
    }

    @Test
    fun aWindowEndingBeforeItStartsGoesPastMidnight() {
        val night = TimeWindow(setOf(DayOfWeek.FRIDAY), 23 * 60, 60)
        assertTrue(night.crossesMidnight)
        assertTrue(night.isActive(at(6, 23, 30)))
        // Le samedi matin appartient à la plage du vendredi.
        assertTrue(night.isActive(at(7, 0, 45)))
        assertFalse(night.isActive(at(7, 1, 0)))
        // Le samedi soir n'est pas choisi.
        assertFalse(night.isActive(at(7, 23, 30)))
        // Le vendredi matin non plus : la plage du jeudi n'existe pas.
        assertFalse(night.isActive(at(6, 0, 30)))
    }

    @Test
    fun theCurrentEndIsWhereADismissedNotificationComesBack() {
        assertEquals(at(3, 16, 30), tuesdayAfternoon.currentEnd(at(3, 16, 5)))
        assertNull(tuesdayAfternoon.currentEnd(at(3, 17, 0)))
        val night = TimeWindow(setOf(DayOfWeek.FRIDAY), 23 * 60, 60)
        assertEquals(at(7, 1, 0), night.currentEnd(at(6, 23, 30)))
        assertEquals(at(7, 1, 0), night.currentEnd(at(7, 0, 15)))
    }

    @Test
    fun labelsReadNaturally() {
        assertEquals("Mardi · 16:00 – 16:30", tuesdayAfternoon.label())
        assertEquals("En semaine · 16:00 – 16:30", TimeWindow.DEFAULT.label())
        assertEquals("Le week-end · 09:00 – 10:00", TimeWindow(setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY), 540, 600).label())
        assertEquals("Tous les jours · 07:45 – 08:15", TimeWindow(DayOfWeek.entries.toSet(), 465, 495).label())
        assertEquals(
            "Lun., mer., ven. · 07:45 – 08:15",
            TimeWindow(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 465, 495).label(),
        )
        assertEquals("Mardi et jeudi · 07:45 – 08:15", TimeWindow(setOf(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY), 465, 495).label())
    }

    @Test
    fun aWindowSurvivesStorage() {
        val restored = TimeWindow.fromJson(JSONObject(tuesdayAfternoon.toJson().toString()))
        assertEquals(tuesdayAfternoon, restored)
        assertNull(TimeWindow.fromJson(JSONObject("""{"days":[],"start":0,"end":60}""")))
        assertNull(TimeWindow.fromJson(JSONObject("""{"days":["MONDAY"],"start":2000,"end":60}""")))
    }
}
