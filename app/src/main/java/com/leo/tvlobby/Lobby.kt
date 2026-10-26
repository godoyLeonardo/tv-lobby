package com.leo.tvlobby

data class Input(
    val label: String,
    val hdmiPort: Int,
    val inputId: String,
    val mark: Int,
    val colors: IntArray,
    // Shown when the device is asleep and not on HDMI-CEC, so the TV can't wake it.
    val wakeHint: String = "Wake it from its own remote or controller.",
)

data class AudioOutput(
    val label: String,
    val detail: String,
    val icon: Int,
    val tclType: Int,
    val accent: Int,
    // LAN address of a WiiM streamer behind this output, from `wiim` in lobby.properties.
    val wiimHost: String? = null,
)

object Lobby {
    // TvInputService ids of this TCL's HDMI ports (`adb shell dumpsys tv_input`): HW15 is HDMI 1, HW18 is HDMI 4.
    val inputs = listOf(
        Input("Switch 2", 2, "com.tcl.tvinput/.TvPassThroughService/HW16", R.drawable.mark_switch, intArrayOf(0xFFB3000E.toInt(), 0xFFFF2A2A.toInt()), "Press HOME on a controller to wake it."),
        Input("PS5", 3, "com.tcl.tvinput/.TvPassThroughService/HW17", R.drawable.mark_playstation, intArrayOf(0xFF00205C.toInt(), 0xFF0070D1.toInt())),
        Input("Apple TV", 4, "com.tcl.tvinput/.TvPassThroughService/HW18", R.drawable.mark_apple, intArrayOf(0xFF1C1C1E.toInt(), 0xFF48484A.toInt())),
    )

    // TCL's own sound-output switch: TvSettings writes EnSound.SoundOutput codes to this Global setting
    // and the TCL audio service reroutes when it changes.
    const val OUTPUT_SETTING = "tcl_device_out_type"

    val outputs = listOf(
        AudioOutput("TV speakers", "Internal", R.drawable.icon_tv, 2, 0xFFF5A623.toInt()),
        AudioOutput("WiiM Ultra", "HDMI eARC", R.drawable.icon_speaker, 16, 0xFF2EC4B6.toInt(), wiimHost = BuildConfig.WIIM_HOST.ifEmpty { null }),
    )

    // Defaults only: long-press an app in the lobby to pin, move or hide it, which is saved on the TV.
    // Long-press the WiiM tile for these.
    val wiimVolumePresets = listOf("Night" to 20, "Normal" to 40)

    val favoriteApps = listOf(
        "com.google.android.youtube.tv",
        "com.plexapp.android",
        "com.eltelon.zapping",
        "com.google.android.youtube.tvmusic",
    )

    val hiddenApps = setOf("com.tcl.suspension")
}
