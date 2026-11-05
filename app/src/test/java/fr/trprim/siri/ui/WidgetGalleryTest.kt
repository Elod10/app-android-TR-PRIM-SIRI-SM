package fr.trprim.siri.ui

import fr.trprim.siri.data.WidgetTheme
import fr.trprim.siri.widget.WidgetFormat
import fr.trprim.siri.widget.WidgetProviders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGalleryTest {

    @Test
    fun theGalleryOffersEachWidgetOnce() {
        val shown = WidgetGallery.sections.flatMap { it.entries } + WidgetGallery.adaptive
        assertEquals(WidgetProviders.catalog.size, shown.size)
        assertEquals(WidgetProviders.catalog.toSet(), shown.toSet())
    }

    @Test
    fun theLineColorIsOfferedWhenTheFormatCanCarryIt() {
        assertTrue(WidgetTheme.LINE in ThemeChoices.available(WidgetFormat.DEUX_SENS_LARGE))
        assertTrue(WidgetTheme.LINE in ThemeChoices.available(null))
        assertFalse(WidgetTheme.LINE in ThemeChoices.available(WidgetFormat.LISTE))
    }

    @Test
    fun aNewWidgetStartsInTheLineColor() {
        assertEquals(WidgetTheme.LINE, ThemeChoices.initial(null, WidgetFormat.PASTILLE))
        assertEquals(WidgetTheme.LINE, ThemeChoices.initial(null, null))
    }

    @Test
    fun aWidgetKeepsItsColorIfItIsStillOffered() {
        assertEquals(WidgetTheme.LIGHT, ThemeChoices.initial(WidgetTheme.LIGHT, WidgetFormat.PASTILLE))
        assertEquals(WidgetTheme.DARK, ThemeChoices.initial(WidgetTheme.LINE, WidgetFormat.LISTE))
    }

    @Test
    fun rowsOfAGroupShareOneCard() {
        assertEquals(RowStyle.CARD, RowStyle.inGroup(0, 1))
        assertEquals(RowStyle.GROUP_TOP, RowStyle.inGroup(0, 3))
        assertEquals(RowStyle.GROUP_MIDDLE, RowStyle.inGroup(1, 3))
        assertEquals(RowStyle.GROUP_BOTTOM, RowStyle.inGroup(2, 3))
    }
}
