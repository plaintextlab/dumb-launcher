package com.plaintextlab.dumblauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Many OEM icons are adaptive icons with a white background layer, which shows up as a
 * white box on a black launcher. "Clean" draws only the foreground layer.
 */
object Icons {
    val STYLES = listOf("Clean (no white box)", "Original")
    private const val SIZE_PX = 144
    private val cache = HashMap<String, Drawable>()

    fun clear() = cache.clear()

    fun get(c: Context, e: AppEntry): Drawable? {
        val icon = e.icon ?: return null
        val clean = Prefs.getInt(c, Prefs.ICON_STYLE, 0) == 0
        if (!clean || icon !is AdaptiveIconDrawable || icon.foreground == null) return icon
        return cache.getOrPut(e.target) { foregroundOnly(c, icon) }
    }

    private fun foregroundOnly(c: Context, icon: AdaptiveIconDrawable): Drawable {
        val bmp = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val fg = icon.foreground.mutate()
        // Adaptive layers are 108dp with the visible 72dp in the middle: extend by 25% each side.
        val pad = SIZE_PX / 4
        fg.setBounds(-pad, -pad, SIZE_PX + pad, SIZE_PX + pad)
        fg.draw(Canvas(bmp))
        return BitmapDrawable(c.resources, bmp)
    }
}
