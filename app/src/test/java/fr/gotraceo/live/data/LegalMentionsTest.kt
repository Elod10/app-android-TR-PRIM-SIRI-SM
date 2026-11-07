package fr.gotraceo.live.data

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
        const val LICENCE_OUVERTE = "https://www.etalab.gouv.fr/licence-ouverte-open-licence"
        const val TRACEO = "https://gotraceo.fr"
        const val DATASETS = "https://data.iledefrance-mobilites.fr/explore/dataset"
    }

    private val strings = File("src/main/res/values/strings.xml").readText()

    private fun string(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)?.groupValues?.get(1)
            ?: throw AssertionError("Texte $name introuvable")

    private fun assertLinksTo(text: String, url: String) =
        assertTrue("Lien manquant : $url", text.contains("""href="$url""""))

    /** Le paragraphe qui cite ce lien : une licence doit y suivre le jeu de données qu'elle couvre. */
    private fun paragraphWith(text: String, url: String): String =
        text.split("\\n\\n").single { it.contains("""href="$url"""") }

    @Test
    fun eachDatasetOfTheStopIndexShowsItsOwnLicence() {
        for (name in listOf("main_about_traceo", "main_about_prim")) {
            val about = string(name)
            for (dataset in listOf("arrets", "zones-d-arrets")) {
                val paragraph = paragraphWith(about, "$DATASETS/$dataset/")
                assertLinksTo(paragraph, LICENCE_OUVERTE)
                assertFalse(paragraph.contains(ODBL))
            }
            for (dataset in listOf("arrets-lignes", "referentiel-des-lignes")) {
                val paragraph = paragraphWith(about, "$DATASETS/$dataset/")
                assertLinksTo(paragraph, ODBL)
                assertFalse(paragraph.contains(LICENCE_OUVERTE))
            }
        }
    }

    @Test
    fun theAttributionsOfTheStopsDatasetAreShown() {
        for (name in listOf("main_about_traceo", "main_about_prim")) {
            val paragraph = paragraphWith(string(name), "$DATASETS/arrets-lignes/")
            assertTrue(paragraph.contains("INSEE, IGN, NaturalEarth, DGCL - BANATIC, MEFR, MAA"))
        }
    }

    @Test
    fun theSourcesScreenGivesTheDateRequiredByTheOpenLicence() {
        assertTrue(string("sources_index_date").contains("%1\$s"))
        val code = File("src/main/java/fr/gotraceo/live/ui/SourcesActivity.kt").readText()
        assertTrue(code.contains("R.string.sources_index_date"))
    }

    @Test
    fun theShortMentionNamesTheDatabaseAndItsLicences() {
        val mention = string("data_mention")
        assertLinksTo(mention, "https://prim.iledefrance-mobilites.fr/")
        assertLinksTo(mention, LICENCE_MOBILITES)
        assertLinksTo(mention, ODBL)
    }

    @Test
    fun theShortMentionIsOnEveryScreenShowingData() {
        val screens = mapOf(
            "activity_main" to "MainActivity",
            "activity_configure" to "ConfigureActivity",
            "activity_nearby_choice" to "NearbyChoiceActivity",
        )
        for ((layout, activity) in screens) {
            val xml = File("src/main/res/layout/$layout.xml").readText()
            assertTrue("Mention absente de $layout", xml.contains("@layout/view_data_mention"))
            // Sans méthode de déplacement, les liens de la mention ne réagissent pas au toucher.
            val code = File("src/main/java/fr/gotraceo/live/ui/$activity.kt").readText()
            assertTrue("Liens inactifs dans $activity", code.contains("R.id.data_mention).movementMethod"))
        }
    }

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
    fun theAppSaysItIsNotAffiliatedWithIdfm() {
        assertTrue(string("sources_not_affiliated").contains("pas affiliée à Île-de-France Mobilités"))
    }

    @Test
    fun oneMinuteIsNoLongerPromisedWithoutKey() {
        assertFalse(string("main_refresh_help").contains("« Chaque minute »"))
    }
}
