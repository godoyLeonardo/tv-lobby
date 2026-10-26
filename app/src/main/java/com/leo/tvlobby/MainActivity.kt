package com.leo.tvlobby

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.database.ContentObserver
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val tvInputs by lazy { getSystemService(TvInputManager::class.java) }
    private val localAdb by lazy { LocalAdb(this) }
    private val aurora by lazy { AuroraView(this) }
    private val inputTiles = linkedMapOf<String, Tile>()
    private val outputTiles = linkedMapOf<AudioOutput, Tile>()
    private lateinit var appsRow: LinearLayout
    private var shownApps = emptyList<String>()

    private val outputObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = renderOutput()
    }

    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) = renderInputs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout())
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Lobby.OUTPUT_SETTING), false, outputObserver)
        tvInputs?.registerCallback(inputCallback, handler)
        inputTiles.values.first().requestFocus()
    }

    override fun onResume() {
        super.onResume()
        aurora.resume()
        renderInputs()
        renderOutput()
        renderApps()
    }

    override fun onPause() {
        aurora.pause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        inputTiles.values.first().requestFocus()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(outputObserver)
        tvInputs?.unregisterCallback(inputCallback)
        super.onDestroy()
    }

    @Deprecated("A home screen has nowhere to go back to")
    override fun onBackPressed() = Unit

    private fun layout(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(16))
            clipChildren = false
            clipToPadding = false
            addView(header())
            addView(sectionLabel("Inputs"))
            addView(row().apply { Lobby.inputs.forEach { addView(inputTile(it)) } })
            addView(sectionLabel("Sound"))
            addView(row().apply { Lobby.outputs.forEach { addView(outputTile(it)) } })
            addView(sectionLabel("Apps"))
            appsRow = row()
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                clipChildren = false
                clipToPadding = false
                addView(appsRow)
            })
        }
        return FrameLayout(this).apply {
            addView(aurora)
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                isFillViewport = true
                addView(column)
            })
            viewTreeObserver.addOnGlobalFocusChangeListener { _, focused ->
                aurora.glowWith((focused as? Tile)?.accent?.takeIf { it != Palette.SURFACE })
            }
        }
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), dp(4))
        clipChildren = false
        clipToPadding = false
        addView(clock(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Tile(context, 148, 48, Palette.SURFACE).apply {
            title.text = "Settings"
            title.textSize = 15f
            detail.visibility = View.GONE
            showMark(getDrawable(R.drawable.icon_settings)!!, 22)
            setOnClickListener { launch(Intent(Settings.ACTION_SETTINGS)) }
            (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        })
    }

    private fun clock(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        addView(TextClock(context).apply {
            format24Hour = "HH:mm"
            format12Hour = "h:mm"
            textSize = 44f
            setTextColor(Palette.TEXT)
            typeface = Fonts.manrope(context, 300)
        })
        addView(TextClock(context).apply {
            format24Hour = "EEEE d MMMM"
            format12Hour = "EEEE d MMMM"
            textSize = 18f
            typeface = Fonts.manrope(context, 500)
            setTextColor(Palette.MUTED)
            setPadding(dp(20), 0, 0, dp(10))
        })
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 13f
        typeface = Fonts.manrope(context, 700)
        letterSpacing = 0.14f
        setTextColor(Palette.MUTED)
        setPadding(dp(8), dp(14), 0, 0)
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(8), dp(10), dp(8), dp(10))
        clipChildren = false
        clipToPadding = false
    }

    private fun inputTile(input: Input) = Tile(this, 256, 112, input.colors.last()).apply {
        title.text = input.label
        showMark(getDrawable(input.mark)!!, 44)
        fillWith(input.colors)
        setOnClickListener {
            val channel = TvContract.buildChannelUriForPassthroughInput(input.inputId)
            launch(Intent(Intent.ACTION_VIEW).setDataAndType(channel, TvContract.Channels.CONTENT_ITEM_TYPE))
            localAdb.wakeHdmiDevice(input.hdmiPort) {
                handler.post { Toast.makeText(this@MainActivity, "Couldn't wake ${input.label}: allow TV Lobby's debugging prompt", Toast.LENGTH_LONG).show() }
            }
        }
        inputTiles[input.inputId] = this
    }

    private fun outputTile(output: AudioOutput) = Tile(this, 256, 80, output.accent).apply {
        title.text = output.label
        showMark(getDrawable(output.icon)!!, 30)
        setOnClickListener { select(output) }
        outputTiles[output] = this
    }

    private fun appTile(app: ResolveInfo): Tile {
        val activity = app.activityInfo
        return Tile(this, 160, 90, Palette.SURFACE).apply {
            val banner = activity.loadBanner(packageManager)
            if (banner != null) showImage(banner) else showIcon(activity.loadIcon(packageManager), app.loadLabel(packageManager))
            contentDescription = app.loadLabel(packageManager)
            setOnClickListener {
                launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setClassName(activity.packageName, activity.name))
            }
        }
    }

    private fun renderInputs() {
        inputTiles.forEach { (inputId, tile) ->
            val state = runCatching { tvInputs?.getInputState(inputId) }.getOrNull()
            when (state) {
                TvInputManager.INPUT_STATE_CONNECTED -> tile.showStatus("On", Palette.ON)
                TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> tile.showStatus("Standby", Palette.STANDBY)
                else -> tile.showStatus("Off", null)
            }
        }
    }

    private fun renderOutput() {
        val current = Settings.Global.getInt(contentResolver, Lobby.OUTPUT_SETTING, 1)
        outputTiles.forEach { (output, tile) ->
            tile.active = output.tclType == current
            if (tile.active) tile.showStatus("Playing", Palette.ON) else tile.showStatus(output.detail, null)
        }
    }

    private fun renderApps() {
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launchable, 0)
            .filter { it.activityInfo.packageName != packageName && it.activityInfo.packageName !in Lobby.hiddenApps }
            .sortedWith(compareBy({ favoriteRank(it.activityInfo.packageName) }, { it.loadLabel(packageManager).toString().lowercase() }))
        val keys = apps.map { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
        if (keys == shownApps) return
        shownApps = keys
        appsRow.removeAllViews()
        apps.forEach { appsRow.addView(appTile(it)) }
    }

    private fun favoriteRank(pkg: String) = Lobby.favoriteApps.indexOf(pkg).let { if (it < 0) Int.MAX_VALUE else it }

    private fun select(output: AudioOutput) {
        try {
            Settings.Global.putInt(contentResolver, Lobby.OUTPUT_SETTING, output.tclType)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Missing WRITE_SECURE_SETTINGS: run ./deploy.sh", Toast.LENGTH_LONG).show()
        }
        renderOutput()
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Nothing on this TV opens that", Toast.LENGTH_SHORT).show()
        }
    }
}
