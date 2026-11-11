package com.plaintextlab.dumblauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's Start screen: an ordered list of pinned tiles, each with a size and a colour.
 * Saved in preferences as JSON. The first time, it is filled from what Start showed before
 * (clock, calendar, weather, photos, music and the number-key apps), so nothing disappears.
 */
object StartTiles {
    /** Tile sizes, in small squares (the grid is 6 small squares wide). */
    enum class Size(val w: Int, val h: Int, val label: String) {
        SMALL(1, 1, "Small"), MEDIUM(2, 2, "Medium"), WIDE(4, 2, "Wide"), LARGE(4, 4, "Large")
    }

    /**
     * [id] is "app:<target>" for an app or shortcut, or one of the live tiles below.
     * [colour] null = automatic (the app's own colour; see-through for clock / calendar / photos / music).
     */
    data class Spec(val id: String, val size: Size, val colour: Int? = null) {
        val target get() = if (id.startsWith(APP)) id.removePrefix(APP) else null
    }

    const val APP = "app:"
    const val CLOCK = "clock"
    const val CALENDAR = "calendar"
    const val WEATHER = "weather"
    const val PHOTOS = "photos"
    const val MUSIC = "music"
    const val PEOPLE = "people"
    val LIVE = listOf(CLOCK, CALENDAR, WEATHER, PHOTOS, MUSIC, PEOPLE)

    /** "See-through" colour choice: lets the wallpaper show, like transparent tiles. */
    const val GLASS = 0x38FFFFFF

    private const val KEY = "start_tiles"

    fun load(c: Context): MutableList<Spec> {
        Prefs.get(c, KEY)?.let { s ->
            try {
                val arr = JSONArray(s)
                return (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Spec(o.getString("id"), Size.valueOf(o.getString("s")), if (o.has("c")) o.getInt("c") else null)
                }.toMutableList()
            } catch (e: Exception) { /* fall through to the default layout */ }
        }
        // Saved straight away: Start only changes when the user changes it.
        return defaults(c).also { save(c, it) }
    }

    /** What a live tile opens when tapped, if the user chose an app (else the usual one). */
    fun openKey(id: String) = "tile_open_$id"

    fun save(c: Context, tiles: List<Spec>) {
        val arr = JSONArray()
        tiles.forEach { t -> arr.put(JSONObject().put("id", t.id).put("s", t.size.name).apply { t.colour?.let { put("c", it) } }) }
        Prefs.set(c, KEY, arr.toString())
    }

    /**
     * The first layout: live tiles, the number-key apps the user set (1-4 medium, 5-9 small),
     * else the phone's own Phone, Messages, Contacts… Never the "most used" apps, which shift.
     */
    private fun defaults(c: Context): MutableList<Spec> {
        val out = mutableListOf(Spec(CLOCK, Size.MEDIUM), Spec(CALENDAR, Size.MEDIUM))
        val set = (1..9).mapNotNull { n -> Prefs.get(c, Prefs.num(n))?.let { n to it } }
        val keys = set.ifEmpty {
            Apps.defaultSlots(c).filterNotNull().filter { it != Prefs.SPECIAL_APPS }.take(9).mapIndexed { i, t -> i + 1 to t }
        }
        keys.firstOrNull()?.let { (_, t) -> out += Spec(APP + t, Size.MEDIUM) }
        if (Weather.enabled(c)) out += Spec(WEATHER, Size.MEDIUM)
        if (Photos.enabled(c)) out += Spec(PHOTOS, Size.WIDE)
        out += Spec(PEOPLE, Size.MEDIUM)
        out += Spec(MUSIC, Size.WIDE)
        keys.drop(1).forEach { (n, t) -> out += Spec(APP + t, if (n in 1..4) Size.MEDIUM else Size.SMALL) }
        return out.distinctBy { it.id }.toMutableList()
    }

    fun isPinned(c: Context, target: String) = load(c).any { it.target == target }

    fun pin(c: Context, target: String) {
        val list = load(c)
        if (list.none { it.target == target }) { list += Spec(APP + target, Size.MEDIUM); save(c, list) }
    }

    fun unpin(c: Context, id: String) = save(c, load(c).filter { it.id != id })

    /** Live tiles that are not on Start, for "Add a live tile". */
    fun missingLive(c: Context) = LIVE.filter { id -> load(c).none { it.id == id } }

    fun addLive(c: Context, id: String) {
        val list = load(c)
        if (list.none { it.id == id }) {
            list += Spec(id, if (id == PHOTOS || id == MUSIC) Size.WIDE else Size.MEDIUM); save(c, list)
        }
    }

    /** Sizes that make sense for a tile (live tiles need room for their content). */
    fun sizesFor(id: String) = when (id) {
        PHOTOS, MUSIC, PEOPLE -> listOf(Size.MEDIUM, Size.WIDE, Size.LARGE)
        else -> Size.values().toList()
    }

    fun liveName(id: String) = when (id) {
        CLOCK -> "Clock"; CALENDAR -> "Calendar"; WEATHER -> "Weather"; PHOTOS -> "Photos"; MUSIC -> "Music"; PEOPLE -> "People"; else -> id
    }

    /** Move the tile at [from] to [to] (indexes in the list). */
    fun move(list: MutableList<Spec>, from: Int, to: Int) {
        if (from !in list.indices) return
        val t = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), t)
    }
}
