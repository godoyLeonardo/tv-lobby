package com.leo.tvlobby

import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

// LinkPlay HTTP API of a WiiM streamer on the LAN.
class WiiM(private val host: String) {
    class Status(val mode: Int, val playing: Boolean, val volume: Int, val muted: Boolean, val title: String, val artist: String) {
        val onTv get() = mode == MODE_HDMI
        val source get() = SOURCES[mode] ?: "Streaming"
        val nowPlaying get() = listOf(title, artist).filter { it.isNotBlank() && it != "Unknown" }.joinToString(" — ").ifEmpty { source }
    }

    private val executor = Executors.newSingleThreadExecutor()

    fun status(onResult: (Status?) -> Unit) = executor.execute {
        onResult(runCatching { parse(JSONObject(call("getPlayerStatus"))) }.getOrNull())
    }

    fun useTvSound(onDone: () -> Unit) = executor.execute {
        runCatching { call("setPlayerCmd:switchmode:HDMI") }
        onDone()
    }

    private fun parse(json: JSONObject) = Status(
        mode = json.optString("mode").toIntOrNull() ?: -1,
        playing = json.optString("status") == "play",
        volume = json.optString("vol").toIntOrNull() ?: 0,
        muted = json.optString("mute") == "1",
        title = decodeHex(json.optString("Title")),
        artist = decodeHex(json.optString("Artist")),
    )

    private fun decodeHex(hex: String) = runCatching { String(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) }.getOrDefault("")

    private fun call(command: String): String {
        val connection = URL("https://$host/httpapi.asp?command=$command").openConnection() as HttpsURLConnection
        connection.sslSocketFactory = pinnedTls.socketFactory
        connection.setHostnameVerifier { hostname, _ -> hostname == host }
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    // The WiiM serves LinkPlay's self-signed certificate (CN www.linkplay.com): trust exactly that certificate, for this host only.
    private val pinnedTls = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(chain.first().encoded).joinToString("") { "%02X".format(it) }
                if (fingerprint != LINKPLAY_CERT_SHA256) throw CertificateException("Unexpected certificate $fingerprint")
            }

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException()

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }), null)
    }

    companion object {
        const val MODE_HDMI = 49
        private const val LINKPLAY_CERT_SHA256 = "AA60EE2F280BE02E131BBCFFA8804E9C43958B462C90A3D8536D8751C2BEF00A"

        // LinkPlay playback modes.
        private val SOURCES = mapOf(
            0 to "Idle",
            1 to "AirPlay",
            2 to "DLNA",
            31 to "Spotify",
            32 to "TIDAL",
            40 to "Line in",
            41 to "Bluetooth",
            43 to "Optical",
            49 to "TV",
        )
    }
}
