package com.leo.tvlobby

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

// Each moving part that a firmware update, the network or a device can break, with what to do about it.
// Shown on the TV (HealthDialog) and on the phone remote (/api/health).
class Health(private val context: Context, private val controls: Controls) {
    class Result(val ok: Boolean, val detail: String)

    class Check(val name: String, val run: (done: (Result) -> Unit) -> Unit)

    val checks: List<Check> = buildList {
        val pkg = context.packageName

        add(Check("Home screen") { done ->
            val home = context.packageManager
                .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            done(if (home == pkg) pass("TV Lobby opens on Home") else fail("Home goes to $home: restart the TV, or run ./deploy.sh"))
        })

        add(Check("Sound switching") { done ->
            done(
                if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) pass("Allowed")
                else fail("Permission missing: run ./deploy.sh"),
            )
        })

        add(Check("TV's ADB") { done ->
            controls.localAdb.run(
                "echo ok",
                onError = { done(fail("Unreachable: turn on network debugging, then allow TV Lobby's prompt")) },
            ) { done(if (it.trim() == "ok") pass("Connected; waking, turning off and the phone remote work") else fail("Unexpected answer: $it")) }
        })

        add(Check("Starts at boot") { done ->
            controls.localAdb.run(
                "appops get $pkg AUTO_START",
                onError = { done(fail("Couldn't read it: the TV's ADB is unreachable")) },
            ) { done(if ("allow" in it) pass("Allowed, so the phone remote runs without opening the lobby") else fail("TCL blocks it: run ./deploy.sh, or open the lobby once")) }
        })

        add(Check("HDMI-CEC") { done ->
            controls.localAdb.cecPowerByPort { power ->
                val (on, off) = Lobby.inputs.partition { it.hdmiPort in power }
                val detail = listOfNotNull(
                    on.takeIf { it.isNotEmpty() }?.joinToString(prefix = "On CEC: ") { it.label },
                    off.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Not on CEC: ") { it.label },
                ).joinToString(" · ")
                done(if (on.isNotEmpty()) pass(detail) else fail("No input device answers: check HDMI-CEC (T-Link) in the TV settings"))
            }
        })

        val wiimHost = Lobby.outputs.firstNotNullOfOrNull { it.wiimHost }
        val wiim = controls.wiim
        if (wiimHost != null && wiim != null) {
            add(Check("WiiM") { done ->
                wiim.status { status, _ ->
                    done(
                        if (status == null) fail("Not answering at $wiimHost: check its address in the router")
                        else pass("$wiimHost · ${status.source} · ${if (status.muted) "muted" else "volume ${status.volume}"}"),
                    )
                }
            })
        }

        if (wiim != null) {
            add(Check("Spotify Connect") { done ->
                wiim.spotifyConnectUser { result ->
                    done(
                        result.fold(
                            { user -> pass(if (user.isEmpty() || user == "null") "Ready on the WiiM; no one signed in" else "Signed in as $user") },
                            { fail("The WiiM's Spotify endpoint doesn't answer: try Reset Spotify, then Restart WiiM") },
                        ),
                    )
                }
            })
        }

        add(Check("Phone remote") { done ->
            thread {
                val listening = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", RemoteService.PORT), 1_000) } }.isSuccess
                done(if (listening) pass("http://${tvAddress() ?: "<TV address>"}:${RemoteService.PORT}") else fail("Not running: open the lobby once to start it"))
            }
        })
    }

    private fun pass(detail: String) = Result(true, detail)

    private fun fail(detail: String) = Result(false, detail)

    private fun tvAddress(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses
            ?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }
}
