package com.leo.tvlobby

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// While the WiiM plays its own source (a cast, Spotify, Bluetooth…) it isn't the TV's audio system, so the remote's
// volume keys would only change the TV speakers. This takes them, on any screen, and sets the WiiM's volume instead.
// On TV sound the keys pass through and reach the WiiM over HDMI-CEC as usual.
class VolumeKeyService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val wiim = Lobby.outputs.firstNotNullOfOrNull { it.wiimHost }?.let(::WiiM)
    private val poller = Executors.newSingleThreadScheduledExecutor()

    @Volatile
    private var status: WiiM.Status? = null
    private var volume = 0
    private var muted = false
    private var lastPress = 0L

    private var indicator: LinearLayout? = null
    private lateinit var indicatorText: TextView
    private val hideIndicator = Runnable { indicator?.animate()?.alpha(0f)?.setDuration(250)?.start() }

    override fun onServiceConnected() {
        if (wiim == null) return
        poller.scheduleWithFixedDelay({ wiim.status { current, _ -> handler.post { update(current) } } }, 0, 4, TimeUnit.SECONDS)
    }

    override fun onDestroy() {
        poller.shutdownNow()
        indicator?.let { getSystemService(WindowManager::class.java).removeView(it) }
        super.onDestroy()
    }

    // A poll that lands right after a press would undo the press on screen before the WiiM has applied it.
    private fun update(current: WiiM.Status?) {
        status = current
        if (current != null && SystemClock.uptimeMillis() - lastPress > 2_000) {
            volume = current.volume
            muted = current.muted
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val wiim = wiim ?: return false
        if (event.keyCode !in KEYS || status?.ownSource != true) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastPress = SystemClock.uptimeMillis()
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_MUTE -> if (event.repeatCount == 0) {
                    muted = !muted
                    wiim.setMuted(muted) {}
                }
                else -> {
                    volume = (volume + if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) STEP else -STEP).coerceIn(0, 100)
                    if (muted) {
                        muted = false
                        wiim.setMuted(false) {}
                    }
                    wiim.setVolume(volume) {}
                }
            }
            showIndicator()
        }
        return true
    }

    private fun showIndicator() {
        val view = indicator ?: createIndicator().also { indicator = it }
        indicatorText.text = if (muted) "Muted" else "$volume"
        view.animate().cancel()
        view.alpha = 1f
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, 1_500)
    }

    private fun createIndicator(): LinearLayout {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0xE6161922.toInt())
            }
            setPadding(dp(20), dp(12), dp(24), dp(12))
            addView(ImageView(context).apply { setImageDrawable(icon(R.drawable.icon_speaker)) }, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(TextView(context).apply {
                text = "WiiM"
                textSize = 18f
                typeface = Fonts.manrope(context, 600)
                setTextColor(Palette.MUTED)
                setPadding(dp(12), 0, dp(12), 0)
            })
            indicatorText = TextView(context).apply {
                textSize = 22f
                typeface = Fonts.manrope(context, 700)
                setTextColor(Palette.TEXT)
            }
            addView(indicatorText)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(48)
            y = dp(40)
        }
        getSystemService(WindowManager::class.java).addView(view, params)
        return view
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val STEP = 2
        private val KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)

        fun component(context: Context) = ComponentName(context, VolumeKeyService::class.java).flattenToString()

        fun isEnabled(context: Context) =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').contains(component(context))

        // Adds this service to the enabled list, keeping any other; needs WRITE_SECURE_SETTINGS (granted by deploy.sh).
        fun enable(context: Context): Boolean = runCatching {
            if (isEnabled(context)) return true
            val resolver = context.contentResolver
            val others = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').filter { it.isNotBlank() }
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + component(context)).joinToString(":"))
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }.getOrDefault(false)
    }
}
