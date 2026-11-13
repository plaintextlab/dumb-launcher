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
    /** The icon style to go back to when switching from the Windows Phone look to the Android look. */
    const val CLASSIC_STYLE = "classic_style"
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
    const val SHOW_CLOCK = "show_clock"
    const val SHOW_WEATHER = "show_weather"
    const val WEATHER_F = "weather_f"
    const val SHOW_PHOTOS = "show_photos"
    const val TILE_COLOURS = "tile_colours"   // true = each app's own colour, false = accent
    const val SHOW_DOCK = "show_dock"
    const val DRAWER_GRID = "drawer_grid"
    const val SORT_USED = "drawer_sort_used"

    fun iconOverride(target: String) = "icon:$target"

    /** A name the user gave an app or shortcut, shown instead of its own label. */
    fun customLabel(c: Context, target: String): String? = get(c, "label:$target")
    fun setCustomLabel(c: Context, target: String, label: String?) =
        set(c, "label:$target", label?.trim()?.takeIf { it.isNotEmpty() })

    val DPAD = listOf(UP to "▲ Up", DOWN to "▼ Down", LEFT to "◀ Left", RIGHT to "▶ Right")

    /** Home number-key shortcut 0-9. */
    fun num(n: Int) = "num$n"

    private fun sp(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun get(c: Context, key: String): String? = sp(c).getString(key, null)?.takeIf { it.isNotEmpty() }
    fun set(c: Context, key: String, value: String?) = sp(c).edit().putString(key, value ?: "").apply()

    // ---- apps hidden from the drawer, search and the automatic dock ----
    private const val HIDDEN = "hidden_apps"
    fun hidden(c: Context): Set<String> = get(c, HIDDEN)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
    fun setHidden(c: Context, target: String, hide: Boolean) =
        set(c, HIDDEN, (if (hide) hidden(c) + target else hidden(c) - target).joinToString("\n"))

    fun getInt(c: Context, key: String, def: Int) = sp(c).getInt(key, def)
    fun setInt(c: Context, key: String, value: Int) = sp(c).edit().putInt(key, value).apply()

    fun getBool(c: Context, key: String, def: Boolean) = sp(c).getBoolean(key, def)
    fun setBool(c: Context, key: String, value: Boolean) = sp(c).edit().putBoolean(key, value).apply()

}
