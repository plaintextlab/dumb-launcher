package com.plaintextlab.dumblauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * The "Metro" look: flat square tiles in one accent colour with a white glyph, like the
 * tile-based phone UIs of the early 2010s. Every app gets the same tile, so the drawer reads
 * as one calm grid of colour instead of a jumble of logos.
 */
object Metro {
    /** Accent palette: strong flat colours that all carry white text. */
    val ACCENTS = listOf(
        "Cyan" to 0xFF1BA1E2.toInt(), "Cobalt" to 0xFF0050EF.toInt(), "Teal" to 0xFF00ABA9.toInt(),
        "Emerald" to 0xFF008A00.toInt(), "Lime" to 0xFF8CBF26.toInt(), "Indigo" to 0xFF6A00FF.toInt(),
        "Violet" to 0xFFAA00FF.toInt(), "Magenta" to 0xFFD80073.toInt(), "Crimson" to 0xFFA20025.toInt(),
        "Red" to 0xFFE51400.toInt(), "Orange" to 0xFFFA6800.toInt(), "Amber" to 0xFFF0A30A.toInt(),
        "Brown" to 0xFF825A2C.toInt(), "Steel" to 0xFF647687.toInt(), "Mauve" to 0xFF76608A.toInt(),
    )

    fun accent(c: Context) = Prefs.getInt(c, Prefs.HIGHLIGHT, ACCENTS[0].second)

    /** A square tile: [accent] background, the app's glyph in white, about half the tile. */
    fun tile(icon: Drawable, size: Int, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(accent)
        canvas.drawBitmap(glyph(icon, size), 0f, 0f, null)
        return bmp
    }

    /** The icon as a white glyph on transparent, centred, about half of [size]. */
    fun glyph(icon: Drawable, size: Int): Bitmap {
        val adaptive = icon as? AdaptiveIconDrawable
        val mono = if (Build.VERSION.SDK_INT >= 33) adaptive?.monochrome else null
        val art = (mono ?: adaptive?.foreground ?: icon).let { it.constantState?.newDrawable()?.mutate() ?: it.mutate() }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Adaptive layers are 108 units with the art in the middle 72 (and usually less):
        // drawing the layer at 0.9x of the tile puts the art at about half the tile.
        val box = if (adaptive != null) (size * 0.9f).toInt() else (size * 0.5f).toInt()
        val o = (size - box) / 2
        art.setBounds(o, o, o + box, o + box)
        art.draw(Canvas(bmp))

        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        whiten(px, keepShape = mono != null || mostlyWhite(px))
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }

    /**
     * Turn artwork into a white glyph. A white-on-colour icon becomes its white shape; a
     * coloured icon becomes a white shape with its light details (a lens, a page) cut out,
     * so it still reads as the same picture.
     */
    fun whiten(px: IntArray, keepShape: Boolean) {
        for (i in px.indices) {
            val p = px[i]
            val a = Color.alpha(p)
            if (a == 0) continue
            val keep = if (keepShape) 1f else 1f - smooth(0.70f, 0.92f, whiteness(p))
            px[i] = Color.argb((a * keep).toInt(), 255, 255, 255)
        }
    }

    /** True when most of the visible artwork is white or near-white. */
    fun mostlyWhite(px: IntArray): Boolean {
        var seen = 0; var white = 0
        for (p in px) {
            if (Color.alpha(p) < 128) continue
            seen++
            if (whiteness(p) > 0.8f) white++
        }
        return seen > 0 && white > seen * 0.6f
    }

    /** 1 for white, 0 for black or a strong colour. */
    private fun whiteness(p: Int): Float {
        val r = Color.red(p) / 255f; val g = Color.green(p) / 255f; val b = Color.blue(p) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val sat = if (max == 0f) 0f else (max - min) / max
        return (0.299f * r + 0.587f * g + 0.114f * b) * (1f - sat)
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

/** A frame that is always as tall as it is wide: one Metro tile in the drawer grid. */
class SquareLayout @JvmOverloads constructor(c: Context, attrs: AttributeSet? = null) : FrameLayout(c, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)

    /** White frame drawn over the tile when it is selected (keypad focus). */
    var outlined = false
        set(v) { field = v; invalidate() }

    private val frame = Paint().apply {
        style = Paint.Style.STROKE; color = Color.WHITE
        strokeWidth = 3 * c.resources.displayMetrics.density
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (outlined) {
            val h = frame.strokeWidth / 2
            canvas.drawRect(h, h, width - h, height - h, frame)
        }
    }
}
