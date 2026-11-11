package com.plaintextlab.dumblauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable

/**
 * The "Tiles" icon style: every app on the same glossy rounded-square tile, at the same size,
 * so icons from different makers look like one family (in the spirit of old keypad-phone
 * menus). The tile takes the app's own background colour when it has one, otherwise a light
 * tint of the icon's main colour, so the artwork always stands out from its tile.
 */
object Tiles {
    /** Draws [icon] on a tile, [size] px square. */
    fun render(icon: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val inset = size * 0.04f                       // room for the soft shadow below
        val side = size - inset * 2.6f
        val left = (size - side) / 2
        val box = RectF(left, inset * 0.6f, left + side, inset * 0.6f + side)
        val radius = box.width() * 0.24f

        val adaptive = icon as? AdaptiveIconDrawable
        val fg = adaptive?.foreground ?: icon
        // The app's own background colour if it has a real one (not white), else a light tint of the artwork.
        val base = adaptive?.background?.let { mainColour(pixels(it, size / 4, true)) }?.takeUnless { isNearWhite(it) }
            ?: tint(mainColour(pixels(fg, size / 2, adaptive != null)))

        // Shadow, then the tile with a top-to-bottom gradient.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x55000000
        canvas.drawRoundRect(RectF(box.left, box.top + inset * 0.8f, box.right, box.bottom + inset * 0.8f), radius, radius, paint)
        paint.color = Color.WHITE
        paint.shader = LinearGradient(0f, box.top, 0f, box.bottom, shade(base, 0.18f), shade(base, -0.16f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(box, radius, radius, paint)

        // The artwork: adaptive foregrounds keep their own safe-zone padding; others get 18% margin.
        canvas.save()
        val clip = android.graphics.Path().apply { addRoundRect(box, radius, radius, android.graphics.Path.Direction.CW) }
        canvas.clipPath(clip)
        val art = fg.constantState?.newDrawable()?.mutate() ?: fg.mutate()
        if (adaptive != null) {
            val grow = box.width() * 0.25f
            art.setBounds((box.left - grow).toInt(), (box.top - grow).toInt(), (box.right + grow).toInt(), (box.bottom + grow).toInt())
        } else {
            val m = box.width() * 0.18f
            art.setBounds((box.left + m).toInt(), (box.top + m).toInt(), (box.right - m).toInt(), (box.bottom - m).toInt())
        }
        art.draw(canvas)

        // Gloss: a bright curved sheen over the top half, and a faint light at the bottom edge.
        paint.shader = LinearGradient(0f, box.top, 0f, box.centerY(), 0x66FFFFFF, 0x08FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(box.left - box.width() * 0.25f, box.top - box.height() * 0.55f,
            box.right + box.width() * 0.25f, box.centerY() + box.height() * 0.02f), paint)
        paint.shader = RadialGradient(box.centerX(), box.bottom, box.width() * 0.5f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRect(box, paint)
        canvas.restore()

        // A thin edge so light tiles don't melt into a light wallpaper.
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 96f
        paint.color = shade(base, -0.35f) and 0x00FFFFFF or 0x66000000
        canvas.drawRoundRect(box, radius, radius, paint)
        return bmp
    }

    private fun pixels(d: Drawable, size: Int, adaptive: Boolean): IntArray {
        val s = maxOf(size, 8)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val copy = d.constantState?.newDrawable()?.mutate() ?: d
        val grow = if (adaptive) s / 4 else 0
        copy.setBounds(-grow, -grow, s + grow, s + grow)
        copy.draw(Canvas(bmp))
        return IntArray(s * s).also { bmp.getPixels(it, 0, s, 0, 0, s, s); bmp.recycle() }
    }

    /** The icon's main colour: an average that favours strong, visible colours over greys. */
    fun mainColour(px: IntArray): Int {
        var r = 0.0; var g = 0.0; var b = 0.0; var w = 0.0
        val hsv = FloatArray(3)
        for (p in px) {
            val a = Color.alpha(p)
            if (a < 128) continue
            Color.colorToHSV(p, hsv)
            val weight = 0.05 + hsv[1] * hsv[2]       // saturated and bright counts most
            r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight; w += weight
        }
        if (w == 0.0) return Color.GRAY
        return Color.rgb((r / w).toInt(), (g / w).toInt(), (b / w).toInt())
    }

    /** A light, gently saturated version of [c], for the tile behind full-colour artwork. */
    fun tint(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = (hsv[1] * 0.35f).coerceAtMost(0.30f)
        hsv[2] = 0.97f
        return Color.HSVToColor(hsv)
    }

    /** Lighten (amount > 0) or darken (amount < 0). */
    fun shade(c: Int, amount: Float): Int {
        val t = if (amount > 0) 255f else 0f
        val k = kotlin.math.abs(amount)
        return Color.rgb(
            (Color.red(c) + (t - Color.red(c)) * k).toInt(),
            (Color.green(c) + (t - Color.green(c)) * k).toInt(),
            (Color.blue(c) + (t - Color.blue(c)) * k).toInt())
    }

    fun isNearWhite(c: Int) = Color.red(c) > 230 && Color.green(c) > 230 && Color.blue(c) > 230
}
