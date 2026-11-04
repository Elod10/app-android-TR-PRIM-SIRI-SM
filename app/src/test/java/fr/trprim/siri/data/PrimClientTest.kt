package fr.trprim.siri.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import kotlin.concurrent.thread

/** Teste la logique de repli de [PrimClient] contre un faux serveur PRIM local. */
class PrimClientTest {

    private lateinit var server: ServerSocket
    private val requests: MutableList<String> = Collections.synchronizedList(ArrayList())
    @Volatile private var forcedStatus: Int? = null

    private fun siriError(text: String) =
        """{"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"Status":"false","ErrorCondition":{"ErrorInformation":{"ErrorText":"$text"}}}]}}}"""

    private fun visit(line: String, destination: String) =
        """{"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::$line:"},"DestinationName":[{"value":"$destination"}],
           "MonitoredCall":{"ExpectedArrivalTime":"2030-01-01T10:00:00.000Z"}}}"""

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                socket.use { handle(it) }
            }
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Mini serveur HTTP/1.1 (une requête par connexion) simulant l'API PRIM. */
    private fun handle(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val target = requestLine.split(' ').getOrElse(1) { "" }
        val query = URLDecoder.decode(target.substringAfter('?', ""), "UTF-8")
        requests += query

        val (status, body) = when {
            forcedStatus != null -> forcedStatus!! to """{"message":"error"}"""
            headers["apikey"] != "secret" -> 401 to """{"message":"Invalid authentication credentials"}"""
            query.contains("StopArea:SP:1:") -> 400 to siriError("La requête contient des identifiants qui sont inconnus")
            query.contains("StopPoint:Q:1:") && query.contains("LineRef") ->
                400 to siriError("Le couple MonitoringRef/LineRef n'existe pas")
            query.contains("StopPoint:Q:1:") -> 200 to
                """{"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"Status":"true","MonitoredStopVisit":[
                    ${visit("C01742", "Boissy-Saint-Léger")}, ${visit("C01743", "Robinson")}]}]}}}"""
            query.contains("StopPoint:Q:2:") -> 200 to
                """{"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"Status":"true","MonitoredStopVisit":[]}]}}}"""
            else -> 400 to siriError("La requête contient des identifiants qui sont inconnus")
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 $status X\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8),
        )
        out.write(bytes)
        out.flush()
    }

    private fun client(key: String = "secret", formats: PrimClient.FormatCache = PrimClient.FormatCache.InMemory()) =
        PrimClient(key, "http://127.0.0.1:${server.localPort}/stop-monitoring", formats)

    @Test
    fun remembersAcceptedFormat() {
        val formats = PrimClient.FormatCache.InMemory()
        client(formats = formats).departures("IDFM:monomodalStopPlace:1", "C01742")
        assertEquals(4, requests.size)

        requests.clear()
        val again = client(formats = formats).departures("IDFM:monomodalStopPlace:1", "C01742")
        assertEquals(listOf("Boissy-Saint-Léger"), again.map { it.destination })
        assertEquals("Une seule requête grâce au format mémorisé", listOf("MonitoringRef=STIF:StopPoint:Q:1:"), requests)
    }

    @Test
    fun forgetsFormatThatStoppedWorking() {
        val formats = PrimClient.FormatCache.InMemory()
        val key = "IDFM:monomodalStopPlace:1|C01742"
        formats.put(key, "STIF:StopArea:SP:1:|")
        client(formats = formats).departures("IDFM:monomodalStopPlace:1", "C01742")
        assertEquals("MonitoringRef=STIF:StopArea:SP:1:", requests.first())
        assertEquals("STIF:StopPoint:Q:1:|", formats.get(key))
    }

    @Test
    fun fallsBackToStopPointThenWithoutLineRef() {
        val departures = client().departures("IDFM:monomodalStopPlace:1", "C01742")
        assertEquals(listOf("Boissy-Saint-Léger"), departures.map { it.destination })
        assertEquals("IDFM:monomodalStopPlace:1", departures.single().stopId)
        assertEquals(
            listOf(
                "MonitoringRef=STIF:StopArea:SP:1:&LineRef=STIF:Line::C01742:",
                "MonitoringRef=STIF:StopArea:SP:1:",
                "MonitoringRef=STIF:StopPoint:Q:1:&LineRef=STIF:Line::C01742:",
                "MonitoringRef=STIF:StopPoint:Q:1:",
            ),
            requests,
        )
    }

    @Test
    fun emptyDeliveryMeansNoDeparture() {
        assertTrue(client().departures("IDFM:2", "C01742").isEmpty())
        assertEquals(1, requests.size)
    }

    @Test
    fun rejectedKey() {
        expectKind(PrimException.Kind.AUTH) { client("wrong").departures("IDFM:2", "C01742") }
        expectKind(PrimException.Kind.AUTH) { client("wrong").testKey() }
        client().testKey()
    }

    @Test
    fun quotaAndServerErrors() {
        forcedStatus = 429
        expectKind(PrimException.Kind.QUOTA) { client().departures("IDFM:2", null) }
        forcedStatus = 503
        expectKind(PrimException.Kind.SERVER) { client().departures("IDFM:2", null) }
    }

    @Test
    fun missingKey() {
        expectKind(PrimException.Kind.NO_KEY) { client("").departures("IDFM:2", null) }
        assertTrue(requests.isEmpty())
    }

    private fun expectKind(kind: PrimException.Kind, block: () -> Unit) {
        try {
            block()
            fail("PrimException $kind attendue")
        } catch (e: PrimException) {
            assertEquals(kind, e.kind)
        }
    }
}
