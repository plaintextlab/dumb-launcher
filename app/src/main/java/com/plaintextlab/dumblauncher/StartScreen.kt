package com.plaintextlab.dumblauncher

import android.app.Activity
import android.content.ClipData
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import com.plaintextlab.dumblauncher.StartTiles.Size
import com.plaintextlab.dumblauncher.StartTiles.Spec

/**
 * Home in the Metro style: the user's pinned tiles on a grid six small squares wide.
 * Small = 1x1 square, medium = 2x2, wide = 4x2, large = 4x4. Tiles are placed in list
 * order into the first free spot (medium and bigger on a 2-square grid), so small tiles fill
 * the gaps the way a tile phone's Start screen does.
 *
 * Touch: tap opens, press-and-hold then drag rearranges, press-and-hold without moving opens
 * the tile menu (move, resize, colour, unpin). Keys: arrows select, OK opens, hold OK opens
 * the tile menu; "Move" lets the arrows push the tile through the list.
 */
class StartScreen(private val a: Activity, private val column: LinearLayout, private val open: (String) -> Unit) {

    private val dp = a.resources.displayMetrics.density
    private val gap = (4 * dp).toInt()
    private var u = 0                                   // one small square, px
    private var specs: MutableList<Spec> = mutableListOf()
    private var counts: Map<String, Int> = emptyMap()

    /** Tile views in list order, and "all apps". For the turnstile and keypad selection. */
    val rows = mutableListOf<View>()
    private val tileViews = mutableListOf<FrameLayout>()

    /** The tile being moved with the arrow keys, or -1. */
    var moving = -1
        private set

    // Live tiles currently on screen (with their size), updated by the clock tick and data loads.
    private val live = HashMap<String, Pair<FrameLayout, Size>>()

