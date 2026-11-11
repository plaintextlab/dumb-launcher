package com.plaintextlab.dumblauncher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.TextView

object Ui {
    /** Accent colours (Metro palette). The first is the default. */
    val COLOURS = Metro.ACCENTS

    /**
     * A list of colours, each with a square swatch of how it looks. [items] are
     * (name, colour to show, what choosing it does); [current] is the index chosen now.
     */
    fun chooseColour(a: Activity, title: String, items: List<Triple<String, Int, () -> Any?>>, current: Int) {
        val d = a.resources.displayMetrics.density
        val adapter = object : android.widget.ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, items.map { it.first }) {
            override fun getView(pos: Int, convert: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(pos, convert, parent) as TextView
                v.text = items[pos].first + if (pos == current) "   ✓" else ""
                val sw = (28 * d).toInt()
                v.setCompoundDrawablesRelative(android.graphics.drawable.GradientDrawable().apply {
                    setColor(items[pos].second)
                    setStroke((1 * d).toInt().coerceAtLeast(1), 0x66FFFFFF)   // outline so dark / see-through still shows
                    setSize(sw, sw); setBounds(0, 0, sw, sw)
                }, null, null, null)
                v.compoundDrawablePadding = (14 * d).toInt()
                return v
            }
        }
        AlertDialog.Builder(a).setTitle(title)
            .setAdapter(adapter) { _, which -> items[which].third() }
            .show().listView?.setSelection(current.coerceAtLeast(0))
    }

    /** A D-pad navigable list dialog. */
    fun choose(a: Activity, title: String, items: List<Pair<String, () -> Any?>>) {
        AlertDialog.Builder(a)
            .setTitle(title)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    /** Open the app list in "pick" mode; the chosen app is saved under [prefKey]. */
    fun pick(a: Activity, prefKey: String, title: String) {
        a.startActivity(AppsActivity.pickIntent(a, prefKey, title))
    }

    private const val ZOOM = 1.2f

    /** Colour one row/cell: only the selected one gets the highlight and a zoomed icon. */
    fun paint(v: View, selected: Boolean, animate: Boolean = false) {
        // Metro tile in the drawer: a white frame and a small lift instead of a coloured bar.
        if (v is SquareLayout) {
            v.outlined = selected
            val s = if (selected && Prefs.getBool(v.context, Prefs.ZOOM, true)) 1.06f else 1f
            Anim.zoom(v, s, animate)
            return
        }
        v.findViewById<View>(R.id.icon)?.let { icon ->
            val s = if (selected && Prefs.getBool(v.context, Prefs.ZOOM, true)) ZOOM else 1f
            Anim.zoom(icon, s, animate)
        }
        val colour = Prefs.getInt(v.context, Prefs.HIGHLIGHT, COLOURS[0].second)
        // Setting a background resets padding to the drawable's own (the inset), so keep the view's.
        val pad = intArrayOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom)
        if (selected) {
            val bg = highlight(v, colour)
            v.background = bg
            v.setPadding(pad[0], pad[1], pad[2], pad[3])
            if (animate) Anim.fadeIn(bg, v.context)
        } else {
            v.background = null
            v.setPadding(pad[0], pad[1], pad[2], pad[3])
        }
        val text = if (selected) readableOn(colour) else v.context.getColor(R.color.fg)
        v.findViewById<TextView>(R.id.label)?.setTextColor(text)
        v.findViewById<android.widget.ImageView>(R.id.sicon)?.setColorFilter(text)
        v.findViewById<TextView>(R.id.value)?.setTextColor(
            if (selected) text else v.context.getColor(R.color.dim))
    }

    /** The selection: a flat, square bar in the accent colour, edge to edge. */
    private fun highlight(@Suppress("UNUSED_PARAMETER") v: View, colour: Int): android.graphics.drawable.Drawable =
        android.graphics.drawable.ColorDrawable(colour)

    /** Black text on light colours, white on dark ones. */
    private fun readableOn(c: Int): Int {
        val lum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
        return if (lum > 150) Color.BLACK else Color.WHITE
    }

    /** Repaint visible items whenever the D-pad moves the selection. */
    fun trackHighlight(list: AbsListView, onMove: () -> Unit = {}) {
        val repaint = {
            for (i in 0 until list.childCount) {
                paint(list.getChildAt(i), list.firstVisiblePosition + i == list.selectedItemPosition, animate = true)
            }
            onMove()
        }
        list.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = repaint()
            override fun onNothingSelected(p: AdapterView<*>?) = repaint()
        }
    }
}
