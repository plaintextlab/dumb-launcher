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
        val original = px.copyOf()
        val plate = if (mono == null) plateColour(px, size) else null
        if (plate != null) unplate(px, plate)              // symbol on its own disc / square
        if (plate == null || px.count { Color.alpha(it) > 24 } < px.size / 100) {
            original.copyInto(px)                          // no plate, or nothing left on it
            whiten(px, keepShape = mono != null || mostlyWhite(px))
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return fit(bmp, px, size)
    }

    /** Crop the glyph to what is drawn and scale it so every glyph has the same size. */
    private fun fit(src: Bitmap, px: IntArray, size: Int): Bitmap {
        var l = size; var t = size; var r = -1; var b = -1
        for (y in 0 until size) for (x in 0 until size) {
            if (Color.alpha(px[y * size + x]) > 24) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
        }
        if (r < l || b < t) return src
        val w = r - l + 1f; val h = b - t + 1f
        val target = size * 0.46f
        val k = minOf(target / maxOf(w, h), 2.5f)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val dw = w * k; val dh = h * k
        val dst = android.graphics.RectF((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2)
        Canvas(out).drawBitmap(src, android.graphics.Rect(l, t, r + 1, b + 1), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    /**
     * If the artwork is a symbol drawn on its own solid disc or square (most older icons), the
     * colour of that plate; else null. Detected by the drawn area filling most of its bounds
     * and one colour covering most of it.
     */
    fun plateColour(px: IntArray, size: Int): Int? {
        var l = size; var t = size; var r = -1; var b = -1; var opaque = 0
        val counts = HashMap<Int, Int>()
        for (y in 0 until size) for (x in 0 until size) {
            val p = px[y * size + x]
            if (Color.alpha(p) < 200) continue
            opaque++
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            val q = (p and 0xF0F0F0)                      // group near-identical colours
            counts[q] = (counts[q] ?: 0) + 1
        }
        if (opaque == 0) return null
        val fill = opaque.toFloat() / ((r - l + 1) * (b - t + 1))
        val (top, n) = counts.maxByOrNull { it.value } ?: return null
        if (fill < 0.75f || n < opaque * 0.45f) return null
        return top or 0xFF080808.toInt()
    }

    /** Keep only what differs from the plate colour, as white. */
    fun unplate(px: IntArray, plate: Int) {
        for (i in px.indices) {
            val p = px[i]
            val a = Color.alpha(p)
            if (a == 0) continue
            val d = (kotlin.math.abs(Color.red(p) - Color.red(plate)) +
                kotlin.math.abs(Color.green(p) - Color.green(plate)) +
                kotlin.math.abs(Color.blue(p) - Color.blue(plate))) / 3f
            px[i] = Color.argb((a * smooth(18f, 60f, d)).toInt(), 255, 255, 255)
        }
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
