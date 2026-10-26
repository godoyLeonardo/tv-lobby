package com.leo.tvlobby

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.database.ContentObserver
import android.graphics.Typeface
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val tvInputs by lazy { getSystemService(TvInputManager::class.java) }
    private val inputTiles = linkedMapOf<String, Tile>()
    private val outputTiles = linkedMapOf<Int, Tile>()
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
        renderInputs()
        renderOutput()
        renderApps()
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
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            setBackgroundColor(Palette.BACKGROUND)
            addView(column)
        }
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        setPadding(dp(8), 0, dp(8), dp(4))
        addView(TextClock(context).apply {
            format24Hour = "HH:mm"
            format12Hour = "h:mm"
            textSize = 44f
            setTextColor(Palette.TEXT)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        addView(TextClock(context).apply {
            format24Hour = "EEEE d MMMM"
            format12Hour = "EEEE d MMMM"
            textSize = 18f
            setTextColor(Palette.MUTED)
            setPadding(dp(20), 0, 0, dp(10))
        })
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 13f
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

    private fun inputTile(input: Input) = Tile(this, 200, 104, input.accent).apply {
        title.text = input.label
        detail.text = input.port
        setOnClickListener {
            val channel = TvContract.buildChannelUriForPassthroughInput(input.inputId)
            launch(Intent(Intent.ACTION_VIEW).setDataAndType(channel, TvContract.Channels.CONTENT_ITEM_TYPE))
        }
        inputTiles[input.inputId] = this
    }

    private fun outputTile(output: AudioOutput) = Tile(this, 200, 72, output.accent).apply {
        title.text = output.label
        detail.text = output.detail
        setOnClickListener { select(output) }
        outputTiles[output.tclType] = this
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
            tile.showStatus(
                when (state) {
                    TvInputManager.INPUT_STATE_CONNECTED -> Palette.ON
                    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> Palette.STANDBY
                    else -> null
                }
            )
        }
    }

    private fun renderOutput() {
        val current = Settings.Global.getInt(contentResolver, Lobby.OUTPUT_SETTING, 1)
        outputTiles.forEach { (type, tile) ->
            tile.active = type == current
            tile.showStatus(if (type == current) Palette.ON else null)
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