    fun build(counts: Map<String, Int>, animate: Boolean = true) {
        this.counts = counts
        specs = StartTiles.load(a)
        column.removeAllViews(); rows.clear(); tileViews.clear(); live.clear()
        val width = a.resources.displayMetrics.widthPixels - column.paddingLeft - column.paddingRight
        u = (width - 5 * gap) / 6

        val grid = FrameLayout(a).apply { clipChildren = false }
        val taken = ArrayList<BooleanArray>()
        var bottom = 0
        val keys = keyNumbers()
        specs.forEachIndexed { i, s ->
            val (x, y) = place(taken, s.size)
            val v = tile(s, i, keys)
            grid.addView(v, FrameLayout.LayoutParams(span(s.size.w), span(s.size.h)).apply {
                leftMargin = x * (u + gap); topMargin = y * (u + gap)
            })
            tileViews += v; rows += v
            bottom = maxOf(bottom, y + s.size.h)
        }
        grid.setOnDragListener(dropHandler)
        column.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(0, bottom * (u + gap) - gap)))

        val all = TextView(a).apply {
            text = "all apps  ›"; textSize = 18f; setTextColor(a.getColor(R.color.fg))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.END; setPadding(0, (10 * dp).toInt(), (6 * dp).toInt(), (20 * dp).toInt())
            setOnClickListener { open(ALL_APPS) }
        }
        selectable(all, ALL_APPS)
        column.addView(all, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = gap })
        rows += all
        if (animate) Turnstile.featherIn(grid) else grid.layoutAnimation = null
    }

    private fun span(n: Int) = n * u + (n - 1) * gap

    /** First free spot for [size], row by row; medium and bigger tiles sit on the 2-square grid. */
    private fun place(taken: ArrayList<BooleanArray>, size: Size): Pair<Int, Int> {
        val step = if (size.w >= 2) 2 else 1
        var y = 0
        while (true) {
            if (size.h < 2 || y % 2 == 0) {
                var x = 0
                while (x + size.w <= 6) {
                    if (fits(taken, x, y, size)) { mark(taken, x, y, size); return x to y }
                    x += step
                }
            }
            y++
        }
    }

    private fun fits(t: ArrayList<BooleanArray>, x: Int, y: Int, s: Size): Boolean {
        for (r in y until y + s.h) for (c in x until x + s.w) if (r < t.size && t[r][c]) return false
        return true
    }

    private fun mark(t: ArrayList<BooleanArray>, x: Int, y: Int, s: Size) {
        while (t.size < y + s.h) t += BooleanArray(6)
        for (r in y until y + s.h) for (c in x until x + s.w) t[r][c] = true
    }

    /** Which number key opens each app, to show it in the tile's corner. */
    private fun keyNumbers(): Map<String, Int> {
        val out = HashMap<String, Int>()
        val dock = if (Dock.enabled(a)) Dock.slots(a).associate { it.key to it.entry?.target } else emptyMap()
        for (n in 0..9) (dock[n] ?: Prefs.get(a, Prefs.num(n)))?.let { out.putIfAbsent(it, n) }
        return out
    }

    // ---- Tiles ----

    private fun tile(s: Spec, index: Int, keys: Map<String, Int>): FrameLayout {
        val v = FrameLayout(a)
        v.background = ColorDrawable(s.colour ?: defaultColour(s.id))
        Turnstile.tilt(v)
        selectable(v, s.id)
        v.setOnClickListener { open(s.id) }
        v.setOnLongClickListener { startDrag(v, index); true }
        val target = s.target
        if (target != null) appTile(v, s, target, keys[target])
        else { live[s.id] = v to s.size; fillLive(s.id) }
        return v
    }

    private fun defaultColour(id: String) = when (id) {
        StartTiles.WEATHER -> 0xFF0078D7.toInt()
        in StartTiles.LIVE -> StartTiles.GLASS
        else -> Metro.accent(a)
    }

    private fun appTile(v: FrameLayout, s: Spec, target: String, key: Int?) {
        val e = Apps.entry(a, target)
        if (e == null) {   // uninstalled
            v.addView(text("(not installed)", 11f).apply { alpha = 0.7f }, corner(Gravity.BOTTOM or Gravity.START, s.size))
            return
        }
        val side = minOf(span(s.size.w), span(s.size.h))
        val g = when (s.size) { Size.SMALL -> side; Size.MEDIUM -> (side * 0.8f).toInt(); else -> (side * 0.72f).toInt() }
        val glyph = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        v.addView(glyph, FrameLayout.LayoutParams(g, g, Gravity.CENTER))
        Icons.loadArt(a, e) { art ->
            if (art == null) return@loadArt
            glyph.setImageBitmap(art.glyph)
            if (s.colour == null) v.background = ColorDrawable(art.colour)
        }
        if (s.size != Size.SMALL) v.addView(text(e.label, 13f), corner(Gravity.BOTTOM or Gravity.START, s.size))
        key?.let { v.addView(text("$it", 10f).apply { alpha = 0.7f }, corner(Gravity.TOP or Gravity.START, s.size)) }
        counts[Shortcuts.packageOf(target)]?.takeIf { it > 0 }?.let { n -> v.addView(badge(n, s.size == Size.SMALL), badgeParams(s.size == Size.SMALL)) }
        v.contentDescription = e.label
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

    private fun text(s: String, size: Float, light: Boolean = false) = TextView(a).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        typeface = Typeface.create(if (light) "sans-serif-light" else "sans-serif", Typeface.NORMAL)
        setShadowLayer(4f, 0f, 1f, 0x66000000)
        includeFontPadding = false
    }

    private fun corner(gravity: Int, size: Size) = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity
    ).apply { val m = ((if (size == Size.SMALL) 3 else 8) * dp).toInt(); setMargins(m, m, m, m) }

    private fun centre() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)

    private fun clockText(fmt: String, size: Float, light: Boolean) = TextClock(a).apply {
        format12Hour = fmt; format24Hour = if (fmt == "h:mm") "HH:mm" else fmt
        textSize = size; setTextColor(Color.WHITE); includeFontPadding = false
        typeface = Typeface.create(if (light) "sans-serif-light" else "sans-serif", Typeface.NORMAL)
        setShadowLayer(4f, 0f, 1f, 0x66000000)
    }

    // ---- Live tiles ----

    private var dateFace = 0
    private var weatherFace = 0
    private var weather: Weather.Now? = null
    private var photos: List<android.graphics.Bitmap> = emptyList()
    private var photoIndex = 0
    private var music: Music.Now? = null

    private fun fillLive(id: String) {
        val (t, size) = live[id] ?: return
        t.removeAllViews()
        val small = size == Size.SMALL
        when (id) {
            StartTiles.CLOCK -> {
                t.addView(clockText("h:mm", when (size) { Size.SMALL -> 15f; Size.MEDIUM -> 30f; else -> 46f }, true), centre())
                if (!small) t.addView(text("Clock", 13f), corner(Gravity.BOTTOM or Gravity.START, size))
            }
            StartTiles.CALENDAR -> {
                val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                if (small) col.addView(clockText("d", 22f, true))
                else if (dateFace == 0) { col.addView(clockText("EEEE", 14f, false)); col.addView(clockText("d", if (size == Size.MEDIUM) 34f else 46f, true)) }
                else { col.addView(clockText("MMMM", 16f, false)); col.addView(clockText("yyyy", 26f, true)) }
                t.addView(col, centre())
                if (!small) t.addView(text("Calendar", 13f), corner(Gravity.BOTTOM or Gravity.START, size))
            }
            StartTiles.WEATHER -> fillWeather(t, size)
            StartTiles.PHOTOS -> {
                if (photos.isEmpty()) {
                    t.addView(ImageView(a).apply { setImageResource(R.drawable.s_image) },
                        FrameLayout.LayoutParams((span(2) * 0.4f).toInt(), (span(2) * 0.4f).toInt(), Gravity.CENTER))
                } else t.addView(ImageView(a).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(photos[photoIndex % photos.size])
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                t.addView(text(if (photos.isEmpty() && !Photos.hasPermission(a)) "Photos · allow access" else "Photos", 13f),
                    corner(Gravity.BOTTOM or Gravity.START, size))
            }
            StartTiles.MUSIC -> fillMusic(t, size)
        }
    }

    private fun fillWeather(t: FrameLayout, size: Size) {
        val w = weather
        val small = size == Size.SMALL
        if (w == null) {
            val g = (span(size.h) * 0.42f).toInt()
            t.addView(ImageView(a).apply { setImageResource(R.drawable.w_partly) }, FrameLayout.LayoutParams(g, g, Gravity.CENTER))
            if (!small) t.addView(text(if (Weather.hasPermission(a)) "Weather" else "Weather · allow location", 13f),
                corner(Gravity.BOTTOM or Gravity.START, size))
            return
        }
        if (small) { t.addView(text("${w.temp}°", 20f, true), centre()); return }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        if (weatherFace == 0 || size != Size.MEDIUM) {
            val g = (span(2) * 0.38f).toInt()
            t.addView(ImageView(a).apply { setImageResource(w.icon) }, FrameLayout.LayoutParams(g, g, Gravity.TOP or Gravity.END)
                .apply { setMargins(0, (8 * dp).toInt(), (8 * dp).toInt(), 0) })
            col.addView(text("${w.temp}°", 34f, true))
            if (size != Size.MEDIUM) { col.addView(text(w.text, 15f)); col.addView(text("${w.high}° / ${w.low}°", 15f)) }
        } else {
            col.addView(text(w.text, 15f)); col.addView(text("${w.high}° / ${w.low}°", 22f, true))
        }
        t.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = (8 * dp).toInt() })
        t.addView(text(w.place.ifEmpty { "Weather" }, 13f), corner(Gravity.BOTTOM or Gravity.START, size))
    }

    private fun fillMusic(t: FrameLayout, size: Size) {
        val now = music
        if (now == null) {
            val g = (span(2) * 0.36f).toInt()
            t.addView(ImageView(a).apply { setImageResource(R.drawable.ic_music) }, FrameLayout.LayoutParams(g, g, Gravity.CENTER))
            t.addView(text("Music", 13f), corner(Gravity.BOTTOM or Gravity.START, size))
            return
        }
        val h = span(size.h)
        val medium = size == Size.MEDIUM
        val art = ImageView(a).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (now.art != null) setImageBitmap(now.art) else setImageResource(R.drawable.ic_music)
            if (medium) alpha = 0.5f            // medium: art behind the text
        }
        t.addView(art, FrameLayout.LayoutParams(if (medium) ViewGroup.LayoutParams.MATCH_PARENT else h,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(if (now.playing) "Now playing" else "Paused", 12f).apply { alpha = 0.85f })
        col.addView(text(now.title, 18f, true))
        if (now.artist.isNotEmpty()) col.addView(text(now.artist, 13f))
        val w = (if (medium) span(2) else span(size.w) - h) - (12 * dp).toInt()
        t.addView(col, FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
            .apply { setMargins((8 * dp).toInt(), 0, 0, (8 * dp).toInt()) })
    }

    fun weather(now: Weather.Now?) { weather = now; fillLive(StartTiles.WEATHER) }
    fun photos(list: List<android.graphics.Bitmap>) { photos = list; photoIndex = 0; fillLive(StartTiles.PHOTOS) }

    /** Now playing (or null): updates the Music tile when the song or play state changes. */
    fun music(now: Music.Now?) {
        if (now?.key == music?.key && now?.playing == music?.playing) return
        music = now; fillLive(StartTiles.MUSIC)
    }

    private var ticks = 0

    /** Called every second while Start is showing: live tiles change now and then, out of step. */
    fun tick() {
        ticks++
        live[StartTiles.CALENDAR]?.let { (t, s) -> if (s != Size.SMALL && ticks % 8 == 0) Turnstile.flip(t) { dateFace = 1 - dateFace; fillLive(StartTiles.CALENDAR) } }
        live[StartTiles.PHOTOS]?.let { (t, _) -> if (ticks % 6 == 3 && photos.size > 1) Turnstile.flip(t) { photoIndex++; fillLive(StartTiles.PHOTOS) } }
        live[StartTiles.WEATHER]?.let { (t, s) -> if (s == Size.MEDIUM && ticks % 10 == 5 && weather != null) Turnstile.flip(t) { weatherFace = 1 - weatherFace; fillLive(StartTiles.WEATHER) } }
    }

    // ---- Selection (keys) ----

    private var focusId: String? = null

    private fun selectable(v: View, id: String) {
        v.isFocusable = true
        v.foreground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                setStroke((3 * dp).toInt(), Color.WHITE); setColor(Color.TRANSPARENT)
            })
        }
        v.setOnFocusChangeListener { view, has ->
            if (has) {
                focusId = id
                view.post { view.requestRectangleOnScreen(Rect(0, -gap * 3, view.width, view.height + gap * 3)) }
            }
        }
        v.tag = id
    }

    /** Select the tile that was selected before a rebuild (or the first one). */
    fun restoreFocus() {
        (rows.firstOrNull { it.tag == focusId } ?: rows.firstOrNull())?.requestFocus()
    }

    // ---- Rearranging ----

    /** Touch: hold a tile, drag it onto another tile's place, let go. Letting go where it was opens its menu. */
    private fun startDrag(v: View, index: Int) {
        if (v.startDragAndDrop(ClipData.newPlainText("tile", "$index"), View.DragShadowBuilder(v), index, 0)) v.alpha = 0.4f
        else menu(index)
    }

    private val dropHandler = View.OnDragListener { grid, e ->
        val from = e.localState as? Int ?: return@OnDragListener false
        when (e.action) {
            DragEvent.ACTION_DROP -> {
                val to = indexAt(e.x.toInt(), e.y.toInt())
                if (to == null || to == from) grid.post { menu(from) }
                else { StartTiles.move(specs, from, to); StartTiles.save(a, specs); focusId = specs[to].id; grid.post { rebuild() } }
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> { tileViews.getOrNull(from)?.alpha = 1f; true }
            else -> true
        }
    }

    private fun indexAt(x: Int, y: Int): Int? {
        val r = Rect()
        tileViews.forEachIndexed { i, v -> v.getHitRect(r); if (r.contains(x, y)) return i }
        // Let go in a gap: the nearest tile.
        return tileViews.indices.minByOrNull { i ->
            tileViews[i].getHitRect(r); val dx = r.centerX() - x; val dy = r.centerY() - y; dx * dx + dy * dy
        }
    }

    private fun rebuild() { build(counts, animate = false); restoreFocus() }

    /** The menu for the selected tile (hold OK). False if "all apps" or nothing is selected. */
    fun menuForSelected(): Boolean {
        val i = specs.indexOfFirst { it.id == focusId }
        if (i < 0) return false
        menu(i); return true
    }

    private fun name(s: Spec) = s.target?.let { Apps.entry(a, it)?.label } ?: StartTiles.liveName(s.id)

    private fun menu(i: Int) {
        val s = specs.getOrNull(i) ?: return
        val items = mutableListOf<Pair<String, () -> Any?>>(
            "Move" to { moving = i; focusId = s.id; Toast.makeText(a, "◀ ▶ ▲ ▼ move    ● done", Toast.LENGTH_LONG).show() },
            "Resize…" to {
                Ui.choose(a, "Size", StartTiles.sizesFor(s.id).map { sz ->
                    (if (sz == s.size) "● " else "") + sz.label to { specs[i] = s.copy(size = sz); focusId = s.id; save() }
                })
            },
            "Colour…" to {
                val choices = listOf<Pair<String, Int?>>("Automatic" to null, "Accent colour" to Metro.accent(a), "See-through" to StartTiles.GLASS) +
                    Metro.ACCENTS.map { (n, c) -> n to c }
                Ui.choose(a, "Tile colour", choices.map { (n, c) -> n to { specs[i] = s.copy(colour = c); focusId = s.id; save() } })
            },
            "Unpin from Start" to { specs.removeAt(i); save() },
        )
        val missing = StartTiles.missingLive(a)
        if (missing.isNotEmpty()) items += "Add a live tile…" to {
            Ui.choose(a, "Add to Start", missing.map { id -> StartTiles.liveName(id) to { StartTiles.addLive(a, id); focusId = id; rebuild() } })
        }
        Ui.choose(a, name(s), items)
    }

    private fun save() { StartTiles.save(a, specs); rebuild() }

    /** Keys while moving: push the tile through the list (▲ ▼ by about a row). */
    fun moveBy(delta: Int) {
        if (moving < 0) return
        val to = (moving + delta).coerceIn(0, specs.lastIndex)
        if (to == moving) return
        StartTiles.move(specs, moving, to); moving = to
        focusId = specs[to].id
        StartTiles.save(a, specs); rebuild()
    }

    fun stopMoving() { moving = -1 }

    companion object {
        const val ALL_APPS = "allapps"
    }
}
