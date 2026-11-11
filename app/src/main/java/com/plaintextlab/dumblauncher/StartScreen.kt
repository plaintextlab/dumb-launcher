package com.plaintextlab.dumblauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/**
 * Home in the Metro style: a mosaic of flat tiles on a grid six small squares wide.
 * A medium tile is 2x2 squares (three across), a small tile one square (four of them pack
 * into one medium cell), a wide tile 4x2.
 *
 * - see-through live tiles for the clock and the date (the date tile flips now and then),
 * - a wide now-playing tile while music plays,
 * - medium tiles for number keys 1-4, small tiles for 5-9, 0 and the arrow keys,
 * - each tile in its app's own colour, with a round badge for missed calls, unread
 *   messages and other notifications; a small key number shows which key opens it.
 */
class StartScreen(private val a: Activity, private val column: LinearLayout, private val onOpen: (Int) -> Unit) {

    data class Tile(val key: Int, val entry: AppEntry, val small: Boolean, val tag: String)

    private val dp = a.resources.displayMetrics.density
    private val gap = (4 * dp).toInt()
    private var u = 0                   // one small square
    private val medium get() = 2 * u + gap
    private var dateFace = 0
    private var dateTile: FrameLayout? = null
    private var musicTile: FrameLayout? = null

    /** The rows, top to bottom (for the turnstile). */
    val rows = mutableListOf<View>()

    private sealed class Cell { abstract val span: Int }
    private class One(val view: View) : Cell() { override val span = 1 }
    private class Two(val view: View) : Cell() { override val span = 2 }

