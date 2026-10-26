package com.leo.tvlobby

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.util.concurrent.Executors

// Shell commands that need system permissions (HDMI-CEC) go through the TV's own network ADB.
// The first connection shows the TV's "Allow debugging?" prompt for this app's key.
class LocalAdb(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    private val keyPair by lazy {
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val private = File(dir, "adbkey")
        val public = File(dir, "adbkey.pub")
        if (!private.exists()) AdbKeyPair.generate(private, public)
        AdbKeyPair.read(private, public)
    }

    fun run(command: String, onError: (Throwable) -> Unit) = executor.execute {
        runCatching { Dadb.create("127.0.0.1", 5555, keyPair).use { it.shell(command) } }.onFailure(onError)
    }

    // Wakes the CEC device on that HDMI port and makes it the active source; a device absent from CEC is left alone.
    fun wakeHdmiDevice(port: Int, onError: (Throwable) -> Unit) = run(
        "la=\$(dumpsys hdmi_control 2>/dev/null | grep -m1 -E 'CEC: logical_address: 0x[0-9a-f]+ .*port_id: $port\$'" +
            " | sed -E 's/.*logical_address: 0x([0-9a-f]+).*/\\1/'); " +
            "[ -n \"\$la\" ] && cmd hdmi_control deviceselect \$((16#\$la))",
        onError,
    )
}
