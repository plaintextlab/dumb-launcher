package com.plaintextlab.dumblauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.LruCache
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

/** Grid of every icon in one pack. Type on the keypad to search by icon name (T9). */
class IconPickerActivity : BaseActivity() {

    companion object {
        fun intent(c: Context, target: String, label: String, pack: String) =
            Intent(c, IconPickerActivity::class.java)
                .putExtra("target", target).putExtra("label", label).putExtra("pack", pack)
    }

    private lateinit var grid: GridView
    private lateinit var title: TextView
    private var pack: IconPack? = null
    private var all: List<Pair<String, String>> = emptyList() // (name, T9 of name)
    private var shown: List<String> = emptyList()
    private var query = ""
    private val drawables = LruCache<String, Drawable>(120)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icons)
        title = findViewById(R.id.query)
        findViewById<TextView>(R.id.hint).text = "● Choose    0-9 Search    ↩ Cancel"
        grid = findViewById(R.id.grid)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> choose(shown[pos]) }
        Ui.trackHighlight(grid) { updateTitle() }
        Anim.cascade(grid)
        pack = IconPacks.get(this, intent.getStringExtra("pack"))
        all = pack?.names.orEmpty().map { it to T9.encode(it.lowercase().replace('_', ' ')) }
        filter()
    }

    private fun matches(t9: String) = query.isEmpty() || t9.split('0').any { it.startsWith(query) }

    private fun filter() {
        shown = all.filter { matches(it.second) }.map { it.first }
        adapter.notifyDataSetChanged()
        grid.requestFocus()
        grid.setSelection(0)
        updateTitle()
    }

    private fun updateTitle() {
        val label = intent.getStringExtra("label")
        val current = shown.getOrNull(grid.selectedItemPosition)?.replace('_', ' ') ?: ""
        val search = if (query.isEmpty()) "" else "  ·  $query"
        title.text = "Icon for $label$search\n${shown.size} icons   $current"
    }

    private fun choose(name: String) {
        Icons.setOverride(this, intent.getStringExtra("target")!!, pack?.pkg, name)
        finish()
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        Keys.digit(code)?.let { query += it; filter(); return true }
        if (Keys.isDelete(code) && query.isNotEmpty()) { query = query.dropLast(1); filter(); return true }
        if (code == KeyEvent.KEYCODE_BACK && query.isNotEmpty()) { query = ""; filter(); return true }
        return super.onKeyDown(code, event)
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(pos: Int) = shown[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
            val v = convert ?: layoutInflater.inflate(R.layout.item_icon, parent, false)
            val name = shown[pos]
            val iv = v.findViewById<ImageView>(R.id.icon)
            iv.tag = name
            val cached = drawables.get(name)
            if (cached != null) iv.setImageDrawable(cached) else {
                iv.setImageDrawable(null)
                Bg.run({ pack?.drawable(name) }) { d ->
                    if (d != null) drawables.put(name, d)
                    if (iv.tag == name) iv.setImageDrawable(d)
                }
            }
            Ui.paint(v, pos == grid.selectedItemPosition)
            return v
        }
    }
}
