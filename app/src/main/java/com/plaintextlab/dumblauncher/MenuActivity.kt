package com.plaintextlab.dumblauncher

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

/** 3×3 grid. Keys 1-9 open the matching cell directly. */
class MenuActivity : BaseActivity() {

    override val zooms = true

    private lateinit var grid: GridView
    private var slots: List<AppEntry?> = emptyList()
    private var cellHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        findViewById<TextView>(R.id.hint).text = "● Open    1-9 Jump    ☆ Options"
        grid = findViewById(R.id.grid)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> open(pos) }
        grid.setOnItemLongClickListener { _, _, pos, _ -> options(pos); true }
        Ui.trackHighlight(grid)
        Anim.cascade(grid)
        // Three rows share the whole height: recompute whenever the grid is resized
        // (first layout, notification bar hidden/shown).
        grid.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val h = (bottom - top - grid.paddingTop - grid.paddingBottom - 2 * grid.verticalSpacing) / 3
            if (h > 0 && h != cellHeight) { cellHeight = h; grid.post { adapter.notifyDataSetChanged() } }
        }
    }

    override fun onResume() {
        super.onResume()
        val keep = grid.selectedItemPosition.coerceAtLeast(0)
        slots = (1..9).map { Apps.entry(this, Prefs.get(this, Prefs.slot(it))) }
        adapter.notifyDataSetChanged()
        grid.requestFocus()
        grid.setSelection(keep)
    }

    private fun open(pos: Int) {
        val slot = slots.getOrNull(pos)
        if (slot == null) Ui.pick(this, Prefs.slot(pos + 1), "Pick app for ${pos + 1}")
        else Apps.launch(this, slot.target)
    }

    private fun options(pos: Int) {
        val n = pos + 1
        Ui.choose(this, "Slot $n", listOf(
            "Change app" to { Ui.pick(this, Prefs.slot(n), "Pick app for $n") },
            "Change icon" to { slots[pos]?.takeIf { it.target != Prefs.SPECIAL_APPS }?.let { Icons.change(this, it) } },
            "Reset icon" to { slots[pos]?.let { Icons.setOverride(this, it.target, null, null); onResume() } },
            "Make it All apps" to { Prefs.set(this, Prefs.slot(n), Prefs.SPECIAL_APPS); onResume() },
            "Empty this slot" to { Prefs.set(this, Prefs.slot(n), null); onResume() },
            "Reset whole menu" to { Prefs.resetSlots(this); onResume() },
        ))
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        if (Keys.handleAppKey(this, code, event)) return true
        Keys.digit(code)?.takeIf { it in 1..9 }?.let {
            grid.setSelection(it - 1); open(it - 1); return true
        }
        if (Keys.isOptions(code)) { options(grid.selectedItemPosition.coerceAtLeast(0)); return true }
        return super.onKeyDown(code, event)
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = 9
        override fun getItem(pos: Int) = slots.getOrNull(pos)
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
            val v = convert ?: layoutInflater.inflate(R.layout.item_grid, parent, false)
            val e = slots.getOrNull(pos)
            if (cellHeight > 0) {
                v.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellHeight)
                // Icon takes about half the cell, leaving room for the label and the zoom.
                val side = minOf(cellHeight * 0.5f, grid.width / 3 * 0.6f).toInt()
                v.findViewById<ImageView>(R.id.icon).layoutParams.apply { width = side; height = side }
            }
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(e?.let { Icons.get(this@MenuActivity, it) })
            v.findViewById<TextView>(R.id.label).text = "${pos + 1} ${e?.label ?: "+ empty"}"
            Ui.paint(v, pos == grid.selectedItemPosition)
            return v
        }
    }
}
