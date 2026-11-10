package com.plaintextlab.dumblauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File

/**
 * Makes a blurred copy of the wallpaper for the app drawer.
 *
 * Android's own window blur is normally disabled on Android Go phones, so we blur in
 * software: shrink the picture to a few dozen pixels, box-blur that tiny image, and let
 * the GPU stretch it back up smoothly. That costs a few milliseconds, once per wallpaper.
 */
object Blur {
    private const val SMALL_WIDTH = 36
    private var cached: Bitmap? = null

    private fun blurFile(c: Context) = File(c.filesDir, "wall_blur.png")
    private fun sourceFile(c: Context) = File(c.filesDir, "wall_source.jpg")

    /** The blurred wallpaper, or null if it hasn't been made (or can't be). */
    fun get(c: Context): Bitmap? {
        cached?.let { return it }
        val f = blurFile(c)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.also { cached = it }
    }

    /** Keep a small copy of a picture the user chose, so we can blur it even if Android won't let us read the wallpaper. */
    fun saveSource(c: Context, uri: Uri) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 720) sample *= 2
        val bmp = c.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return
        sourceFile(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    /** Rebuild the blurred copy. Run off the main thread. Returns false if no wallpaper could be read. */
    fun rebuild(c: Context): Boolean {
        val src = systemWallpaper(c) ?: BitmapFactory.decodeFile(sourceFile(c).path) ?: return false
        val small = shrinkToScreen(c, src)
        repeat(3) { boxBlur(small, 2) }
        blurFile(c).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cached = small
        return true
    }

    /** Window background: the tiny blurred image, smoothly stretched to fill the screen. */
    fun drawable(c: Context): Drawable? = get(c)?.let {
        BitmapDrawable(c.resources, it).apply { isFilterBitmap = true }
    }

    private fun systemWallpaper(c: Context): Bitmap? = try {
        @Suppress("MissingPermission") // reading the wallpaper may be refused; then we fall back
        val d = WallpaperManager.getInstance(c).drawable ?: return null
        if (d is BitmapDrawable && d.bitmap != null) d.bitmap else {
            val w = d.intrinsicWidth.coerceIn(1, 1080); val h = d.intrinsicHeight.coerceIn(1, 1920)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                d.setBounds(0, 0, w, h); d.draw(Canvas(it))
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Centre-crop to the screen's shape, then shrink to SMALL_WIDTH pixels wide. */
    private fun shrinkToScreen(c: Context, src: Bitmap): Bitmap {
        val dm = c.resources.displayMetrics
        val screenRatio = dm.heightPixels.toFloat() / dm.widthPixels
        var w = src.width
        var h = (w * screenRatio).toInt()
        if (h > src.height) { h = src.height; w = (h / screenRatio).toInt() }
        val cropped = Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h)
        val smallH = (SMALL_WIDTH * screenRatio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(cropped, SMALL_WIDTH, smallH, true).copy(Bitmap.Config.ARGB_8888, true)
    }

    /** One horizontal + one vertical box-blur pass, in place. */
    private fun boxBlur(b: Bitmap, r: Int) {
        val w = b.width; val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        boxBlur(px, w, h, r)
        b.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Box blur on ARGB pixels (pure Kotlin, unit-tested). Edges repeat the border pixel. */
    internal fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val out = IntArray(px.size)
        fun pass(len: Int, lines: Int, idx: (line: Int, i: Int) -> Int, from: IntArray, to: IntArray) {
            for (line in 0 until lines) for (i in 0 until len) {
                var a = 0; var rr = 0; var g = 0; var bb = 0; var n = 0
                for (k in -r..r) {
                    val p = from[idx(line, (i + k).coerceIn(0, len - 1))]
                    a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; bb += p and 255; n++
                }
                to[idx(line, i)] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (bb / n)
            }
        }
        pass(w, h, { y, x -> y * w + x }, px, out)   // horizontal
        pass(h, w, { x, y -> y * w + x }, out, px)   // vertical
    }
}
