package com.leo.tvlobby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.tv.TvInputManager
import android.os.IBinder
import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// Serves the phone remote on the home network, whatever the TV is showing. A background app may not
// start activities, so anything that changes the screen goes through the TV's shell (LocalAdb).
class RemoteService : Service() {
    private val controls by lazy { Controls(this) }
    private val tvInputs by lazy { getSystemService(TvInputManager::class.java) }
    private var server: ServerSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        thread(name = "remote") { serve() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        server?.close()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Phone remote", NotificationManager.IMPORTANCE_MIN))
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.icon_tv)
            .setContentTitle("Phone remote on port $PORT")
            .build()
    }

    private fun serve() {
        val socket = runCatching { ServerSocket(PORT) }.getOrNull() ?: return
        server = socket
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            thread { client.use(::handle) }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5_000
        val reader = client.getInputStream().bufferedReader()
        val request = reader.readLine()?.split(" ") ?: return
        while (reader.readLine()?.isNotEmpty() == true) Unit
        val (status, type, body) = runCatching { route(request.getOrNull(0), request.getOrNull(1).orEmpty()) }
            .getOrElse { Response("500 Internal Server Error", JSON, JSONObject().put("error", it.toString()).toString()) }
        val bytes = body.toByteArray()
        client.getOutputStream().run {
            write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    private data class Response(val status: String, val type: String, val body: String)

    private fun route(method: String?, path: String): Response {
        val parts = path.trim('/').split('/')
        return when {
            method == "GET" && path == "/" -> Response(OK, "text/html; charset=utf-8", assets.open("remote.html").bufferedReader().use { it.readText() })
            method == "GET" && path == "/api/state" -> Response(OK, JSON, state().toString())
            method == "POST" && parts.size == 3 && parts[1] == "input" -> act { openInput(Lobby.inputs.first { it.hdmiPort == parts[2].toInt() }) }
            method == "POST" && parts.size == 4 && parts[1] == "input" && parts[3] == "off" ->
                act { controls.localAdb.standbyHdmiDevice(parts[2].toInt(), onError = {}, onAbsent = {}) }
            method == "GET" && path == "/api/wiim/inputs" -> Response(OK, JSON, wiimInputs().toString())
            method == "GET" && path == "/api/health" -> Response(OK, JSON, health().toString())
            method == "POST" && parts.size == 4 && parts[1] == "wiim" -> wiimAction(parts[2], parts[3])
            method == "POST" && parts.size == 3 && parts[1] == "wiim" -> wiimAction(parts[2], null)
            method == "POST" && parts.size == 3 && parts[1] == "output" -> act { controls.selectOutput(Lobby.outputs.first { it.tclType == parts[2].toInt() }) }
            method == "POST" && path == "/api/lobby" -> act { controls.localAdb.run("am start -a ${Intent.ACTION_MAIN} -c ${Intent.CATEGORY_HOME}", onError = {}) }
            method == "POST" && path == "/api/goodnight" -> act { controls.localAdb.goodnight(onError = {}) }
            else -> Response("404 Not Found", JSON, "{}")
        }
    }

    private fun health(): JSONArray {
        val checks = Health(this, controls).checks
        val results = checks.map { check -> CompletableFuture<Health.Result>().also { check.run(it::complete) } }
        return JSONArray(checks.zip(results).map { (check, future) ->
            val result = runCatching { future.get(6, TimeUnit.SECONDS) }.getOrNull()
            JSONObject().put("name", check.name).put("ok", result?.ok == true).put("detail", result?.detail ?: "No answer")
        })
    }

    private fun wiimInputs(): JSONArray {
        val wiim = controls.wiim ?: return JSONArray()
        val inputs = CompletableFuture<List<WiiM.Input>>()
        wiim.inputs(inputs::complete)
        return JSONArray(runCatching { inputs.get(4, TimeUnit.SECONDS) }.getOrDefault(emptyList()).map { JSONObject().put("label", it.label).put("command", it.command) })
    }

    private fun wiimAction(action: String, value: String?): Response {
        val wiim = controls.wiim ?: return Response("404 Not Found", JSON, "{}")
        val done = CompletableFuture<Boolean>()
        when (action) {
            "input" -> {
                val inputs = CompletableFuture<List<WiiM.Input>>()
                wiim.inputs(inputs::complete)
                val input = inputs.get(4, TimeUnit.SECONDS).first { it.command == value }
                controls.useWiimInput(input, done::complete)
            }
            "volume" -> wiim.setVolume(value!!.toInt().coerceIn(0, 100), done::complete)
            "mute" -> wiim.setMuted(value == "1", done::complete)
            "previous" -> wiim.previous(done::complete)
            "toggle" -> wiim.togglePlayback(done::complete)
            "next" -> wiim.next(done::complete)
            "restart" -> wiim.restart(done::complete)
            "spotify-reset" -> wiim.resetSpotifyConnect(done::complete)
            else -> return Response("404 Not Found", JSON, "{}")
        }
        val accepted = runCatching { done.get(6, TimeUnit.SECONDS) }.getOrDefault(false)
        return Response(OK, JSON, JSONObject().put("ok", accepted).toString())
    }

    private fun act(action: () -> Unit): Response {
        action()
        return Response(OK, JSON, JSONObject().put("ok", true).toString())
    }

    private fun openInput(input: Input) {
        val intent = controls.passthroughIntent(input)
        controls.localAdb.run("am start -a ${intent.action} -d '${intent.data}' -t '${intent.type}'", onError = {})
        controls.localAdb.wakeHdmiDevice(input.hdmiPort, onError = {})
    }

    private fun state(): JSONObject {
        val cec = CompletableFuture<Map<Int, Int>>()
        controls.localAdb.cecPowerByPort(cec::complete)
        val wiim = CompletableFuture<Pair<WiiM.Status?, WiiM.Track?>>()
        controls.wiim?.status { status, track -> wiim.complete(status to track) } ?: wiim.complete(null to null)
        val power = runCatching { cec.get(4, TimeUnit.SECONDS) }.getOrDefault(emptyMap())
        val (status, track) = runCatching { wiim.get(4, TimeUnit.SECONDS) }.getOrDefault(null to null)
        val output = controls.currentOutput
        return JSONObject()
            .put("inputs", JSONArray(Lobby.inputs.map { input ->
                JSONObject()
                    .put("port", input.hdmiPort)
                    .put("label", input.label)
                    .put("power", power(input, power[input.hdmiPort]))
                    .put("colors", JSONArray(input.colors.map { "#%06X".format(it and 0xFFFFFF) }))
            }))
            .put("outputs", JSONArray(Lobby.outputs.map { out ->
                JSONObject().put("type", out.tclType).put("label", out.label).put("detail", out.detail).put("active", out.tclType == output)
            }))
            .put("wiim", status?.let {
                JSONObject()
                    .put("source", it.source)
                    .put("onTv", it.onTv)
                    .put("playing", it.playing)
                    .put("volume", it.volume)
                    .put("muted", it.muted)
                    .put("title", track?.title.orEmpty())
                    .put("artist", track?.artist.orEmpty())
                    .put("art", track?.artUrl.orEmpty())
                    .put("ownSource", it.ownSource)
                    .put("mode", it.mode)
                    .put("presets", JSONArray(Lobby.wiimVolumePresets.map { (label, volume) -> JSONObject().put("label", label).put("volume", volume) }))
            })
    }

    private fun power(input: Input, cec: Int?) = when (cec) {
        0, 2 -> "on"
        1, 3 -> "standby"
        else -> when (runCatching { tvInputs?.getInputState(input.inputId) }.getOrNull()) {
            TvInputManager.INPUT_STATE_CONNECTED -> "on"
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "standby"
            else -> "off"
        }
    }

    companion object {
        const val PORT = 8888
        private const val CHANNEL = "remote"
        private const val OK = "200 OK"
        private const val JSON = "application/json"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RemoteService::class.java))
        }
    }
}