    fun build(counts: Map<String, Int>, animate: Boolean = true) {
        column.removeAllViews(); rows.clear(); focusables.clear(); weatherTile = null; photosTile = null
        val width = a.resources.displayMetrics.widthPixels - column.paddingLeft - column.paddingRight
        u = (width - 5 * gap) / 6

        val cells = mutableListOf<Cell>()
        cells += One(clockTile())
        cells += One(dateTile().also { dateTile = it })
        val tiles = tiles()
        val mediums = tiles.filter { !it.small }
        val smalls = tiles.filter { it.small }
        // Interleave like a real Start screen: a medium, a block of four smalls, a medium...
        val blocks = smalls.chunked(4).map { block -> One(smallBlock(block.map { tile(it, counts) })) }
        mediums.firstOrNull()?.let { cells += One(tile(it, counts)) }
        if (Weather.enabled(a)) cells += One(weatherTileView().also { weatherTile = it; fillWeather() })
        if (Photos.enabled(a)) cells += Two(photosTileView().also { photosTile = it; showPhoto() })
        cells += Two(musicTileView().also { musicTile = it })
        var b = 0
        for (m in mediums.drop(1)) {
            cells += One(tile(m, counts))
            if (b < blocks.size && cells.size % 2 == 0) cells += blocks[b++]
        }
        while (b < blocks.size) cells += blocks[b++]
        pack(cells)

        addRow(TextView(a).apply {
            text = "all apps  ›"; textSize = 18f; setTextColor(a.getColor(R.color.fg))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.END; setPadding(0, (10 * dp).toInt(), (6 * dp).toInt(), (20 * dp).toInt())
            setOnClickListener { onOpen(ALL_APPS) }
            selectable(this)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (animate) Turnstile.featherIn(column) else column.layoutAnimation = null
    }

    /** Lay cells into rows three medium cells wide; a wide cell that won't fit starts a new row. */
    private fun pack(cells: List<Cell>) {
        var row = newRow(); var used = 0
        for (c in cells) {
            if (c is Two && c.view.visibility == View.GONE) {   // hidden wide tile: its own (empty) row
                addRow(c.view, LinearLayout.LayoutParams(2 * medium + gap, medium)); continue
            }
            if (used + c.span > 3) { addRow(row, rowParams()); row = newRow(); used = 0 }
            val v = when (c) { is One -> c.view; is Two -> c.view }
            val w = if (c.span == 2) 2 * medium + gap else medium
            row.addView(v, LinearLayout.LayoutParams(w, medium).apply { if (used > 0) leftMargin = gap })
            used += c.span
        }
        if (used > 0) addRow(row, rowParams())
    }

    private fun newRow() = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
    private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun addRow(v: View, lp: LinearLayout.LayoutParams) {
        lp.bottomMargin = gap
        column.addView(v, lp); rows += v
    }

    /** Number keys 1-4 as medium tiles; 5-9 and 0 as small ones. Empty keys are left out.
     *  (The arrow keys move the selection here, so they have no apps of their own.) */
    private fun tiles(): List<Tile> {
        val out = mutableListOf<Tile>()
        val dock = if (Dock.enabled(a)) Dock.slots(a).associate { it.key to it.entry } else emptyMap()
        for (n in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)) {
            val e = dock[n] ?: Apps.entry(a, Prefs.get(a, Prefs.num(n))) ?: continue
            out += Tile(n, e, small = n !in 1..4, tag = "$n")
        }
        return out
    }

    /** Four small tiles in one medium cell, 2x2. */
    private fun smallBlock(tiles: List<View>) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        tiles.chunked(2).forEachIndexed { r, pair ->
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                pair.forEachIndexed { i, t -> addView(t, LinearLayout.LayoutParams(u, u).apply { if (i > 0) leftMargin = gap }) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u).apply { if (r > 0) topMargin = gap })
        }
    }

    private fun box(colour: Int) = FrameLayout(a).apply {
        background = ColorDrawable(colour)
        Turnstile.tilt(this)
        selectable(this)
    }

    /** Arrow keys move between tiles; the selected one gets a white frame. */
    private fun selectable(v: View) {
        v.isFocusable = true
        v.foreground = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                setStroke((3 * dp).toInt(), Color.WHITE); setColor(Color.TRANSPARENT)
            })
        }
        v.setOnFocusChangeListener { view, has ->
            if (has) {
                lastFocus = focusables.indexOf(view)
                // Keep the whole tile on screen, with a little room around it.
                view.post { view.requestRectangleOnScreen(android.graphics.Rect(0, -gap * 3, view.width, view.height + gap * 3)) }
            }
        }
        focusables += v
    }

    private val focusables = mutableListOf<View>()
    private var lastFocus = 0

    /** Select the tile that was selected before a rebuild (or the first one). */
    fun restoreFocus() {
        (focusables.getOrNull(lastFocus) ?: focusables.firstOrNull())?.requestFocus()
    }

    /** See-through tiles let the wallpaper show, like transparent live tiles. */
    private val glass = 0x38FFFFFF

    private fun tile(t: Tile, counts: Map<String, Int>): View {
        val side = if (t.small) u else medium
        val v = box(Metro.accent(a))
        val glyph = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        Icons.load(a, t.entry) { d, _ ->
            glyph.setImageDrawable(d)
            // The tile takes the colour of the icon's own square, so the glyph sits seamlessly.
            (d as? BitmapDrawable)?.bitmap?.let { bmp -> v.background = ColorDrawable(bmp.getPixel(1, 1)) }
        }
        val g = if (t.small) side else (side * 0.82f).toInt()
        v.addView(glyph, FrameLayout.LayoutParams(g, g, Gravity.CENTER))
        if (!t.small) v.addView(text(t.entry.label, 13f), corner(Gravity.BOTTOM or Gravity.START))
        v.addView(text(t.tag, 10f).apply { alpha = 0.7f }, corner(Gravity.TOP or Gravity.START, tight = t.small))
        counts[Shortcuts.packageOf(t.entry.target)]?.takeIf { it > 0 }?.let { n -> v.addView(badge(n, t.small), badgeParams(t.small)) }
        v.contentDescription = t.entry.label
        v.setOnClickListener { onOpen(t.key) }
        return v
    }

    /** A round dark badge with the count, in the bottom-right corner. */
    private fun badge(n: Int, small: Boolean) = TextView(a).apply {
        text = if (n > 99) "99+" else "$n"
        textSize = if (small) 12f else 17f
        setTextColor(Color.WHITE); gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val d = ((if (small) 22 else 32) * dp).toInt()
        minWidth = d; minHeight = d
        setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
        background = GradientDrawable().apply { setColor(0xE6202020.toInt()); cornerRadius = d / 2f }
    }

    private fun badgeParams(small: Boolean) = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END
    ).apply { val m = ((if (small) 2 else 6) * dp).toInt(); setMargins(m, m, m, m) }

    private fun text(s: String, size: Float) = TextView(a).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setShadowLayer(4f, 0f, 1f, 0x66000000)
    }

    private fun corner(gravity: Int, tight: Boolean = false) = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity
    ).apply { val m = ((if (tight) 3 else 8) * dp).toInt(); setMargins(m, ((if (tight) 1 else 4) * dp).toInt(), m, (6 * dp).toInt()) }

    // ---- Live tiles ----

    private fun clockTile() = box(glass).apply {
        addView(TextClock(a).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = 30f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(text("Clock", 13f), corner(Gravity.BOTTOM or Gravity.START))
        setOnClickListener { onOpen(CLOCK) }
    }

    private fun dateTile() = box(glass).also { fillDate(it); it.setOnClickListener { onOpen(CALENDAR) } }

    private fun fillDate(t: FrameLayout) {
        t.removeAllViews()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        fun clock(fmt: String, size: Float, light: Boolean) = TextClock(a).apply {
            format12Hour = fmt; format24Hour = fmt; textSize = size; setTextColor(Color.WHITE)
            typeface = Typeface.create(if (light) "sans-serif-light" else "sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL; includeFontPadding = false
        }
        if (dateFace == 0) { col.addView(clock("EEEE", 14f, false)); col.addView(clock("d", 34f, true)) }
        else { col.addView(clock("MMMM", 16f, false)); col.addView(clock("yyyy", 26f, true)) }
        t.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = (12 * dp).toInt() })
        t.addView(text("Calendar", 13f), corner(Gravity.BOTTOM or Gravity.START))
    }

    private var ticks = 0

    /** Called every second while Home is showing: live tiles change now and then, out of step. */
    fun tick() {
        ticks++
        dateTile?.let { t -> if (ticks % 8 == 0) Turnstile.flip(t) { dateFace = 1 - dateFace; fillDate(t) } }
        photosTile?.let { t -> if (ticks % 6 == 3 && photos.size > 1) Turnstile.flip(t) { photoIndex++; showPhoto() } }
        weatherTile?.let { t -> if (ticks % 10 == 5 && weather != null) Turnstile.flip(t) { weatherFace = 1 - weatherFace; fillWeather() } }
    }

    // Weather: a blue tile with the temperature and a symbol; the back shows today's range.
    private var weather: Weather.Now? = null
    private var weatherFace = 0
    private var weatherTile: FrameLayout? = null

    private fun weatherTileView() = box(0xFF0078D7.toInt()).apply { setOnClickListener { onOpen(WEATHER) } }

    /** New weather data (or null while there is none yet). */
    fun weather(now: Weather.Now?) { weather = now; fillWeather() }

    private fun fillWeather() {
        val t = weatherTile ?: return
        t.removeAllViews()
        val w = weather
        val f = "°"
        if (w == null) {
            t.addView(ImageView(a).apply { setImageResource(R.drawable.w_partly) },
                FrameLayout.LayoutParams((medium * 0.42f).toInt(), (medium * 0.42f).toInt(), Gravity.CENTER))
            t.addView(text(if (Weather.hasPermission(a)) "Weather" else "Weather · allow location", 13f), corner(Gravity.BOTTOM or Gravity.START))
            return
        }
        if (weatherFace == 0) {
            t.addView(ImageView(a).apply { setImageResource(w.icon) },
                FrameLayout.LayoutParams((medium * 0.38f).toInt(), (medium * 0.38f).toInt(), Gravity.TOP or Gravity.END)
                    .apply { setMargins(0, (8 * dp).toInt(), (8 * dp).toInt(), 0) })
            t.addView(text("${w.temp}$f", 34f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL)
                    .apply { leftMargin = (8 * dp).toInt() })
        } else {
            val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(w.text, 15f))
            col.addView(text("${w.high}$f / ${w.low}$f", 22f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
            t.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = (8 * dp).toInt() })
        }
        t.addView(text(w.place.ifEmpty { "Weather" }, 13f), corner(Gravity.BOTTOM or Gravity.START))
    }

    // Photos: a wide tile that shows your recent photos one after another.
    private var photos: List<android.graphics.Bitmap> = emptyList()
    private var photoIndex = 0
    private var photosTile: FrameLayout? = null

    private fun photosTileView() = box(0xFF2D2D30.toInt()).apply { setOnClickListener { onOpen(PHOTOS) } }

    /** Recent photo thumbnails, newest first. */
    fun photos(list: List<android.graphics.Bitmap>) { photos = list; photoIndex = 0; showPhoto() }

    private fun showPhoto() {
        val t = photosTile ?: return
        t.removeAllViews()
        if (photos.isEmpty()) {
            t.addView(ImageView(a).apply { setImageResource(R.drawable.s_image) },
                FrameLayout.LayoutParams((medium * 0.4f).toInt(), (medium * 0.4f).toInt(), Gravity.CENTER))
        } else {
            t.addView(ImageView(a).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(photos[photoIndex % photos.size])
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        t.addView(text(if (photos.isEmpty() && !Photos.hasPermission(a)) "Photos · allow access" else "Photos", 13f),
            corner(Gravity.BOTTOM or Gravity.START))
    }

    private fun musicTileView() = box(glass).apply { visibility = View.GONE }

    /** Show or hide the now-playing tile. */
    fun music(now: Music.Now?) {
        val t = musicTile ?: return
        if (now == null) { if (t.visibility != View.GONE) { t.visibility = View.GONE; t.tag = null }; return }
        val key = now.key + now.playing
        if (t.visibility == View.VISIBLE && t.tag == key) return
        t.tag = key
        t.removeAllViews()
        val h = medium
        val art = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        if (now.art != null) art.setImageBitmap(now.art) else art.setImageResource(R.drawable.ic_music)
        t.addView(art, FrameLayout.LayoutParams(h, h, Gravity.END))
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(if (now.playing) "Now playing" else "Paused", 12f).apply { alpha = 0.8f })
        col.addView(text(now.title, 18f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        if (now.artist.isNotEmpty()) col.addView(text(now.artist, 13f))
        t.addView(col, FrameLayout.LayoutParams(medium - (12 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START).apply { setMargins((8 * dp).toInt(), 0, 0, (6 * dp).toInt()) })
        t.setOnClickListener { onOpen(MUSIC) }
        t.visibility = View.VISIBLE
    }

    companion object {
        const val ALL_APPS = 100
        const val CLOCK = 101
        const val MUSIC = 102
        const val CALENDAR = 103
        const val WEATHER = 104
        const val PHOTOS = 105
    }
}
