package fr.trprim.siri.data

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/** Mini client HTTP (GET uniquement) basé sur HttpURLConnection. */
object Http {

    data class Response(val code: Int, val body: String)

    private const val USER_AGENT = "TempsReelIDF-Android/1.0"

    @Throws(IOException::class)
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 8_000,
        readTimeoutMs: Int = 12_000,
    ): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                // Certaines versions d'Android lèvent une exception sur un 401 sans en-tête WWW-Authenticate.
                if (e.message?.contains("authentication challenge", ignoreCase = true) == true) {
                    return Response(401, "")
                }
                throw e
            }
            val raw: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
            val stream = if (raw != null && "gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                GZIPInputStream(raw)
            } else {
                raw
            }
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return Response(code, body)
        } finally {
            conn.disconnect()
        }
    }

    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
