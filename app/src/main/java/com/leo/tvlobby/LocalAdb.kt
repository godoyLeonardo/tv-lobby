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

    fun run(command: String, onError: (Throwable) -> Unit, onOutput: (String) -> Unit = {}) = executor.execute {
        runCatching { Dadb.create("127.0.0.1", 5555, keyPair).use { it.shell(command).output } }
            .onSuccess(onOutput)
            .onFailure(onError)
    }

    // Wakes the CEC device on that HDMI port and makes it the active source.
    fun wakeHdmiDevice(port: Int, onError: (Throwable) -> Unit, onAbsent: () -> Unit = {}) =
        onCecDevice(port, "cmd hdmi_control deviceselect \$la", onError, onAbsent)

    fun standbyHdmiDevice(port: Int, onError: (Throwable) -> Unit, onAbsent: () -> Unit) =
        onCecDevice(port, "$cecTool standby \$la", onError, onAbsent)

    // Puts every CEC device to sleep, then the TV.
    fun goodnight(onError: (Throwable) -> Unit) = run(
        "las=\$(dumpsys hdmi_control 2>/dev/null | grep -E 'CEC: logical_address: .*port_id: [1-9][0-9]*\$' | sort -u" +
            " | sed -E 's/.*logical_address: 0x([0-9a-f]+).*/\\1/' | while read la; do echo \$((16#\$la)); done); " +
            "[ -n \"\$las\" ] && $cecTool standby \$las; input keyevent KEYCODE_SLEEP",
        onError,
    )

    private val cecTool get() = "CLASSPATH=\$(pm path ${context.packageName} | sed 's/package://') app_process /system/bin ${CecTool::class.java.name}"

    // CEC power status of each device in the TV's CEC list, keyed by HDMI port; a device asleep without CEC is absent.
    fun cecPowerByPort(onResult: (Map<Int, Int>) -> Unit) = run(
        "dumpsys hdmi_control 2>/dev/null | grep -E 'CEC: logical_address: .*port_id: [1-9][0-9]*\$' | sort -u",
        onError = {},
    ) { output ->
        onResult(output.lines().mapNotNull(CEC_DEVICE::find).associate { it.groupValues[2].toInt() to it.groupValues[1].toInt() })
    }

    // Runs `action` with $la set to the logical address of the CEC device on that port, or reports it absent.
    private fun onCecDevice(port: Int, action: String, onError: (Throwable) -> Unit, onAbsent: () -> Unit) = run(
        "la=\$(dumpsys hdmi_control 2>/dev/null | grep -m1 -E 'CEC: logical_address: 0x[0-9a-f]+ .*port_id: $port\$'" +
            " | sed -E 's/.*logical_address: 0x([0-9a-f]+).*/\\1/'); " +
            "if [ -n \"\$la\" ]; then la=\$((16#\$la)); $action; else echo $ABSENT; fi",
        onError,
    ) { if (it.trim() == ABSENT) onAbsent() }

    private companion object {
        const val ABSENT = "no-cec-device"
        val CEC_DEVICE = Regex("""power_status: (-?\d+) .*port_id: (\d+)$""")
    }
}
