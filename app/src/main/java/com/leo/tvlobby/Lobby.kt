package com.leo.tvlobby

data class Input(val label: String, val inputId: String, val mark: Int, val colors: IntArray)

data class AudioOutput(val label: String, val detail: String, val tclType: Int, val accent: Int)

object Lobby {
    // TvInputService ids of this TCL's HDMI ports (`adb shell dumpsys tv_input`): HW15 is HDMI 1, HW18 is HDMI 4.
    val inputs = listOf(
        Input("Switch 2", "com.tcl.tvinput/.TvPassThroughService/HW16", R.drawable.mark_switch, intArrayOf(0xFFB3000E.toInt(), 0xFFFF2A2A.toInt())),
        Input("PS5", "com.tcl.tvinput/.TvPassThroughService/HW17", R.drawable.mark_playstation, intArrayOf(0xFF00205C.toInt(), 0xFF0070D1.toInt())),
        Input("Apple TV", "com.tcl.tvinput/.TvPassThroughService/HW18", R.drawable.mark_appletv, intArrayOf(0xFF1C1C1E.toInt(), 0xFF48484A.toInt())),
    )

    // TCL's own sound-output switch: TvSettings writes EnSound.SoundOutput codes to this Global setting
    // and the TCL audio service reroutes when it changes.
    const val OUTPUT_SETTING = "tcl_device_out_type"

    val outputs = listOf(
        AudioOutput("TV speakers", "Internal", 2, 0xFFF5A623.toInt()),
        AudioOutput("WiiM Ultra", "HDMI eARC", 16, 0xFF2EC4B6.toInt()),
    )

    val favoriteApps = listOf(
        "com.google.android.youtube.tv",
        "com.plexapp.android",
        "com.eltelon.zapping",
        "com.google.android.youtube.tvmusic",
    )

    val hiddenApps = setOf("com.tcl.suspension")
}
