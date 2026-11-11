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
    private val grid get() = Prefs.getBool(this, Prefs.DRAWER_GRID, true)
    private lateinit var queryView: TextView
    private var all: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
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
        list.setOnItemClickListener { _, _, pos, _ -> choose(shown[pos]) }
        list.setOnItemLongClickListener { _, _, pos, _ -> if (pickPref == null) options(shown[pos]); true }
        Ui.trackHighlight(list)
        Anim.cascade(list)
        // Show the remembered list at once; re-read installed apps in the background.
        AppList.cached?.let { all = it; filter() }
        Bg.run({ AppList.refresh(this) }) { changed ->
            if (changed == true || all.isEmpty()) { all = AppList.cached.orEmpty(); filter() }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged() // an icon may have changed
    }

    private fun filter() {
        shown = all.filter { it.matches(query) }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "All apps"
        queryView.text = if (query.isEmpty()) title else "$title  ·  $query  (${shown.size})"
        adapter.notifyDataSetChanged()
        list.requestFocus()
        list.setSelection(0)
    }

    private fun choose(e: AppEntry) {
        val pref = pickPref
        if (pref != null) { Prefs.set(this, pref, e.target); finish() }
        else Apps.launch(this, e.target)
    }

    /** Re-read the app list (after adding/removing a drawer shortcut). */
    fun reload() = Bg.run({ AppList.refresh(this) }) { all = AppList.cached.orEmpty(); filter() }

    private fun options(e: AppEntry) {
        val extra = Shortcuts.isExtra(e.target)
        val pkg = Shortcuts.packageOf(e.target)
        val items = mutableListOf<Pair<String, () -> Any?>>()
        if (!extra) {
            items += "Shortcuts…" to { Shortcuts.showShortcuts(this, e.target, e.label) }
            items += "Activities…" to { Shortcuts.showActivities(this, e.target, e.label) }
        }
        items += Prefs.DPAD.map { (pref, name) -> "Home $name key" to { Prefs.set(this, pref, e.target) } }
        items += "Home number key…" to {
            Ui.choose(this, "Number key", (0..9).map { n ->
                    "$n  ${Apps.entry(this, Prefs.get(this, Prefs.num(n)))?.label ?: "not set"}" to
                        { Prefs.set(this, Prefs.num(n), e.target) }
                })
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
        Keys.digit(code)?.let { query += it; filter(); return true }
        if (Keys.isDelete(code) && query.isNotEmpty()) { query = query.dropLast(1); filter(); return true }
        if (code == KeyEvent.KEYCODE_BACK && query.isNotEmpty()) { query = ""; filter(); return true }
        if (Keys.isOptions(code) && pickPref == null) {
            shown.getOrNull(list.selectedItemPosition)?.let { options(it) }; return true
        }
        return super.onKeyDown(code, event)
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(pos: Int) = shown[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
            val v = convert ?: layoutInflater.inflate(if (grid) R.layout.item_grid else R.layout.item_row, parent, false)
            val e = shown[pos]
            val iv = v.findViewById<ImageView>(R.id.icon)
            iv.tag = e.target
            Icons.load(this@AppsActivity, e) { d, instant ->
                if (iv.tag != e.target) return@load   // this cell was reused for another app meanwhile
                iv.setImageDrawable(d)
                if (!instant && Anim.on(iv.context)) { iv.alpha = 0f; iv.animate().alpha(1f).setDuration(150).start() }
            }
            v.findViewById<TextView>(R.id.label).text = shown[pos].label
            Ui.paint(v, pos == list.selectedItemPosition)
            return v
        }
    }
}
