package com.plaintextlab.dumblauncher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.TextView

object Ui {
    val COLOURS = listOf(
        "Yellow" to 0xFFFFC107.toInt(), "Orange" to 0xFFFF6D00.toInt(), "Red" to 0xFFE53935.toInt(),
        "Pink" to 0xFFEC407A.toInt(), "Purple" to 0xFF8E24AA.toInt(), "Blue" to 0xFF1E88E5.toInt(),
        "Cyan" to 0xFF00ACC1.toInt(), "Green" to 0xFF43A047.toInt(), "White" to 0xFFFFFFFF.toInt(),
        "Grey" to 0xFF616161.toInt(),
    )

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
        v.findViewById<View>(R.id.icon)?.let { icon ->
            val s = if (selected && Prefs.getBool(v.context, Prefs.ZOOM, true)) ZOOM else 1f
            Anim.zoom(icon, s, animate)
        }
        val colour = Prefs.getInt(v.context, Prefs.HIGHLIGHT, COLOURS[0].second)
        if (selected) {
            val bg = highlight(v, colour)
            v.background = bg
            if (animate) Anim.fadeIn(bg, v.context)
        } else {
            v.background = null
        }
        val text = if (selected) readableOn(colour) else v.context.getColor(R.color.fg)
        v.findViewById<TextView>(R.id.label)?.setTextColor(text)
        v.findViewById<android.widget.ImageView>(R.id.sicon)?.setColorFilter(text)
        v.findViewById<TextView>(R.id.value)?.setTextColor(
            if (selected) text else v.context.getColor(R.color.dim))
    }

    /** The selection: a rounded tile in the highlight colour, inset a little from the edges. */
    private fun highlight(v: View, colour: Int): android.graphics.drawable.Drawable {
        val dp = v.resources.displayMetrics.density
        val tile = android.graphics.drawable.GradientDrawable().apply {
            setColor(colour); cornerRadius = 14 * dp
        }
        return android.graphics.drawable.InsetDrawable(tile, (4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
    }

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
