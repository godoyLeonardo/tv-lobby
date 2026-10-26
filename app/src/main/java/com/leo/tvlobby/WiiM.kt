package com.leo.tvlobby

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        val ownSource get() = mode > 0 && !onTv
        val source get() = SOURCES[mode] ?: "Streaming"
        val nowPlaying get() = listOf(title, artist).filter { it.isNotBlank() && it != "Unknown" }.joinToString(" — ").ifEmpty { source }
    }

    class Input(val label: String, val command: String, val icon: Int, val modes: Set<Int>)

    class Track(val title: String, val artist: String, val artUrl: String?)

    private val executor = Executors.newSingleThreadExecutor()

    // The track is only read while the WiiM plays its own source; on TV sound there is none.
    fun status(onResult: (Status?, Track?) -> Unit) = executor.execute {
        val status = runCatching { parse(JSONObject(call("getPlayerStatus"))) }.getOrNull()
        val track = if (status?.ownSource == true) runCatching { parseTrack(JSONObject(call("getMetaInfo")).getJSONObject("metaData")) }.getOrNull() else null
        onResult(status, track)
    }

    // Album art comes from the streaming service's CDN, not from the WiiM.
    fun art(url: String, onResult: (Bitmap?) -> Unit) = executor.execute {
        onResult(runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull())
    }

    fun togglePlayback(onDone: (Boolean) -> Unit) = command("setPlayerCmd:onepause", onDone)

    fun next(onDone: (Boolean) -> Unit) = command("setPlayerCmd:next", onDone)

    fun previous(onDone: (Boolean) -> Unit) = command("setPlayerCmd:prev", onDone)

    @Volatile
    private var supported: List<Input>? = null

    // The inputs this model has, from the plm_support bitmask, plus network streaming.
    fun inputs(onResult: (List<Input>) -> Unit) = executor.execute {
        val inputs = supported ?: runCatching {
            val plm = JSONObject(call("getStatusEx")).optString("plm_support").removePrefix("0x").toLong(16)
            INPUTS.filter { (bit, _) -> plm and bit != 0L }.map { it.second } + WIFI
        }.getOrNull()?.also { supported = it }
        onResult(inputs.orEmpty())
    }

    fun useTvSound(onDone: (Boolean) -> Unit) = command("setPlayerCmd:switchmode:HDMI", onDone)

    fun switchTo(input: Input, onDone: (Boolean) -> Unit) = command("setPlayerCmd:switchmode:${input.command}", onDone)

    fun setVolume(volume: Int, onDone: (Boolean) -> Unit) = command("setPlayerCmd:vol:$volume", onDone)

    fun setMuted(muted: Boolean, onDone: (Boolean) -> Unit) = command("setPlayerCmd:mute:${if (muted) 1 else 0}", onDone)

    // The API document's `reboot` is an unknown command on this firmware; StartRebootTime:1 restarts it.
    fun restart(onDone: (Boolean) -> Unit) = command("StartRebootTime:1", onDone)

    private fun command(command: String, onDone: (Boolean) -> Unit) = executor.execute {
        onDone(runCatching { call(command) }.getOrNull()?.contains("OK") == true)
    }

    private fun parse(json: JSONObject) = Status(
        mode = json.optString("mode").toIntOrNull() ?: -1,
        playing = json.optString("status") == "play",
        volume = json.optString("vol").toIntOrNull() ?: 0,
        muted = json.optString("mute") == "1",
        title = decodeHex(json.optString("Title")),
        artist = decodeHex(json.optString("Artist")),
    )

    // The WiiM reports missing fields as "unknow" or "un_known"; the API document shows some keys with a trailing space.
    private fun parseTrack(meta: JSONObject): Track {
        fun field(key: String) = (meta.optString(key).ifEmpty { meta.optString("$key ") }).takeUnless { it.isBlank() || it.startsWith("unknow") || it == "un_known" }
        return Track(field("title").orEmpty(), field("artist").orEmpty(), field("albumArtURI")?.takeIf { it.startsWith("http") })
    }

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

        // plm_support bits, from Home Assistant's LinkPlay library (InputMode), with the switchmode names it sends.
        private val INPUTS = listOf(
            1024L to Input("TV", "HDMI", R.drawable.icon_tv, setOf(MODE_HDMI)),
            65536L to Input("Phono", "phono", R.drawable.icon_album, setOf(54)),
            4L to Input("Bluetooth", "bluetooth", R.drawable.icon_bluetooth, setOf(41)),
            2L to Input("Line in", "line-in", R.drawable.icon_cable, setOf(40)),
            16L to Input("Optical", "optical", R.drawable.icon_settings_input_component, setOf(43)),
        )
        private val WIFI = Input("Wi-Fi", "wifi", R.drawable.icon_wifi, (1..3).toSet() + (10..39))
        private const val LINKPLAY_CERT_SHA256 = "AA60EE2F280BE02E131BBCFFA8804E9C43958B462C90A3D8536D8751C2BEF00A"

        // LinkPlay playback modes.
        private val SOURCES = mapOf(
            0 to "Idle",
            1 to "AirPlay",
            2 to "DLNA",
            31 to "Spotify",
            32 to "TIDAL",
            36 to "Qobuz",
            40 to "Line in",
            41 to "Bluetooth",
            43 to "Optical",
            49 to "TV",
            54 to "Phono",
        )
    }
}
