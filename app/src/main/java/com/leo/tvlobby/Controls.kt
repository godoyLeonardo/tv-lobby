package com.leo.tvlobby

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.provider.Settings

// What the lobby and the phone remote both do to the TV, the HDMI devices and the WiiM.
class Controls(private val context: Context) {
    val localAdb = LocalAdb(context)
    val wiim = Lobby.outputs.firstNotNullOfOrNull { it.wiimHost }?.let(::WiiM)

    val currentOutput get() = Settings.Global.getInt(context.contentResolver, Lobby.OUTPUT_SETTING, 1)

    fun passthroughIntent(input: Input): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(TvContract.buildChannelUriForPassthroughInput(input.inputId), TvContract.Channels.CONTENT_ITEM_TYPE)

    // False when the audio permission is missing. Choosing the WiiM also switches it to TV sound.
    fun selectOutput(output: AudioOutput, onWiim: (Boolean) -> Unit = {}): Boolean {
        val written = try {
            Settings.Global.putInt(context.contentResolver, Lobby.OUTPUT_SETTING, output.tclType)
        } catch (e: SecurityException) {
            false
        }
        if (output.wiimHost != null) wiim?.useTvSound(onWiim)
        return written
    }
}
