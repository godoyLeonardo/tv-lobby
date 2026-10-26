package com.leo.tvlobby

import android.content.Context

// Pinned order and hidden apps, edited from the lobby and kept on the TV; Lobby.kt only seeds them.
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("lobby", Context.MODE_PRIVATE)

    var pinned: List<String>
        get() = prefs.getString(PINNED, null)?.split('\n')?.filter(String::isNotEmpty) ?: Lobby.favoriteApps
        private set(value) = prefs.edit().putString(PINNED, value.joinToString("\n")).apply()

    var hidden: Set<String>
        get() = prefs.getStringSet(HIDDEN, null) ?: Lobby.hiddenApps
        private set(value) = prefs.edit().putStringSet(HIDDEN, value).apply()

    fun pinFirst(pkg: String) {
        pinned = listOf(pkg) + (pinned - pkg)
    }

    fun unpin(pkg: String) {
        pinned = pinned - pkg
    }

    fun hide(pkg: String) {
        hidden = hidden + pkg
    }

    fun unhide(pkg: String) {
        hidden = hidden - pkg
    }

    private companion object {
        const val PINNED = "pinned_apps"
        const val HIDDEN = "hidden_apps"
    }
}
