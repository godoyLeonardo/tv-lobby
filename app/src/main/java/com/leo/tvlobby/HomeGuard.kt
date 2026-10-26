package com.leo.tvlobby

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

// Redoes deploy.sh's setup when a firmware update undoes it: the stock homes come back enabled
// (their HOME filters outrank ours) or the audio permission is revoked.
object HomeGuard {
    private val STOCK_HOMES = listOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith")

    // Only boot may bring up the lobby: an update or the lobby's own start must leave the screen alone.
    fun ensure(context: Context, showHome: Boolean = false, done: () -> Unit = {}) {
        val pkg = context.packageName
        val home = context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val canSwitchAudio = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        val commands = buildList {
            if (!canSwitchAudio) add("pm grant $pkg ${Manifest.permission.WRITE_SECURE_SETTINGS}")
            if (home != pkg) {
                STOCK_HOMES.forEach { add("pm disable-user --user 0 $it") }
                add("cmd package set-home-activity $pkg/${MainActivity::class.java.name}")
                if (showHome) add("am start -a ${Intent.ACTION_MAIN} -c ${Intent.CATEGORY_HOME}")
            }
        }
        if (commands.isEmpty()) return done()
        val adb = LocalAdb(context)
        adb.run(commands.joinToString("; "), onError = { done() }) { done() }
        adb.close()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RemoteService.start(context)
        val pending = goAsync()
        HomeGuard.ensure(context.applicationContext, showHome = intent.action == Intent.ACTION_BOOT_COMPLETED) { pending.finish() }
    }
}
