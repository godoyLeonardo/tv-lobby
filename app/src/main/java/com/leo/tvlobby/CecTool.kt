package com.leo.tvlobby

import android.os.IBinder
import android.os.Parcel

// Runs as the shell user through app_process (see LocalAdb), which holds HDMI_CEC.
// `service call` can't be used: TCL wraps hdmi_control in its own interface, so the shell stamps the wrong token.
object CecTool {
    // IHdmiControlService.sendStandby(deviceType, deviceId) on this TV's framework (Android 14).
    private const val TRANSACTION_SEND_STANDBY = 37
    private const val DEVICE_TYPE_TV = 0

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
            hdmi.transact(TRANSACTION_SEND_STANDBY, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
