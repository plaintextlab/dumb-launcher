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

    private lateinit var grid: FrameLayout
    private var lastSig: String? = null

    fun build(counts: Map<String, Int>, animate: Boolean = true) {
        this.counts = counts
        if (dragFrom >= 0) return          // not while a tile is being dragged; rebuilt when it lands
        // Nothing changed since last time (the usual case when coming back Home): keep the tiles
        // as they are and only swing them in. Rebuilding every time made returning Home stutter.
        val sig = listOf(Prefs.get(a, "start_tiles"), counts, keyNumbers(), Metro.accent(a), Metro.appColours(a),
            Icons.version, AppList.version, a.resources.displayMetrics.widthPixels).toString()
        if (sig == lastSig && ::grid.isInitialized && grid.parent === column) {
            Turnstile.reset(rows)
            if (animate) Turnstile.featherIn(column) { rows.toList() }
            return
        }
        lastSig = sig
        specs = StartTiles.load(a)
        column.removeAllViews(); rows.clear(); tileViews.clear(); live.clear()
        val width = a.resources.displayMetrics.widthPixels - column.paddingLeft - column.paddingRight
        u = (width - 5 * gap) / 6

        grid = FrameLayout(a).apply { clipChildren = false }
        val (cells, bottom) = pack(specs)
        val keys = keyNumbers()
        specs.forEachIndexed { i, s ->
            val (x, y) = cells[i]
            val v = tile(s, i, keys)
            grid.addView(v, FrameLayout.LayoutParams(span(s.size.w), span(s.size.h)).apply {
                leftMargin = x * (u + gap); topMargin = y * (u + gap)
            })
            tileViews += v; rows += v
        }
        grid.setOnDragListener(dropHandler)
        column.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height(bottom)))

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
        if (animate) Turnstile.featherIn(column) { rows.toList() }
    }

    private fun span(n: Int) = n * u + (n - 1) * gap
    private fun height(bottom: Int) = maxOf(0, bottom * (u + gap) - gap)

    /** Where each tile of [list] goes (in small squares), and how many rows that takes. */
    private fun pack(list: List<Spec>): Pair<List<Pair<Int, Int>>, Int> {
        val taken = ArrayList<BooleanArray>()
        var bottom = 0
        val cells = list.map { s -> place(taken, s.size).also { (_, y) -> bottom = maxOf(bottom, y + s.size.h) } }
        return cells to bottom
    }

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

    /** Which number key opens each app (only keys the user set; 0 is All apps), for the tile's corner. */
    private fun keyNumbers(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (n in 1..9) Prefs.get(a, Prefs.num(n))?.let { out.putIfAbsent(it, n) }
        return out
    }

    // ---- Tiles ----

    private fun tile(s: Spec, index: Int, keys: Map<String, Int>): FrameLayout {
        val v = FrameLayout(a)
        v.background = ColorDrawable(s.colour ?: defaultColour(s.id))
        Turnstile.tilt(v)
        selectable(v, s.id)
        v.setOnClickListener { open(s.id) }
        v.setOnLongClickListener { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); startDrag(v, index); true }
        val target = s.target
        if (target != null) appTile(v, s, target, keys[target])
        else { v.contentDescription = StartTiles.liveName(s.id); live[s.id] = v to s.size; fillLive(s.id) }
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
            StartTiles.PEOPLE -> fillPeople(t, size)
        }
    }

    private var faces: List<People.Face> = emptyList()
    private var faceCells: MutableList<ImageView> = mutableListOf()
    private var faceNext = 0

    /** People: contact photos tiled 3 high (3x3 on a square tile, 6x3 on a wide one). */
    private fun fillPeople(t: FrameLayout, size: Size) {
        faceCells = mutableListOf()
        if (faces.isEmpty()) {
            val g = (span(2) * 0.4f).toInt()
            t.addView(ImageView(a).apply { setImageResource(R.drawable.s_contacts) }, FrameLayout.LayoutParams(g, g, Gravity.CENTER))
            t.addView(text(if (People.hasPermission(a)) "People" else "People · allow contacts", 13f), corner(Gravity.BOTTOM or Gravity.START, size))
            return
        }
        val rowsN = 3
        val cols = if (size == Size.WIDE) 6 else 3
        val cw = span(size.w) / cols.toFloat(); val ch = span(size.h) / rowsN.toFloat()
        for (r in 0 until rowsN) for (c in 0 until cols) {
            val iv = ImageView(a).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(faces[(r * cols + c) % faces.size].photo)
            }
            t.addView(iv, FrameLayout.LayoutParams(Math.round((c + 1) * cw) - Math.round(c * cw), Math.round((r + 1) * ch) - Math.round(r * ch))
                .apply { leftMargin = Math.round(c * cw); topMargin = Math.round(r * ch) })
            faceCells += iv
        }
        faceNext = faceCells.size
        if (size != Size.SMALL) t.addView(text("People", 13f), corner(Gravity.BOTTOM or Gravity.START, size))
    }

    fun people(list: List<People.Face>) { faces = list; fillLive(StartTiles.PEOPLE) }

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
        // The album art: filling the tile (medium, large) or as a square on the right (wide).
        val h = span(size.h)
        val wide = size == Size.WIDE
        val art = ImageView(a).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (now.art != null) setImageBitmap(now.art) else {
                setImageResource(R.drawable.ic_music); scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
        t.addView(art, FrameLayout.LayoutParams(if (wide) h else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        if (!wide) t.addView(View(a).apply {   // darken the bottom so the words and buttons read over the art
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0xB0000000.toInt()))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        if (size != Size.MEDIUM) col.addView(text(if (now.playing) "Now playing" else "Paused", 12f).apply { alpha = 0.85f })
        col.addView(text(now.title, if (size == Size.MEDIUM) 14f else 18f, true))
        if (now.artist.isNotEmpty() && size != Size.MEDIUM) col.addView(text(now.artist, 13f))
        col.addView(controls(now, if (size == Size.MEDIUM) 30 else 36))
        val w = (if (wide) span(size.w) - h else span(size.w)) - (12 * dp).toInt()
        t.addView(col, FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
            .apply { setMargins((6 * dp).toInt(), 0, 0, (4 * dp).toInt()) })
    }

    /** ⏮ ⏯ ⏭ by touch. (With keys: hold OK on the tile for the same in its menu.) */
    private fun controls(now: Music.Now, sizeDp: Int) = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        val px = (sizeDp * dp).toInt()
        listOf(
            R.drawable.ic_prev to { Music.previous(a) },
            (if (now.playing) R.drawable.ic_pause else R.drawable.ic_play) to { Music.playPause(a) },
            R.drawable.ic_next to { Music.next(a) },
        ).forEach { (icon, act) ->
            addView(ImageView(a).apply {
                setImageResource(icon); setColorFilter(Color.WHITE)
                val p = (px * 0.2f).toInt(); setPadding(p, p, p, p)
                isFocusable = false
                setOnClickListener { Turnstile.press(this); act(); music = null }   // re-read on the next tick
            }, LinearLayout.LayoutParams(px, px))
        }
    }

    fun weather(now: Weather.Now?) { weather = now; fillLive(StartTiles.WEATHER) }
    fun photos(list: List<android.graphics.Bitmap>) { photos = list; photoIndex = 0; fillLive(StartTiles.PHOTOS) }

    /** Now playing (or null): updates the Music tile when the song or play state changes. */
    fun music(now: Music.Now?) {
        // Also when the cover arrives after the song (YouTube, links to covers): it is often a moment late.
        if (now?.key == music?.key && now?.playing == music?.playing && now?.art === music?.art) return
        music = now; fillLive(StartTiles.MUSIC)
    }

    private var ticks = 0

    /** Called every second while Start is showing: live tiles change now and then, out of step. */
    fun tick() {
        ticks++
        live[StartTiles.CALENDAR]?.let { (t, s) -> if (s != Size.SMALL && ticks % 8 == 0) Turnstile.flip(t) { dateFace = 1 - dateFace; fillLive(StartTiles.CALENDAR) } }
        live[StartTiles.PHOTOS]?.let { (t, _) -> if (ticks % 6 == 3 && photos.size > 1) Turnstile.flip(t) { photoIndex++; fillLive(StartTiles.PHOTOS) } }
        live[StartTiles.WEATHER]?.let { (t, s) -> if (s == Size.MEDIUM && ticks % 10 == 5 && weather != null) Turnstile.flip(t) { weatherFace = 1 - weatherFace; fillLive(StartTiles.WEATHER) } }
        // People: one photo at a time flips to another contact.
        if (live.containsKey(StartTiles.PEOPLE) && ticks % 3 == 1 && faces.size > faceCells.size && faceCells.isNotEmpty()) {
            val cell = faceCells[(ticks / 3 * 5) % faceCells.size]
            val face = faces[faceNext++ % faces.size]
            Turnstile.flip(cell) { cell.setImageBitmap(face.photo) }
        }
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

    /*
     * Touch: hold a tile and drag. While it moves, the other tiles slide out of its way to show
     * where it will land (the faded tile marks the spot). Let go to drop it there. Holding
     * without moving opens its menu instead.
     */
    private var dragFrom = -1          // index in specs of the tile being dragged, or -1
    private var dragTo = -1            // where it would land now
    private var dragMoved = false
    private var pending = -1           // a new landing spot the finger is resting on
    private var targets: List<Rect> = emptyList()   // each tile's place in the current preview (by spec index)
    private val dwell = android.os.Handler(android.os.Looper.getMainLooper())

    /** Called when a live tile was added and its photos / pictures need loading. */
    var onNeedData: (() -> Unit)? = null

    /** Back on Start: scroll to the top and select the first tile, as on a tile phone. */
    fun toTop() {
        (column.parent as? android.widget.ScrollView)?.smoothScrollTo(0, 0)
        rows.firstOrNull()?.requestFocus()
    }

    /** While dragging near the top or bottom of the screen, scroll Start so the tile can go further. */
    private fun edgeScroll(y: Float) {
        val sv = column.parent as? android.widget.ScrollView ?: return
        val inView = grid.top + column.top + y - sv.scrollY
        val edge = 56 * dp
        when {
            inView > sv.height - edge -> sv.scrollBy(0, (12 * dp).toInt())
            inView < edge -> sv.scrollBy(0, -(12 * dp).toInt())
        }
    }

    private fun startDrag(v: View, index: Int) {
        dragFrom = index; dragTo = index; dragMoved = false; pending = -1
        targets = rectsFor(order(index, index))
        if (v.startDragAndDrop(ClipData.newPlainText("tile", "$index"), View.DragShadowBuilder(v), index, 0)) {
            v.animate().alpha(0.3f).setDuration(150).start()
        } else { dragFrom = -1; menu(index) }
    }

    /** Spec indexes in the order they would be with [from] moved to [to]. */
    private fun order(from: Int, to: Int) = specs.indices.toMutableList().also { o -> o.add(to, o.removeAt(from)) }

    /** Each tile's rectangle (by spec index) when laid out in [order]. */
    private fun rectsFor(order: List<Int>): List<Rect> {
        val (cells, _) = pack(order.map { specs[it] })
        val out = arrayOfNulls<Rect>(specs.size)
        order.forEachIndexed { pos, i ->
            val (x, y) = cells[pos]; val s = specs[i].size
            val l = x * (u + gap); val t = y * (u + gap)
            out[i] = Rect(l, t, l + span(s.w), t + span(s.h))
        }
        return out.map { it!! }
    }

    /** Slide every tile to its place with the dragged tile at [to]. */
    private fun preview(to: Int) {
        val o = order(dragFrom, to)
        targets = rectsFor(o)
        val (_, bottom) = pack(o.map { specs[it] })
        grid.layoutParams = grid.layoutParams.apply { height = maxOf(height, height(bottom)) }
        tileViews.forEachIndexed { i, v ->
            val lp = v.layoutParams as FrameLayout.LayoutParams
            Turnstile.slide(v, (targets[i].left - lp.leftMargin).toFloat(), (targets[i].top - lp.topMargin).toFloat())
        }
    }

    /** The landing spot under the finger: the position of the tile there, or the end below the last row. */
    private fun spotAt(x: Int, y: Int): Int? {
        val o = order(dragFrom, dragTo)
        targets.forEachIndexed { i, r -> if (r.contains(x, y)) return o.indexOf(i) }
        if (y > (targets.maxOfOrNull { it.bottom } ?: 0)) return specs.lastIndex
        return null   // in a gap: stay
    }

    private val dropHandler = View.OnDragListener { _, e ->
        if (e.localState !is Int || dragFrom < 0) return@OnDragListener false
        when (e.action) {
            DragEvent.ACTION_DRAG_LOCATION -> {
                edgeScroll(e.y)
                val spot = spotAt(e.x.toInt(), e.y.toInt())
                if (spot == null || spot == dragTo) { pending = -1; dwell.removeCallbacksAndMessages(null) }
                else if (spot != pending) {
                    // Wait until the finger rests a moment, so tiles don't jump about while passing over them.
                    pending = spot; dwell.removeCallbacksAndMessages(null)
                    dwell.postDelayed({ if (dragFrom >= 0 && pending >= 0) { dragTo = pending; dragMoved = true; pending = -1; preview(dragTo) } }, 120)
                }
            }
            DragEvent.ACTION_DROP -> {
                dwell.removeCallbacksAndMessages(null)
                if (pending >= 0) { dragTo = pending; dragMoved = true }
                val from = dragFrom; val to = dragTo; val moved = dragMoved
                dragFrom = -1
                if (!moved && to == from) { grid.post { settle(); menu(from) } }
                else {
                    StartTiles.move(specs, from, to); StartTiles.save(a, specs); focusId = specs[to].id
                    grid.post { rebuild() }
                }
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                dwell.removeCallbacksAndMessages(null)
                if (dragFrom >= 0) { dragFrom = -1; settle() }    // let go outside Start: put everything back
            }
        }
        true
    }

    /** Tiles back to where they are built, fully shown. */
    private fun settle() = tileViews.forEach { v -> Turnstile.slide(v, 0f, 0f); v.animate().alpha(1f).setDuration(150).start() }

    /**
     * Rebuild after a change. Tiles glide from where they were drawn to their new places
     * (new tiles grow in), instead of jumping.
     */
    private fun rebuild() {
        val before = tileViews.associate { v ->
            val lp = v.layoutParams as FrameLayout.LayoutParams
            (v.tag as String) to (lp.leftMargin + v.translationX to lp.topMargin + v.translationY)
        }
        lastSig = null
        build(counts, animate = false); restoreFocus()
        if (!Anim.on(a)) return
        tileViews.forEach { v ->
            val lp = v.layoutParams as FrameLayout.LayoutParams
            val old = before[v.tag as String]
            if (old == null) {
                v.scaleX = 0.6f; v.scaleY = 0.6f; v.alpha = 0f
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(Turnstile.QUINT_OUT).start()
            } else {
                v.translationX = old.first - lp.leftMargin; v.translationY = old.second - lp.topMargin
                if (v.translationX != 0f || v.translationY != 0f) Turnstile.slide(v, 0f, 0f)
            }
        }
    }

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
                // "Automatic" shows the colour the tile has on its own (its app's colour, or the default).
                val auto = if (s.colour == null) (tileViews.getOrNull(i)?.background as? ColorDrawable)?.color ?: defaultColour(s.id)
                           else defaultColour(s.id)
                val current = if (s.colour == null) 0 else choices.indexOfFirst { it.second == s.colour }.takeIf { it > 0 } ?: -1
                Ui.chooseColour(a, "Tile colour", choices.map { (n, c) ->
                    Triple(n, c ?: auto, { specs[i] = s.copy(colour = c); focusId = s.id; save() })
                }, current)
            },
            "Unpin from Start" to { specs.removeAt(i); save() },
        )
        if (s.target == null) items.add(3, "Opens…" to { chooseOpens(s.id) })
        if (s.id == StartTiles.MUSIC) items.addAll(0, listOf(
            "Play / pause" to { Music.playPause(a); music = null },
            "Next song" to { Music.next(a); music = null },
            "Previous song" to { Music.previous(a); music = null },
        ))
        val missing = StartTiles.missingLive(a)
        if (missing.isNotEmpty()) items += "Add a live tile…" to {
            Ui.choose(a, "Add to Start", missing.map { id -> StartTiles.liveName(id) to {
                StartTiles.addLive(a, id); focusId = id; rebuild(); onNeedData?.invoke()
                if (id == StartTiles.PEOPLE && !People.hasPermission(a)) a.requestPermissions(arrayOf(People.permission), 2)
            } })
        }
        items += "Launcher settings" to { a.startActivity(android.content.Intent(a, SettingsActivity::class.java)) }
        Ui.choose(a, name(s), items)
    }

    /** Which app a live tile opens: the usual one, or one the user picks. */
    private fun chooseOpens(id: String) {
        val key = StartTiles.openKey(id)
        val chosen = Prefs.get(a, key)?.let { Apps.entry(a, it)?.label }
        val usual = when (id) {
            StartTiles.WEATHER -> "Refresh the weather"
            StartTiles.MUSIC -> "The playing app, else the music app"
            else -> "The phone's ${StartTiles.liveName(id).lowercase()} app"
        }
        Ui.choose(a, "${StartTiles.liveName(id)} tile opens", listOf(
            (if (chosen == null) "● " else "") + usual to { Prefs.set(a, key, null) },
            (if (chosen != null) "● $chosen  (change…)" else "Choose an app…") to { Ui.pick(a, key, "${StartTiles.liveName(id)} tile opens") },
        ))
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
