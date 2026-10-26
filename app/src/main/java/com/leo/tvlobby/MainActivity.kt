package com.leo.tvlobby

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
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
    private val controls by lazy { Controls(this) }
    private val localAdb get() = controls.localAdb
    private val aurora by lazy { AuroraView(this) }
    private val inputTiles = linkedMapOf<Input, Tile>()
    private val outputTiles = linkedMapOf<AudioOutput, Tile>()
    private lateinit var appsRow: LinearLayout
    private var shownApps = emptyList<String>()
    private var cecPower = emptyMap<Int, Int>()
    private val wiim get() = controls.wiim
    private var wiimStatus: WiiM.Status? = null
    private var wiimTrack: WiiM.Track? = null
    private var wiimArt: Pair<String, Bitmap>? = null
    private val prefs by lazy { getSharedPreferences("lobby", MODE_PRIVATE) }
    private val appPrefs by lazy { AppPrefs(this) }
    private val tilesByKey = mutableMapOf<String, Tile>()
    private var focusLastUsed = true
    private val refreshDevices = Runnable { refreshDevices() }

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
        HomeGuard.ensure(this)
    }

    override fun onResume() {
        super.onResume()
        aurora.resume()
        renderInputs()
        refreshDevices()
        renderOutput()
        renderApps()
        if (focusLastUsed) {
            focusLastUsed = false
            (tilesByKey[prefs.getString(LAST_USED, null)] ?: inputTiles.values.first()).requestFocus()
        }
    }

    override fun onPause() {
        aurora.pause()
        handler.removeCallbacks(refreshDevices)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        focusLastUsed = true
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(outputObserver)
        tvInputs?.unregisterCallback(inputCallback)
        super.onDestroy()
    }

    @Deprecated("A home screen has nowhere to go back to")
    override fun onBackPressed() = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && currentFocus?.isLongClickable == true) return currentFocus!!.performLongClick()
        return super.onKeyDown(keyCode, event)
    }

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
        addView(Tile(context, 160, 48, Palette.SURFACE).apply {
            title.text = "Goodnight"
            title.textSize = 15f
            detail.visibility = View.GONE
            showMark(getDrawable(R.drawable.icon_bedtime)!!, 22)
            setOnClickListener {
                Menu(
                    this@MainActivity, "Turn everything off?", icon(R.drawable.icon_bedtime),
                    listOf(MenuRow(null, listOf(
                        MenuOption("Cancel", icon(R.drawable.icon_close), Palette.SURFACE) {},
                        MenuOption("Goodnight", icon(R.drawable.icon_power), Menu.DANGER) {
                            localAdb.goodnight(onError = { toast("Couldn't turn things off: allow TV Lobby's debugging prompt") })
                        },
                    ))),
                ).show()
            }
        })
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
        setOnClickListener { open(input) }
        setOnLongClickListener {
            Menu(
                this@MainActivity, input.label, icon(input.mark),
                listOf(MenuRow(null, listOf(
                    MenuOption("Open", icon(R.drawable.icon_input), input.colors.last(), input.colors) { open(input) },
                    MenuOption("Turn off", icon(R.drawable.icon_power), Menu.DANGER) { turnOff(input) },
                ))),
            ).show()
            true
        }
        inputTiles[input] = this
        tilesByKey["input:${input.hdmiPort}"] = this
    }

    private fun open(input: Input) {
        rememberLastUsed("input:${input.hdmiPort}")
        launch(controls.passthroughIntent(input))
        localAdb.wakeHdmiDevice(
            input.hdmiPort,
            onError = { toast("Couldn't wake ${input.label}: allow TV Lobby's debugging prompt") },
            onAbsent = {
                val signal = runCatching { tvInputs?.getInputState(input.inputId) }.getOrNull()
                if (signal != TvInputManager.INPUT_STATE_CONNECTED) toast("${input.label} is asleep and the TV can't wake it. ${input.wakeHint}")
            },
        )
    }

    private fun turnOff(input: Input) {
        localAdb.standbyHdmiDevice(
            input.hdmiPort,
            onError = { toast("Couldn't turn off ${input.label}: allow TV Lobby's debugging prompt") },
            onAbsent = { toast("${input.label} isn't on HDMI-CEC, so the TV can't turn it off") },
        )
        refreshDevicesIn(3_000)
    }

    private fun toast(text: String) = handler.post { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }

    private fun outputTile(output: AudioOutput) = Tile(this, 256, 80, output.accent).apply {
        title.text = output.label
        showMark(getDrawable(output.icon)!!, 30)
        setOnClickListener { select(output) }
        if (output.wiimHost != null) {
            setOnLongClickListener {
                showWiimMenu(output)
                true
            }
        }
        outputTiles[output] = this
    }

    private fun appTile(app: ResolveInfo): Tile {
        val activity = app.activityInfo
        return Tile(this, 160, 90, Palette.SURFACE).apply {
            val banner = activity.loadBanner(packageManager)
            if (banner != null) showImage(banner) else showIcon(activity.loadIcon(packageManager), app.loadLabel(packageManager))
            contentDescription = app.loadLabel(packageManager)
            setOnClickListener {
                rememberLastUsed(appKey(app))
                launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setClassName(activity.packageName, activity.name))
            }
            setOnLongClickListener {
                showAppMenu(app)
                true
            }
            tilesByKey[appKey(app)] = this
        }
    }

    private fun showAppMenu(app: ResolveInfo) {
        val pkg = app.activityInfo.packageName
        val pinned = appPrefs.pinned
        val options = buildList {
            when {
                pkg !in pinned -> add(MenuOption("Pin to front", icon(R.drawable.icon_push_pin), Palette.SURFACE) { appPrefs.pinFirst(pkg) })
                pinned.first() != pkg -> add(MenuOption("Move to front", icon(R.drawable.icon_push_pin), Palette.SURFACE) { appPrefs.pinFirst(pkg) })
            }
            if (pkg in pinned) add(MenuOption("Unpin", icon(R.drawable.icon_close), Palette.SURFACE) { appPrefs.unpin(pkg) })
            add(MenuOption("Hide", icon(R.drawable.icon_visibility_off), Menu.DANGER) { appPrefs.hide(pkg) })
        }.map { option -> MenuOption(option.label, option.icon, option.accent) { option.action(); renderApps(focus = appKey(app)) } }
        Menu(this, app.loadLabel(packageManager), app.activityInfo.loadIcon(packageManager), listOf(MenuRow(null, options))).show()
    }

    private fun hiddenAppsTile(hidden: List<ResolveInfo>) = Tile(this, 160, 90, Palette.SURFACE).apply {
        showIcon(icon(R.drawable.icon_visibility_off), "Hidden apps (${hidden.size})")
        setOnClickListener {
            Menu(
                this@MainActivity, "Hidden apps", icon(R.drawable.icon_visibility_off),
                listOf(
                    MenuRow(
                        null,
                        hidden.map { app ->
                            MenuOption(app.loadLabel(packageManager).toString(), app.activityInfo.loadIcon(packageManager), Palette.SURFACE) {
                                appPrefs.unhide(app.activityInfo.packageName)
                                renderApps(focus = appKey(app))
                            }
                        },
                    ),
                ),
            ).show()
        }
        tilesByKey[HIDDEN_APPS] = this
    }

    private fun renderInputs() {
        inputTiles.forEach { (input, tile) ->
            when (cecPower[input.hdmiPort]) {
                CEC_POWER_ON, CEC_POWER_TRANSIENT_TO_ON -> tile.showStatus("On", Palette.ON)
                CEC_POWER_STANDBY, CEC_POWER_TRANSIENT_TO_STANDBY -> tile.showStatus("Standby", Palette.STANDBY)
                else -> when (runCatching { tvInputs?.getInputState(input.inputId) }.getOrNull()) {
                    TvInputManager.INPUT_STATE_CONNECTED -> tile.showStatus("On", Palette.ON)
                    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> tile.showStatus("Standby", Palette.STANDBY)
                    else -> tile.showStatus("Off", null)
                }
            }
        }
    }

    // The HDMI signal state says "on" for a device that is asleep but keeps its output alive (Apple TV),
    // so devices on CEC show their CEC power status instead; the TV's CEC service re-polls it about every minute.
    private fun refreshDevices() {
        localAdb.cecPowerByPort { power ->
            handler.post {
                cecPower = power
                renderInputs()
            }
        }
        wiim?.status { status, track ->
            handler.post {
                wiimStatus = status
                wiimTrack = track
                renderOutput()
                loadWiimArt(track?.artUrl)
            }
        }
        refreshDevicesIn(20_000)
    }

    private fun refreshDevicesIn(delayMillis: Long) {
        handler.removeCallbacks(refreshDevices)
        handler.postDelayed(refreshDevices, delayMillis)
    }

    private fun renderOutput() {
        val current = controls.currentOutput
        outputTiles.forEach { (output, tile) ->
            tile.active = output.tclType == current
            val streamer = wiimStatus?.takeIf { output.wiimHost != null }
            // While the WiiM plays its own source, its tile shows the song, over the album art when there is one.
            val track = wiimTrack.takeIf { streamer?.ownSource == true }
            tile.title.text = track?.title?.takeIf { it.isNotEmpty() } ?: streamer?.takeIf { it.ownSource }?.nowPlaying ?: output.label
            val byline = listOfNotNull(track?.artist?.takeIf { it.isNotEmpty() }, streamer?.source).joinToString(" · ")
            when {
                streamer != null && tile.active && !streamer.onTv -> tile.showStatus("On ${streamer.source}: select for TV sound", Palette.STANDBY)
                tile.active -> tile.showStatus(listOfNotNull("Playing", streamer?.let { if (it.muted) "Muted" else "Vol ${it.volume}" }).joinToString(" · "), Palette.ON)
                streamer?.ownSource == true && streamer.playing -> tile.showStatus(byline, Palette.ON)
                streamer?.ownSource == true -> tile.showStatus("Paused · $byline", null)
                else -> tile.showStatus(output.detail, null)
            }
            if (output.wiimHost != null) tile.showBackdrop(wiimArt?.takeIf { track?.artUrl == it.first }?.let { BitmapDrawable(resources, it.second) })
        }
    }

    private fun renderApps(focus: String? = null) {
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val pinned = appPrefs.pinned
        val hidden = appPrefs.hidden
        val (hiddenApps, apps) = packageManager.queryIntentActivities(launchable, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedWith(compareBy({ pinRank(pinned, it.activityInfo.packageName) }, { it.loadLabel(packageManager).toString().lowercase() }))
            .partition { it.activityInfo.packageName in hidden }
        val keys = apps.map(::appKey) + hiddenApps.map { "hidden:" + appKey(it) }
        if (keys == shownApps) return
        shownApps = keys
        appsRow.removeAllViews()
        tilesByKey.keys.removeAll { it.startsWith("app:") || it == HIDDEN_APPS }
        apps.forEach { appsRow.addView(appTile(it)) }
        if (hiddenApps.isNotEmpty()) appsRow.addView(hiddenAppsTile(hiddenApps))
        focus?.let { (tilesByKey[it] ?: tilesByKey[HIDDEN_APPS] ?: appsRow.getChildAt(0))?.requestFocus() }
    }

    private fun pinRank(pinned: List<String>, pkg: String) = pinned.indexOf(pkg).let { if (it < 0) Int.MAX_VALUE else it }

    private fun select(output: AudioOutput) {
        if (!controls.selectOutput(output, ::afterWiimCommand)) toast("Missing WRITE_SECURE_SETTINGS: run ./deploy.sh")
        renderOutput()
    }

    private fun loadWiimArt(url: String?) {
        if (url == null) {
            wiimArt = null
            aurora.ambientWith(null)
            return
        }
        if (wiimArt?.first == url) return
        wiim?.art(url) { bitmap ->
            handler.post {
                if (bitmap == null || wiimTrack?.artUrl != url) return@post
                wiimArt = url to bitmap
                aurora.ambientWith(Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0))
                renderOutput()
            }
        }
    }

    private fun showWiimMenu(output: AudioOutput) {
        val wiim = wiim ?: return
        wiim.inputs { inputs ->
            handler.post {
                val status = wiimStatus
                val muted = status?.muted == true
                val playing = status?.playing == true
                val rows = listOfNotNull(
                    MenuRow(
                        "Now playing",
                        listOf(
                            MenuOption("Previous", icon(R.drawable.icon_skip_previous), output.accent) { wiim.previous(::afterWiimCommand) },
                            MenuOption(if (playing) "Pause" else "Play", icon(if (playing) R.drawable.icon_pause else R.drawable.icon_play_arrow), output.accent) {
                                wiim.togglePlayback(::afterWiimCommand)
                            },
                            MenuOption("Next", icon(R.drawable.icon_skip_next), output.accent) { wiim.next(::afterWiimCommand) },
                        ),
                    ).takeIf { status?.ownSource == true },
                    MenuRow(
                        "Input",
                        inputs.map { input ->
                            MenuOption(input.label, icon(input.icon), output.accent, active = status?.mode in input.modes) { useWiimInput(output, input) }
                        },
                    ).takeIf { inputs.isNotEmpty() },
                    MenuRow(
                        "Volume",
                        Lobby.wiimVolumePresets.map { (label, volume) ->
                            MenuOption("$label · $volume", icon(R.drawable.icon_volume_up), output.accent, active = status?.volume == volume) {
                                wiim.setVolume(volume, ::afterWiimCommand)
                            }
                        } + MenuOption(if (muted) "Unmute" else "Mute", icon(if (muted) R.drawable.icon_volume_up else R.drawable.icon_volume_off), output.accent) {
                            wiim.setMuted(!muted, ::afterWiimCommand)
                        },
                    ),
                    MenuRow(
                        "Device",
                        listOf(MenuOption("Restart WiiM", icon(R.drawable.icon_restart_alt), Menu.DANGER) { wiim.restart(::afterWiimCommand) }),
                    ),
                )
                Menu(this, output.label, icon(output.icon), rows).show()
            }
        }
    }

    private fun useWiimInput(output: AudioOutput, input: WiiM.Input) {
        if (input.modes.contains(WiiM.MODE_HDMI)) return select(output)
        // TV sound goes to the TV speakers first: while it still goes to the WiiM over eARC, the TV pulls the WiiM back to HDMI.
        Lobby.outputs.firstOrNull { it.wiimHost == null }?.let(::select)
        handler.postDelayed({ wiim?.switchTo(input, ::afterWiimCommand) }, 2_000)
    }

    private fun afterWiimCommand(accepted: Boolean) {
        handler.post {
            if (!accepted) toast("The WiiM didn't accept that")
            refreshDevicesIn(1_500)
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Nothing on this TV opens that", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appKey(app: ResolveInfo) = "app:${app.activityInfo.packageName}/${app.activityInfo.name}"

    private fun rememberLastUsed(key: String) = prefs.edit().putString(LAST_USED, key).apply()

    private companion object {
        const val LAST_USED = "last_used_tile"
        const val HIDDEN_APPS = "hidden_apps"

        // HdmiControlManager.POWER_STATUS_* (system API, so not visible to apps).
        const val CEC_POWER_ON = 0
        const val CEC_POWER_STANDBY = 1
        const val CEC_POWER_TRANSIENT_TO_ON = 2
        const val CEC_POWER_TRANSIENT_TO_STANDBY = 3
    }
}
