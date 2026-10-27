package com.leo.tvlobby

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
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
// It also presses the phone remote's navigation and volume keys, which a shell `input keyevent` delivered a key at a time.
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
        instance = this
        if (wiim == null) return
        poller.scheduleWithFixedDelay({ wiim.status { current, _ -> handler.post { update(current) } } }, 0, 4, TimeUnit.SECONDS)
    }

    override fun onDestroy() {
        instance = null
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
        if (event.keyCode !in KEYS || status?.ownSource != true) return false
        if (event.action == KeyEvent.ACTION_DOWN) stepWiim(event.keyCode, event.repeatCount)
        return true
    }

    private fun stepWiim(keyCode: Int, repeatCount: Int) {
        val wiim = wiim ?: return
        lastPress = SystemClock.uptimeMillis()
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_MUTE -> if (repeatCount == 0) {
                muted = !muted
                wiim.setMuted(muted) {}
            }
            else -> {
                volume = (volume + if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) STEP else -STEP).coerceIn(0, 100)
                if (muted) {
                    muted = false
                    wiim.setMuted(false) {}
                }
                wiim.setVolume(volume) {}
            }
        }
        showIndicator()
    }

    // A phone remote key: false when this service doesn't press it (the remote then falls back to the shell).
    // Volume goes to the WiiM while it plays its own source, else to the TV's audio, which sends it over CEC on eARC.
    fun press(key: String): Boolean {
        val action = NAVIGATION[key]
        val volumeKey = VOLUME[key]
        when {
            action != null -> performGlobalAction(action)
            volumeKey != null -> handler.post {
                if (status?.ownSource == true) stepWiim(volumeKey, 0)
                else getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, ADJUSTMENTS.getValue(volumeKey), AudioManager.FLAG_SHOW_UI)
            }
            else -> return false
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

        // The phone remote's key names (RemoteService.KEYS) this service presses itself.
        private val NAVIGATION = mapOf(
            "up" to GLOBAL_ACTION_DPAD_UP,
            "down" to GLOBAL_ACTION_DPAD_DOWN,
            "left" to GLOBAL_ACTION_DPAD_LEFT,
            "right" to GLOBAL_ACTION_DPAD_RIGHT,
            "ok" to GLOBAL_ACTION_DPAD_CENTER,
            "back" to GLOBAL_ACTION_BACK,
            "home" to GLOBAL_ACTION_HOME,
        )
        private val VOLUME = mapOf(
            "volume-up" to KeyEvent.KEYCODE_VOLUME_UP,
            "volume-down" to KeyEvent.KEYCODE_VOLUME_DOWN,
            "mute" to KeyEvent.KEYCODE_VOLUME_MUTE,
        )
        private val ADJUSTMENTS = mapOf(
            KeyEvent.KEYCODE_VOLUME_UP to AudioManager.ADJUST_RAISE,
            KeyEvent.KEYCODE_VOLUME_DOWN to AudioManager.ADJUST_LOWER,
            KeyEvent.KEYCODE_VOLUME_MUTE to AudioManager.ADJUST_TOGGLE_MUTE,
        )

        // The running service, for the phone remote; null while it's off.
        @Volatile
        var instance: VolumeKeyService? = null
            private set

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
