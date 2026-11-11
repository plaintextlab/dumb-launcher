package com.plaintextlab.dumblauncher

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import java.util.concurrent.ConcurrentHashMap

/**
 * Picks the icon for an app, in this order:
 * 1. an icon the user picked for that app, 2. the selected icon pack, 3. the system icon,
 * restyled by the pack (its iconback / mask) if it has one, else drawn in the chosen style.
 * Many OEM system icons are adaptive icons with a white background layer; the "Clean"
 * style draws only the foreground layer so there is no white box.
 */
object Icons {
    /** Stored as the index; Metro is the default. */
    val STYLES = listOf("Clean (no white box)", "Original", "Glossy tiles", "Metro (flat accent tiles)")
    const val TILES = 2
    const val METRO = 3
    fun style(c: Context) = Prefs.getInt(c, Prefs.ICON_STYLE, METRO)
    private const val SIZE_PX = 144
    private object None
    private val cache = ConcurrentHashMap<String, Any>()   // Drawable, or None for "no icon"

    fun clear() { cache.clear(); art.clear() }

    /** A Start tile's artwork: the white glyph (transparent background) and the app's tile colour. */
    data class Art(val glyph: Bitmap, val colour: Int)
    private val art = ConcurrentHashMap<String, Art>()

    /** Loads the glyph and colour for [e] (cached), calling [onReady] on the UI thread. */
    fun loadArt(c: Context, e: AppEntry, onReady: (Art?) -> Unit) {
        art[e.target]?.let { onReady(it); return }
        val app = c.applicationContext
        Bg.run({
            val src = picked(app, e) ?: IconPacks.current(app)?.iconFor(e.target) ?: e.icon ?: Apps.rawIcon(app, e.target)
            src?.let { Art(Metro.glyph(it, SIZE_PX), Metro.colourFor(app, it)).also { a -> art[e.target] = a } }
        }) { onReady(it) }
    }

    /** Loads now. Fine for a handful of icons; the drawer uses [load] instead. */
    fun get(c: Context, e: AppEntry): Drawable? {
        cache[e.target]?.let { return it as? Drawable }
        return resolve(c.applicationContext, e).also { cache[e.target] = it ?: None }
    }

    /**
     * Shows a cached icon at once; otherwise loads it on a background thread and calls
     * [onReady] on the UI thread. Returns true if the icon was already cached.
     */
    fun load(c: Context, e: AppEntry, onReady: (Drawable?, Boolean) -> Unit) {
        cache[e.target]?.let { onReady(it as? Drawable, true); return }
        val app = c.applicationContext
        Bg.run({ get(app, e) }) { onReady(it, false) }
    }

    private fun resolve(c: Context, e: AppEntry): Drawable? {
        if (e.target == Prefs.SPECIAL_APPS) return e.icon
        if (style(c) == METRO) {
            // Every icon (picked, from a pack, or the app's own) becomes a flat accent tile.
            val src = picked(c, e) ?: IconPacks.current(c)?.iconFor(e.target) ?: e.icon ?: Apps.rawIcon(c, e.target) ?: return null
            return BitmapDrawable(c.resources, Metro.tile(src, SIZE_PX, Metro.colourFor(c, src)))
        }
        picked(c, e)?.let { return it }
        val pack = IconPacks.current(c)
        pack?.iconFor(e.target)?.let { return it }
        val raw = e.icon ?: Apps.rawIcon(c, e.target) ?: return null
        // Apps the pack has no icon for: style them the way the pack says, so they still match.
        pack?.restyle(raw, e.target, SIZE_PX)?.let { return BitmapDrawable(c.resources, it) }
        if (style(c) == TILES) return BitmapDrawable(c.resources, Tiles.render(raw, SIZE_PX))
        return system(c, raw)
    }

    /** The icon the user picked for this app, if any. */
    private fun picked(c: Context, e: AppEntry): Drawable? {
        val saved = Prefs.get(c, Prefs.iconOverride(e.target)) ?: return null
        val (pkg, name) = saved.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return IconPacks.get(c, pkg)?.drawable(name)
    }

    private fun system(c: Context, icon: Drawable?): Drawable? {
        val clean = style(c) == 0
        if (!clean || icon !is AdaptiveIconDrawable || icon.foreground == null) return icon
        val bmp = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val fg = icon.foreground.mutate()
        // Adaptive layers are 108dp with the visible 72dp in the middle: extend by 25% each side.
        val pad = SIZE_PX / 4
        fg.setBounds(-pad, -pad, SIZE_PX + pad, SIZE_PX + pad)
        fg.draw(Canvas(bmp))
        return BitmapDrawable(c.resources, bmp)
    }

    fun setOverride(c: Context, target: String, pkg: String?, name: String?) {
        Prefs.set(c, Prefs.iconOverride(target), if (pkg == null) null else "$pkg|$name")
        cache.remove(target)
    }

    /** Let the user pick an icon for one app: choose the pack (if several), then the icon. */
    fun change(a: Activity, e: AppEntry) {
        val packs = IconPacks.installed(a)
        if (packs.isEmpty()) {
            Toast.makeText(a, "Install an icon pack first", Toast.LENGTH_LONG).show(); return
        }
        val open = { pkg: String -> a.startActivity(IconPickerActivity.intent(a, e.target, e.label, pkg)) }
        if (packs.size == 1) open(packs[0].first)
        else Ui.choose(a, "Icons from…", packs.map { (pkg, label) -> label to { open(pkg) } })
    }

    fun choosePack(a: Activity, done: () -> Unit) {
        val items = listOf("None (system icons)" to null as String?) + IconPacks.installed(a).map { it.second to it.first }
        Ui.choose(a, "Icon pack", items.map { (label, pkg) ->
            label to { Prefs.set(a, Prefs.ICON_PACK, pkg); clear(); done() }
        })
    }
}
