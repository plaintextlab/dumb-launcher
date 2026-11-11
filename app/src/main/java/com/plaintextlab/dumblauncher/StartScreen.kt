package com.plaintextlab.dumblauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/**
 * Home in the Metro style: a column of flat tiles on a 4-unit grid.
 * - a wide live clock tile (flips between time and date),
 * - a wide now-playing tile while music plays,
 * - medium tiles for number keys 1-4, small tiles for 5-9, 0 and the four arrow keys,
 * - counts in the corner of a tile (missed calls on Phone, unread on Messages, others'
 *   notifications), and "all apps" at the bottom.
 * Each tile shows which key opens it, so the keys stay the way you use it.
 */
class StartScreen(private val a: Activity, private val column: LinearLayout, private val onOpen: (Int) -> Unit) {

    /** Called with a tile's key: digit 0-9, or -1..-4 for the arrows (up, down, left, right). */
    data class Tile(val key: Int, val entry: AppEntry, val size: Size, val tag: String)
    enum class Size { SMALL, MEDIUM, WIDE }

    private val dp = a.resources.displayMetrics.density
    private val gap = (6 * dp).toInt()
    private var unit = 0
    private var clockFace = 0
    private var clockTile: FrameLayout? = null
    private var musicTile: FrameLayout? = null

    /** The rows, top to bottom (for the turnstile). */
    val rows = mutableListOf<View>()

    fun build(counts: Map<String, Int>, animate: Boolean = true) {
        column.removeAllViews(); rows.clear()
        val width = a.resources.displayMetrics.widthPixels - (16 * dp).toInt()
        unit = (width - 3 * gap) / 4
        val accent = Metro.accent(a)

        clockTile = wide(accent).also { addRow(it); fillClock(it) }
        musicTile = wide(accent).also { addRow(it); it.visibility = View.GONE }

        val tiles = tiles()
        val medium = tiles.filter { it.size == Size.MEDIUM }
        val small = tiles.filter { it.size == Size.SMALL }
        medium.chunked(2).forEach { pair -> addRow(row(pair.map { tile(it, accent, counts) })) }
        small.chunked(4).forEach { four -> addRow(row(four.map { tile(it, accent, counts) })) }

        addRow(TextView(a).apply {
            text = "all apps  ›"; textSize = 18f; setTextColor(a.getColor(R.color.fg))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.END; setPadding(0, (10 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt())
            setOnClickListener { onOpen(ALL_APPS) }
        })
        if (animate) Turnstile.featherIn(column) else column.layoutAnimation = null
    }

    /** Number keys 1-4 as medium tiles; 5-9, 0 and the arrows as small ones. Empty keys are left out. */
    private fun tiles(): List<Tile> {
        val out = mutableListOf<Tile>()
        val dock = if (Dock.enabled(a)) Dock.slots(a).associate { it.key to it.entry } else emptyMap()
        for (n in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)) {
            val e = dock[n] ?: Apps.entry(a, Prefs.get(a, Prefs.num(n))) ?: continue
            out += Tile(n, e, if (n in 1..4) Size.MEDIUM else Size.SMALL, "$n")
        }
        Prefs.DPAD.forEachIndexed { i, (pref, _) ->
            val e = Apps.entry(a, Prefs.get(a, pref)) ?: return@forEachIndexed
            out += Tile(-(i + 1), e, Size.SMALL, listOf("▲", "▼", "◀", "▶")[i])
        }
        return out
    }

