package com.leo.tvlobby

import android.os.IBinder
import android.os.Parcel

// Runs as the shell user through app_process (see LocalAdb), which holds HDMI_CEC.
// `service call` can't be used: TCL wraps hdmi_control in its own interface, so the shell stamps the wrong token.
object CecTool {
    private const val DEVICE_TYPE_TV = 0

    // Read from the TV's framework rather than hardcoded: the AIDL numbering can shift with a firmware update.
    private val sendStandbyTransaction: Int
        get() = Class.forName("android.hardware.hdmi.IHdmiControlService\$Stub").getDeclaredField("TRANSACTION_sendStandby")
            .apply { isAccessible = true }
            .getInt(null)

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "standby" -> standby(args[1].toInt())
            else -> error("usage: standby <logical address>")
        }
    }

    private fun standby(logicalAddress: Int) {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val hdmi = serviceManager.getMethod("getService", String::class.java).invoke(null, "hdmi_control") as IBinder
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("android.hardware.hdmi.IHdmiControlService")
            data.writeInt(DEVICE_TYPE_TV)
            data.writeInt(logicalAddress)
            hdmi.transact(sendStandbyTransaction, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
