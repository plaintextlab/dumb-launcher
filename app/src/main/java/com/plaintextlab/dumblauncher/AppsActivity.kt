package com.plaintextlab.dumblauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.AbsListView
import android.widget.TextView

/** All apps A–Z (grid or list) with T9 search: just type. Also used to pick an app for a key. */
class AppsActivity : BaseActivity() {

    override val isDrawer = true
    override val zooms = true

    companion object {
        private const val EXTRA_PREF = "pref"
        private const val EXTRA_TITLE = "title"

        fun pickIntent(c: Context, pref: String, title: String) =
            Intent(c, AppsActivity::class.java).putExtra(EXTRA_PREF, pref).putExtra(EXTRA_TITLE, title)
    }

    private lateinit var list: AbsListView
    private val grid get() = Prefs.getBool(this, Prefs.DRAWER_GRID, Icons.style(this) != Icons.METRO)
    private val metro get() = Icons.style(this) == Icons.METRO
    private lateinit var queryView: TextView
    private var all: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    /** What the list shows: apps, and in the Metro list also letter headers (Strings). */
    private var rows: List<Any> = emptyList()
    private var query = ""
    private val pickPref get() = intent.getStringExtra(EXTRA_PREF)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        queryView = findViewById(R.id.query)
        findViewById<TextView>(R.id.hint).text =
            if (pickPref != null) "● Choose    0-9 Search    ↩ Cancel"
            else "● Open    0-9 Search    hold ● Options"
        list = findViewById(if (grid) R.id.grid else R.id.list)
        list.visibility = View.VISIBLE
        findViewById<View>(if (grid) R.id.list else R.id.grid).visibility = View.GONE
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            when (val r = rows[pos]) { is AppEntry -> choose(r); is String -> jumpList() }
        }
        list.setOnItemLongClickListener { _, _, pos, _ -> if (pickPref == null) (rows[pos] as? AppEntry)?.let { options(it) }; true }
        Ui.trackHighlight(list)
        Anim.cascade(list)
        if (metroList) searchBox()
        queryView.setOnClickListener { openKeyboard() }
        // Show the remembered list at once; re-read installed apps in the background.
        AppList.cached?.let { all = it; filter() }
        Bg.run({ AppList.refresh(this) }) { changed ->
            if (changed == true || all.isEmpty()) { all = AppList.cached.orEmpty(); filter() }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged() // an icon may have changed
        Turnstile.reset(screenRows())     // after swinging out to open an app
    }

    private fun filter() {
        val byUse = Prefs.getBool(this, Prefs.SORT_USED, false)
        val rank = if (byUse) Usage.ranked(this).withIndex().associate { it.value to it.index } else emptyMap()
        shown = all.filter { it.matches(query) }
            .let { l -> if (byUse) l.sortedBy { rank[it.target] ?: Int.MAX_VALUE } else l }
            // Searching: apps whose name starts with what you typed come before ones that match a later word.
            .let { l -> if (query.isEmpty()) l else l.sortedBy { if (it.t9.firstOrNull()?.startsWith(query) == true) 0 else 1 } }
        rows = if (metro && !grid && query.isEmpty() && !byUse) withLetters(shown) else shown
        val q = typed ?: query
        if (metroList) {
            // Search box: the hint (or what you are picking for) until you type, then the digits.
            val hint = intent.getStringExtra(EXTRA_TITLE) ?: "Search apps"
            queryView.text = if (query.isEmpty()) hint else "$q   (${shown.size})"
            queryView.setTextColor(getColor(if (query.isEmpty()) R.color.dim else R.color.fg))
        } else {
            val title = (intent.getStringExtra(EXTRA_TITLE) ?: "apps").lowercase()
            queryView.text = if (query.isEmpty()) title else "$title  ·  $q  (${shown.size})"
        }
        adapter.notifyDataSetChanged()
        if (!input.hasFocus()) list.requestFocus()
        val first = rows.indexOfFirst { it is AppEntry }.coerceAtLeast(0)
        list.setSelection(first)
        // Keep a letter header above the first app visible, instead of scrolling it off the top.
        if (first > 0 && list is android.widget.ListView) {
            (list as android.widget.ListView).setSelectionFromTop(first, (64 * resources.displayMetrics.density).toInt())
        }
    }

    private val metroList get() = metro && !grid

    /** Letters typed on the on-screen keyboard (shown as typed; matched through T9 like the keypad). */
    private var typed: String? = null
    private var settingInput = false

    /**
     * Touch phones: tapping the search box opens the keyboard. It types into this invisible
     * field; the text is turned into keypad digits so the same search works.
     */
    private val input by lazy {
        android.widget.EditText(this).apply {
            alpha = 0f; isCursorVisible = false; background = null
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (settingInput) return
                    val t = s?.toString().orEmpty()
                    typed = t.ifEmpty { null }
                    query = T9.encode(t.lowercase())
                    filter()
                }
            })
            setOnEditorActionListener { _, _, _ -> shown.firstOrNull()?.let { choose(it) }; true }
        }
    }

    private fun openKeyboard() {
        if (input.parent == null) {
            findViewById<ViewGroup>(android.R.id.content).addView(input, ViewGroup.LayoutParams(1, 1))
        }
        settingInput = true; input.setText(typed ?: ""); input.setSelection(input.text.length); settingInput = false
        input.requestFocus()
        input.post {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun clearTyped() {
        typed = null
        if (input.parent != null && input.text.isNotEmpty()) { settingInput = true; input.setText(""); settingInput = false }
    }

    /** Swipe right goes back to Start, as on a tile phone. */
    private val swipe by lazy {
        val minDist = 80 * resources.displayMetrics.density
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                val s = e1 ?: return false
                val dx = e2.x - s.x
                if (pickPref == null && dx > minDist && dx > 2 * Math.abs(e2.y - s.y) && vx > 0) { finish(); return true }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (swipe.onTouchEvent(ev)) {
            // A swipe, not a tap: cancel the touch so the tile or app under the finger doesn't open too.
            ev.action = android.view.MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(ev)
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /** The title becomes an outlined search box with a magnifier, as in tile-style app lists. */
    private fun searchBox() {
        val d = resources.displayMetrics.density
        queryView.apply {
            textSize = 17f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            background = android.graphics.drawable.GradientDrawable().apply {
                setStroke((2 * d).toInt(), getColor(R.color.dim)); setColor(0x22000000)
            }
            setPadding((12 * d).toInt(), (9 * d).toInt(), (12 * d).toInt(), (9 * d).toInt())
            (layoutParams as ViewGroup.MarginLayoutParams).setMargins((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_search, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.dim))
        }
    }

    /** Apps A-Z with a letter header before each new first letter (# for digits and symbols). */
    private fun withLetters(apps: List<AppEntry>): List<Any> {
        val out = ArrayList<Any>(apps.size + 27)
        var last: String? = null
        for (e in apps) {
            val c = e.label.firstOrNull()?.lowercaseChar()
            val letter = if (c != null && c.isLetter()) c.toString() else "#"
            if (letter != last) { out += letter; last = letter }
            out += e
        }
        return out
    }

    /**
     * Tap (or OK on) a letter header: every letter in a grid over the list. Letters with apps
     * are lit in the accent colour, the rest are dimmed; choosing one jumps to its apps.
     */
    private fun jumpList() {
        val letters = listOf("#") + ('a'..'z').map { it.toString() }
        val has = rows.filterIsInstance<String>().toSet()
        val d = resources.displayMetrics.density
        val accent = Metro.accent(this)
        val dialog = android.app.Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        // Square letters, 4 across, as big as fits both the width and the height (7 rows).
        val dm = resources.displayMetrics
        val cell = minOf((dm.widthPixels - 16 * d - 3 * 8 * d) / 4, (dm.heightPixels - 56 * d - 6 * 8 * d) / 7).toInt()
        val side = ((dm.widthPixels - 4 * cell - 3 * 8 * d) / 2).toInt()
        val grid = android.widget.GridView(this).apply {
            numColumns = 4
            horizontalSpacing = (8 * d).toInt(); verticalSpacing = (8 * d).toInt()
            setPadding(side, (16 * d).toInt(), side, (16 * d).toInt())
            clipToPadding = false
            stretchMode = android.widget.GridView.NO_STRETCH; columnWidth = cell
            selector = android.graphics.drawable.GradientDrawable().apply {
                setStroke((3 * d).toInt(), android.graphics.Color.WHITE); setColor(android.graphics.Color.TRANSPARENT)
            }
            setDrawSelectorOnTop(true)
            adapter = object : BaseAdapter() {
                override fun getCount() = letters.size
                override fun getItem(p: Int) = letters[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun areAllItemsEnabled() = false
                override fun isEnabled(p: Int) = letters[p] in has
                override fun getView(p: Int, convert: View?, parent: ViewGroup): View =
                    ((convert as? TextView) ?: TextView(this@AppsActivity)).apply {
                        val on = letters[p] in has
                        text = letters[p]; textSize = 30f
                        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                        gravity = android.view.Gravity.START or android.view.Gravity.BOTTOM
                        setPadding((8 * d).toInt(), 0, 0, (4 * d).toInt())
                        setTextColor(if (on) android.graphics.Color.WHITE else 0xFF555555.toInt())
                        setBackgroundColor(if (on) accent else 0xFF222222.toInt())
                        layoutParams = AbsListView.LayoutParams(cell, cell)
                    }
            }
            setOnItemClickListener { _, _, p, _ ->
                val at = rows.indexOf(letters[p])
                if (at >= 0) {
                    (list as? android.widget.ListView)?.setSelectionFromTop(at, 0) ?: list.setSelection(at)
                }
                dialog.dismiss()
            }
        }
        dialog.setContentView(grid)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xF0000000.toInt()))
        dialog.setOnShowListener {
            // Start on the letter that was selected, if it has apps.
            val now = (rows.getOrNull(list.selectedItemPosition) as? String) ?: has.minOrNull()
            grid.setSelection(letters.indexOf(now).coerceAtLeast(0))
            grid.requestFocus()
            Turnstile.featherIn(grid) { (0 until grid.childCount).map { grid.getChildAt(it) } }
        }
        dialog.show()
    }

    /** The title and the visible list rows, for the turnstile. */
    private fun screenRows(): List<View> = listOf<View>(queryView) + (0 until list.childCount).map { list.getChildAt(it) }

    private fun choose(e: AppEntry) {
        val pref = pickPref
        if (pref != null) { Prefs.set(this, pref, e.target); finish() }
        else if (metro) Turnstile.out(screenRows()) { Apps.launch(this, e.target) }
        else Apps.launch(this, e.target)
    }

    /** Re-read the app list (after adding/removing a drawer shortcut). */
    fun reload() = Bg.run({ AppList.refresh(this) }) { all = AppList.cached.orEmpty(); filter() }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    private fun options(e: AppEntry) {
        val extra = Shortcuts.isExtra(e.target)
        val pkg = Shortcuts.packageOf(e.target)
        val items = mutableListOf<Pair<String, () -> Any?>>()
        if (!extra) {
            items += "Shortcuts…" to { Shortcuts.showShortcuts(this, e.target, e.label) }
            items += "Activities…" to { Shortcuts.showActivities(this, e.target, e.label) }
        }
        if (metro) items.add(0, if (StartTiles.isPinned(this, e.target))
            "Unpin from Start" to { StartTiles.unpin(this, StartTiles.APP + e.target); toast("Unpinned ${e.label}") }
        else "Pin to Start" to { StartTiles.pin(this, e.target); toast("${e.label} pinned to Start") })
        if (!metro) items += Prefs.DPAD.map { (pref, name) -> "Home $name key" to { Prefs.set(this, pref, e.target) } }
        items += "Home number key…" to {
            Ui.choose(this, "Number key", (1..9).map { n ->
                    "$n  ${Apps.entry(this, Prefs.get(this, Prefs.num(n)))?.label ?: "not set"}" to
                        { Prefs.set(this, Prefs.num(n), e.target) }
                })
        }
        items += (if (Prefs.getBool(this, Prefs.SORT_USED, false)) "Sort apps A to Z" else "Sort apps by most used") to {
            Prefs.setBool(this, Prefs.SORT_USED, !Prefs.getBool(this, Prefs.SORT_USED, false)); filter()
        }
        items += "Rename…" to { rename(e) }
        if (Prefs.customLabel(this, e.target) != null) items += "Reset name" to { setLabel(e, null) }
        items += "Change icon" to { Icons.change(this, e) }
        items += "Reset icon" to { Icons.setOverride(this, e.target, null, null); adapter.notifyDataSetChanged() }
        items += "Hide from launcher" to {
            Prefs.setHidden(this, e.target, true)
            all = all.filter { it.target != e.target }; filter()
            Bg.run({ AppList.refresh(this) })
            android.widget.Toast.makeText(this, "${e.label} hidden. Undo in Settings > Hidden apps", android.widget.Toast.LENGTH_LONG).show()
        }
        if (extra) items += "Remove from drawer" to { Shortcuts.removeFromDrawer(this, e.target); reload() }
        else items += "App info" to {
            Apps.start(this, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        }
        Ui.choose(this, e.label, items)
    }

    /** Ask for a new name; an empty name goes back to the app's own label. */
    private fun rename(e: AppEntry) {
        val input = android.widget.EditText(this).apply {
            setText(e.label)
            setSelection(text.length)
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val box = android.widget.FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Rename ${e.label}")
            .setMessage("Leave empty to use the app's own name")
            .setView(box)
            .setPositiveButton("Save") { _, _ -> setLabel(e, input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .create()
        input.setOnEditorActionListener { _, _, _ -> setLabel(e, input.text.toString()); dialog.dismiss(); true }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private fun setLabel(e: AppEntry, label: String?) {
        Prefs.setCustomLabel(this, e.target, label)
        val updated = Apps.renamed(this, e.copy(label = Apps.rawEntry(this, e.target)?.label ?: e.label))
        all = (all.filter { it.target != e.target } + updated).sortedBy { it.label.lowercase() }
        filter()
        Bg.run({ AppList.refresh(this) })
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        if (Keys.handleAppKey(this, code, event)) return true
        Keys.digit(code)?.let { clearTyped(); query += it; filter(); return true }
        if (Keys.isDelete(code) && query.isNotEmpty()) { clearTyped(); query = query.dropLast(1); filter(); return true }
        if (code == KeyEvent.KEYCODE_BACK && query.isNotEmpty()) { clearTyped(); query = ""; filter(); return true }
        return super.onKeyDown(code, event)
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(pos: Int) = rows[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(pos: Int) = if (rows[pos] is AppEntry) 0 else 1
        override fun areAllItemsEnabled() = true
        override fun isEnabled(pos: Int) = true   // letter headers open the letter grid
        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
            val item = rows[pos]
            if (item is String) {   // letter header
                val h = convert ?: layoutInflater.inflate(R.layout.item_letter, parent, false)
                h.findViewById<TextView>(R.id.label).text = item.uppercase()
                return h
            }
            val v = convert ?: layoutInflater.inflate(
                if (!grid) (if (metro) R.layout.item_metro_row else R.layout.item_row)
                else if (metro) R.layout.item_tile else R.layout.item_grid, parent, false)
            val e = item as AppEntry
            val iv = v.findViewById<ImageView>(R.id.icon)
            iv.tag = e.target
            Icons.load(this@AppsActivity, e) { d, instant ->
                if (iv.tag != e.target) return@load   // this cell was reused for another app meanwhile
                iv.setImageDrawable(d)
                if (!instant && Anim.on(iv.context)) { iv.alpha = 0f; iv.animate().alpha(1f).setDuration(150).start() }
            }
            v.findViewById<TextView>(R.id.label).text = e.label
            Ui.paint(v, pos == list.selectedItemPosition)
            return v
        }
    }
}
