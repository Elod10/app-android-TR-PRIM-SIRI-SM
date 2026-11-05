package fr.trprim.siri.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde les mentions exigées par la Licence Mobilités (art. 5.4) et l'ODbL (section 4.3) : le nom de la
 * base avec son lien, et la licence avec le lien vers son texte.
 */
class LegalMentionsTest {

    private companion object {
        const val LICENCE_MOBILITES = "https://cloud.fabmob.io/s/eYWWJBdM3fQiFNm"
        const val ODBL = "https://opendatacommons.org/licenses/odbl/1-0/"
        const val TRACEO = "https://gotraceo.fr"
    }

    private val strings = File("src/main/res/values/strings.xml").readText()

    private fun string(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)?.groupValues?.get(1)
            ?: throw AssertionError("Texte $name introuvable")

    private fun assertLinksTo(text: String, url: String) =
        assertTrue("Lien manquant : $url", text.contains("""href="$url""""))

    @Test
    fun withoutKeyTheMentionSaysTraceoCachesPrimData() {
        val about = string("main_about_traceo")
        assertTrue(about.contains("Île-de-France Mobilités"))
        assertTrue(about.contains("interroge l\\'API PRIM"))
        assertTrue(about.contains("en cache"))
        assertLinksTo(about, TRACEO)
        assertLinksTo(about, "https://prim.iledefrance-mobilites.fr/")
        assertLinksTo(about, LICENCE_MOBILITES)
        assertLinksTo(about, ODBL)
    }

    @Test
    fun withKeyTheMentionNamesEachDatasetAndItsLicence() {
        val about = string("main_about_prim")
        assertTrue(about.contains("Île-de-France Mobilités"))
        assertLinksTo(about, "https://prim.iledefrance-mobilites.fr/fr/apis/idfm-ivtr-requete_unitaire")
        assertLinksTo(about, "https://data.iledefrance-mobilites.fr/explore/dataset/referentiel-des-lignes/")
        assertLinksTo(about, "https://data.iledefrance-mobilites.fr/explore/dataset/arrets-lignes/")
        assertLinksTo(about, LICENCE_MOBILITES)
        assertLinksTo(about, ODBL)
    }

    @Test
    fun bothMentionsNameTheDatasetsOfTheStopIndex() {
        for (name in listOf("main_about_traceo", "main_about_prim")) {
            val about = string(name)
            assertLinksTo(about, "https://data.iledefrance-mobilites.fr/explore/dataset/arrets-lignes/")
            assertLinksTo(about, "https://data.iledefrance-mobilites.fr/explore/dataset/arrets/")
            assertLinksTo(about, "https://data.iledefrance-mobilites.fr/explore/dataset/zones-d-arrets/")
            assertLinksTo(about, "https://data.iledefrance-mobilites.fr/explore/dataset/referentiel-des-lignes/")
        }
    }

    @Test
    fun sourceLinesShowThatTheDataAreAlwaysFromPrim() {
        assertTrue(string("main_source_traceo").contains("PRIM via le cache de Tracéo"))
        assertTrue(string("main_source_prim").contains("PRIM en direct"))
        assertTrue(string("main_key_help").contains("met ses réponses en cache"))
    }

    @Test
    fun oneMinuteIsNoLongerPromisedWithoutKey() {
        assertFalse(string("main_refresh_help").contains("« Chaque minute »"))
    }
}
