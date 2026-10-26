package com.leo.tvlobby

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

// "Lobby status": checks each moving part that a firmware update, the network or a device can break, and says what to do.
class HealthDialog(context: Context, private val controls: Controls) :
    Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    private class Row(val icon: ImageView, val detail: TextView)

    private val handler = Handler(Looper.getMainLooper())
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        setContentView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = context.dp(24).toFloat()
                setColor(0xF2161922.toInt())
            }
            setPadding(context.dp(32), context.dp(24), context.dp(32), context.dp(20))
            addView(TextView(context).apply {
                text = "Lobby status"
                textSize = 22f
                typeface = Fonts.manrope(context, 700)
                setTextColor(Palette.TEXT)
                setPadding(0, 0, 0, context.dp(8))
            })
            addView(list, LinearLayout.LayoutParams(context.dp(640), LinearLayout.LayoutParams.WRAP_CONTENT))
        })
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.6f)
        runChecks()
    }

    private fun runChecks() {
        val pkg = context.packageName

        checkNow("Home screen") {
            val home = context.packageManager
                .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            if (home == pkg) pass("TV Lobby opens on Home") else fail("Home goes to $home: restart the TV, or run ./deploy.sh")
        }

        checkNow("Sound switching") {
            if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) pass("Allowed")
            else fail("Permission missing: run ./deploy.sh")
        }

        check("TV's ADB") { done ->
            controls.localAdb.run(
                "echo ok",
                onError = { done(fail("Unreachable: turn on network debugging, then allow TV Lobby's prompt")) },
            ) { done(if (it.trim() == "ok") pass("Connected; waking, turning off and the phone remote work") else fail("Unexpected answer: $it")) }
        }

        check("HDMI-CEC") { done ->
            controls.localAdb.cecPowerByPort { power ->
                val (on, off) = Lobby.inputs.partition { it.hdmiPort in power }
                val detail = listOfNotNull(
                    on.takeIf { it.isNotEmpty() }?.joinToString(prefix = "On CEC: ") { it.label },
                    off.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Not on CEC: ") { it.label },
                ).joinToString(" · ")
                done(if (on.isNotEmpty()) pass(detail) else fail("No input device answers: check HDMI-CEC (T-Link) in the TV settings"))
            }
        }

        val wiimHost = Lobby.outputs.firstNotNullOfOrNull { it.wiimHost }
        if (wiimHost != null) {
            check("WiiM") { done ->
                controls.wiim?.status { status, _ ->
                    done(
                        if (status == null) fail("Not answering at $wiimHost: check its address in the router")
                        else pass("$wiimHost · ${status.source} · ${if (status.muted) "muted" else "volume ${status.volume}"}"),
                    )
                }
            }
        }

        check("Phone remote") { done ->
            thread {
                val listening = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", RemoteService.PORT), 1_000) } }.isSuccess
                done(if (listening) pass("http://${tvAddress() ?: "<TV address>"}:${RemoteService.PORT}") else fail("Not running: open the lobby once to start it"))
            }
        }
    }

    private class Result(val ok: Boolean, val detail: String)

    private fun pass(detail: String) = Result(true, detail)

    private fun fail(detail: String) = Result(false, detail)

    private fun checkNow(name: String, test: () -> Result) = check(name) { done -> done(test()) }

    private fun check(name: String, test: (done: (Result) -> Unit) -> Unit) {
        val row = addRow(name)
        test { result ->
            handler.post {
                row.icon.setImageDrawable(context.icon(if (result.ok) R.drawable.icon_check_circle else R.drawable.icon_error))
                row.icon.setColorFilter(if (result.ok) Palette.ON else Menu.DANGER)
                row.detail.text = result.detail
                row.detail.setTextColor(if (result.ok) Palette.MUTED else Palette.TEXT)
            }
        }
    }

    private fun addRow(name: String): Row {
        val icon = ImageView(context).apply {
            setImageDrawable(context.icon(R.drawable.icon_schedule))
            setColorFilter(Palette.MUTED)
        }
        val detail = TextView(context).apply {
            text = "Checking…"
            textSize = 14f
            typeface = Fonts.manrope(context, 500)
            setTextColor(Palette.MUTED)
        }
        list.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(10), 0, context.dp(10))
            addView(icon, LinearLayout.LayoutParams(context.dp(26), context.dp(26)).apply { marginEnd = context.dp(16) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = name
                    textSize = 17f
                    typeface = Fonts.manrope(context, 700)
                    setTextColor(Palette.TEXT)
                })
                addView(detail)
            })
        })
        return Row(icon, detail)
    }

    private fun tvAddress(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses
            ?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }
}
