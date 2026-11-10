package com.plaintextlab.dumblauncher

import android.content.Context

/** Stored targets are flattened ComponentNames, or one of the SPECIAL_ values. */
object Prefs {
    const val UP = "dpad_up"
    const val DOWN = "dpad_down"
    const val LEFT = "dpad_left"
    const val RIGHT = "dpad_right"
    const val SPECIAL_APPS = "special:apps"
    const val HIGHLIGHT = "highlight"
    const val ICON_STYLE = "icon_style"
    const val ICON_PACK = "icon_pack"
    const val WALL_MODE = "wall_mode"
    const val WALL_DIM = "wall_dim"
    const val HIDE_STATUS = "hide_status"
    const val SHOW_COUNTS = "show_counts"
    const val ZOOM = "zoom"
    const val ANIM = "anim"
    const val SHOW_DPAD = "show_dpad"
    const val DRAWER_BG = "drawer_bg"
    const val SHOW_MUSIC = "show_music"

    fun iconOverride(target: String) = "icon:$target"

    val DPAD = listOf(UP to "▲ Up", DOWN to "▼ Down", LEFT to "◀ Left", RIGHT to "▶ Right")

    fun slot(n: Int) = "slot$n"

    /** Home number-key shortcut 0-9. */
    fun num(n: Int) = "num$n"

    private fun sp(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun get(c: Context, key: String): String? = sp(c).getString(key, null)?.takeIf { it.isNotEmpty() }
    fun set(c: Context, key: String, value: String?) = sp(c).edit().putString(key, value ?: "").apply()

    fun getInt(c: Context, key: String, def: Int) = sp(c).getInt(key, def)
    fun setInt(c: Context, key: String, value: Int) = sp(c).edit().putInt(key, value).apply()

    fun getBool(c: Context, key: String, def: Boolean) = sp(c).getBoolean(key, def)
    fun setBool(c: Context, key: String, value: Boolean) = sp(c).edit().putBoolean(key, value).apply()

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
