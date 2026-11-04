package fr.trprim.siri.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferentialTest {

    @Test
    fun parsesExportedLines() {
        val body = """
            [
             {"id_line":"C01383","name_line":"14","shortname_line":"14","transportmode":"metro","networkname":"Métro","operatorname":"RATP","colourweb_hexa":"662483","textcolourweb_hexa":"FFFFFF","status":"active"},
             {"id_line":"C01372","name_line":"2","shortname_line":"2","transportmode":"metro","operatorname":"RATP","colourweb_hexa":"#3C91DC"},
             {"id_line":"C01386","name_line":"3B","shortname_line":"3B","transportmode":"metro","colourweb_hexa":"6EC4E8"},
             {"id_line":"C01373","name_line":"3","shortname_line":"3","transportmode":"metro","colourweb_hexa":"invalid"},
             {"id_line":"C09999","name_line":"Old","transportmode":"metro","status":"inactive"}
            ]
        """.trimIndent()
        val records = Referential.parseRecords(body)
        assertNotNull(records)
        val lines = Referential.parseLines(records!!)
        assertEquals(listOf("2", "3", "3B", "14"), lines.map { it.shortName })
        assertEquals("3C91DC", lines[0].color)
        assertEquals(null, lines[1].color)
        assertEquals("Métro · RATP", lines[3].subtitle())
    }

    @Test
    fun parsesRecordsAndLegacyFormats() {
        val v21 = """{"total_count":1,"results":[{"id_line":"C01742","name_line":"A","transportmode":"rail","networkname":"RER"}]}"""
        val v1 = """[{"datasetid":"x","fields":{"id_line":"C01743","name_line":"B","transportmode":"rail"}}]"""
        val a = Referential.parseLines(Referential.parseRecords(v21)!!)
        val b = Referential.parseLines(Referential.parseRecords(v1)!!)
        assertEquals("C01742", a.single().id)
        assertTrue(a.single().isRer())
        assertEquals("C01743", b.single().id)
        assertTrue("RER B détecté par son indice", b.single().isRer())
    }

    @Test
    fun railModesAreSplit() {
        val rer = Line("C01742", "A", "A", "rail", network = "RER")
        val transilien = Line("C01739", "H", "H", "rail", network = "Transilien")
        assertTrue(TransportMode.RER.accepts(rer))
        assertFalse(TransportMode.RER.accepts(transilien))
        assertTrue(TransportMode.TRAIN.accepts(transilien))
        assertFalse(TransportMode.TRAIN.accepts(rer))
    }

    @Test
    fun mergesStopsByNameAndTown() {
        val body = """
            [
             {"id":"IDFM:C01374","stop_id":"IDFM:22092","stop_name":"Châtelet","nom_commune":"Paris"},
             {"id":"IDFM:C01374","stop_id":"IDFM:463158","stop_name":"Châtelet","nom_commune":"Paris"},
             {"id":"IDFM:C01374","stop_id":"IDFM:22100","stop_name":"Barbès - Rochechouart","nom_commune":"Paris"},
             {"id":"IDFM:C01374","stop_id":"IDFM:22100","stop_name":"Barbès - Rochechouart","nom_commune":"Paris"},
             {"id":"IDFM:C01374","stop_id":"IDFM:9","stop_name":"Mairie","nom_commune":"Montrouge"},
             {"id":"IDFM:C01374","stop_id":"IDFM:10","stop_name":"Mairie","nom_commune":"Bagneux"}
            ]
        """.trimIndent()
        val stops = Referential.parseStops(Referential.parseRecords(body)!!)
        assertEquals(listOf("Barbès - Rochechouart", "Châtelet", "Mairie", "Mairie"), stops.map { it.name })
        assertEquals(listOf("IDFM:22100"), stops[0].stopIds)
        assertEquals(listOf("IDFM:22092", "IDFM:463158"), stops[1].stopIds)
        assertEquals(listOf("Bagneux", "Montrouge"), stops.drop(2).map { it.town })
    }

    @Test
    fun buildsOpendatasoftQueries() {
        assertEquals("`transportmode`=\"cableway\" OR `transportmode`=\"funicular\"", Referential.linesWhere(listOf("cableway", "funicular")))
        assertEquals("`id`=\"IDFM:C01742\"", Referential.stopsWhere("C01742"))
        assertEquals("`id`=\"IDFM:C01742\"", Referential.stopsWhere("IDFM:C01742"))
        assertTrue(Referential.exportUrl("arrets-lignes", "x").startsWith(
            "https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/arrets-lignes/exports/json?where=x"))
    }

    @Test
    fun monitoringRefsFromReferentialIds() {
        assertEquals(
            listOf("STIF:StopArea:SP:473921:", "STIF:StopPoint:Q:473921:"),
            PrimClient.monitoringRefs("IDFM:monomodalStopPlace:473921"),
        )
        assertEquals(listOf("STIF:StopPoint:Q:22092:"), PrimClient.monitoringRefs("IDFM:22092"))
        assertEquals(listOf("STIF:StopPoint:Q:22092:"), PrimClient.monitoringRefs("STIF:StopPoint:Q:22092:"))
    }

    @Test
    fun lineRefMatching() {
        assertTrue(PrimClient.lineMatches("STIF:Line::C01742:", "C01742"))
        assertTrue(PrimClient.lineMatches(null, "C01742"))
        assertFalse(PrimClient.lineMatches("STIF:Line::C01743:", "C01742"))
    }

    @Test
    fun naturalSort() {
        val sorted = listOf("N12", "10", "2", "T3b", "T3a", "1").sortedWith(Referential.naturalComparator)
        assertEquals(listOf("1", "2", "10", "N12", "T3a", "T3b"), sorted)
    }
}