    private fun addRow(v: View) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = gap
        column.addView(v, lp); rows += v
    }

    private fun row(tiles: List<View>) = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        tiles.forEachIndexed { i, t ->
            addView(t, LinearLayout.LayoutParams(t.layoutParams.width, t.layoutParams.height).apply {
                if (i > 0) leftMargin = gap
            })
        }
    }

    private fun box(w: Int, h: Int, accent: Int) = FrameLayout(a).apply {
        layoutParams = ViewGroup.LayoutParams(w, h)
        background = ColorDrawable(accent)
        Turnstile.tilt(this)
    }

    private fun wide(accent: Int) = box(4 * unit + 3 * gap, 2 * unit + gap, accent)

    private fun tile(t: Tile, accent: Int, counts: Map<String, Int>): View {
        val side = if (t.size == Size.MEDIUM) 2 * unit + gap else unit
        val v = box(side, side, accent)
        val glyph = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        Icons.load(a, t.entry) { d, _ -> glyph.setImageDrawable(d) }
        val g = if (t.size == Size.MEDIUM) (side * 0.8f).toInt() else side
        v.addView(glyph, FrameLayout.LayoutParams(g, g, Gravity.CENTER))
        if (t.size == Size.MEDIUM) v.addView(text(t.entry.label, 13f), corner(Gravity.BOTTOM or Gravity.START))
        v.addView(text(t.tag, 11f).apply { alpha = 0.8f }, corner(Gravity.TOP or Gravity.END))
        val pkg = Shortcuts.packageOf(t.entry.target)
        counts[pkg]?.takeIf { it > 0 }?.let { n ->
            v.addView(text("$n", if (t.size == Size.MEDIUM) 22f else 15f), corner(Gravity.BOTTOM or Gravity.END))
        }
        v.contentDescription = t.entry.label
        v.setOnClickListener { onOpen(t.key) }
        return v
    }

    private fun text(s: String, size: Float) = TextView(a).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        typeface = Typeface.create(if (size >= 20f) "sans-serif-light" else "sans-serif", Typeface.NORMAL)
    }

    private fun corner(gravity: Int) = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity
    ).apply { val m = (7 * dp).toInt(); setMargins(m, (4 * dp).toInt(), m, (5 * dp).toInt()) }

    // ---- Live tiles ----

    private fun fillClock(t: FrameLayout) {
        t.removeAllViews()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        if (clockFace == 0) {
            col.addView(TextClock(a).apply {
                format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = 46f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); includeFontPadding = false
            })
            col.addView(TextClock(a).apply {
                format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; textSize = 14f; setTextColor(Color.WHITE)
            })
        } else {
            col.addView(TextClock(a).apply {
                format12Hour = "EEEE"; format24Hour = "EEEE"; textSize = 28f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            })
            col.addView(TextClock(a).apply {
                format12Hour = "d MMMM yyyy"; format24Hour = "d MMMM yyyy"; textSize = 16f; setTextColor(Color.WHITE)
            })
        }
        t.addView(col, corner(Gravity.BOTTOM or Gravity.START).apply { setMargins((10 * dp).toInt(), 0, 0, (8 * dp).toInt()) })
        t.setOnClickListener { onOpen(CLOCK) }
    }

    private var ticks = 0

    /** Called every second while Home is showing: flips the clock tile now and then. */
    fun tick() {
        val t = clockTile ?: return
        if (++ticks % 9 == 0) Turnstile.flip(t) { clockFace = 1 - clockFace; fillClock(t) }
    }

    /** Show or hide the now-playing tile. */
    fun music(now: Music.Now?) {
        val t = musicTile ?: return
        if (now == null) { t.visibility = View.GONE; return }
        if (t.visibility != View.VISIBLE || t.tag != now.key + now.playing) {
            t.tag = now.key + now.playing
            t.removeAllViews()
            val h = t.layoutParams.height
            val art = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            if (now.art != null) art.setImageBitmap(now.art) else art.setImageResource(R.drawable.ic_music)
            t.addView(art, FrameLayout.LayoutParams(h, h, Gravity.END))
            val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(if (now.playing) "now playing" else "paused", 12f).apply { alpha = 0.8f })
            col.addView(text(now.title, 20f))
            if (now.artist.isNotEmpty()) col.addView(text(now.artist, 13f))
            t.addView(col, FrameLayout.LayoutParams(t.layoutParams.width - h - (16 * dp).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                setMargins((10 * dp).toInt(), 0, 0, (8 * dp).toInt())
            })
            t.setOnClickListener { onOpen(MUSIC) }
            t.visibility = View.VISIBLE
        }
    }

    companion object {
        const val ALL_APPS = 100
        const val CLOCK = 101
        const val MUSIC = 102
    }
}
