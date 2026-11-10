package com.plaintextlab.dumblauncher

import android.content.Context

/** Stored targets are flattened ComponentNames, or one of the SPECIAL_ values. */
object Prefs {
    const val UP = "dpad_up"
    const val DOWN = "dpad_down"
    const val LEFT = "dpad_left"
    const val RIGHT = "dpad_right"
    const val SPECIAL_APPS = "special:apps"

    val DPAD = listOf(UP to "▲ Up", DOWN to "▼ Down", LEFT to "◀ Left", RIGHT to "▶ Right")

    fun slot(n: Int) = "slot$n"

    private fun sp(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun get(c: Context, key: String): String? = sp(c).getString(key, null)?.takeIf { it.isNotEmpty() }
    fun set(c: Context, key: String, value: String?) = sp(c).edit().putString(key, value ?: "").apply()

    fun ensureDefaults(c: Context) {
        if (sp(c).getBoolean("defaults_done", false)) return
        val e = sp(c).edit()
        Apps.defaultSlots(c).forEachIndexed { i, v -> e.putString(slot(i + 1), v ?: "") }
        e.putBoolean("defaults_done", true).apply()
    }

    fun resetSlots(c: Context) {
        sp(c).edit().putBoolean("defaults_done", false).apply()
        ensureDefaults(c)
    }
}
