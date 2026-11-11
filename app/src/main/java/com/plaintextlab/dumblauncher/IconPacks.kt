package com.plaintextlab.dumblauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Reads third-party icon packs in the common ADW / Nova / Apex format:
 * `appfilter.xml` maps app components to drawable names, and the optional
 * `drawable.xml` lists every icon in the pack (used by the per-app picker).
 */
class IconPack(val pkg: String, val label: String, private val res: Resources) {

    private val byComponent = HashMap<String, String>()
    private val byPackage = HashMap<String, String>()

    // How the pack styles apps it has no icon for: draw the app icon on [backs], cut by [mask],
    // with [upon] on top, shrunk to [scale]. Most packs include these.
    private val backs = mutableListOf<String>()
    private var upon: String? = null
    private var mask: String? = null
    private var scale = 1f

    /** Every icon name in the pack, for the picker. */
    val names: List<String> by lazy {
        val list = LinkedHashSet<String>()
        read("drawable") { p -> if (p.name == "item") p.getAttributeValue(null, "drawable")?.let { list += it } }
        if (list.isEmpty()) list += byComponent.values
        list.filter { id(it) != 0 }
    }

    init {
        read("appfilter") { p ->
            when (p.name) {
                "iconback" -> { for (i in 0 until p.attributeCount) backs += p.getAttributeValue(i); return@read }
                "iconupon" -> { if (p.attributeCount > 0) upon = p.getAttributeValue(0); return@read }
                "iconmask" -> { if (p.attributeCount > 0) mask = p.getAttributeValue(0); return@read }
                "scale" -> { p.getAttributeValue(null, "factor")?.toFloatOrNull()?.let { scale = it }; return@read }
                "item" -> {}
                else -> return@read
            }
            val comp = p.getAttributeValue(null, "component") ?: return@read
            val drawable = p.getAttributeValue(null, "drawable") ?: return@read
            val cn = ComponentName.unflattenFromString(
                comp.removePrefix("ComponentInfo{").removeSuffix("}")) ?: return@read
            byComponent.putIfAbsent(cn.flattenToString(), drawable)
            byPackage.putIfAbsent(cn.packageName, drawable)
        }
    }

    /** The pack's icon for an app, matching the exact activity first, then just the package. */
    fun iconFor(target: String): Drawable? {
        val cn = ComponentName.unflattenFromString(target) ?: return null
        val name = byComponent[cn.flattenToString()] ?: byPackage[cn.packageName] ?: return null
        return drawable(name)
    }

    /** [icon] restyled the way this pack styles unknown apps, or null if the pack doesn't say how. */
    fun restyle(icon: android.graphics.drawable.Drawable, key: String, size: Int): android.graphics.Bitmap? {
        val back = backs.mapNotNull { drawable(it) }.let { if (it.isEmpty()) return null else it[Math.floorMod(key.hashCode(), it.size)] }
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        back.setBounds(0, 0, size, size); back.draw(canvas)
        // The app icon on its own layer, so the mask cuts only the icon.
        val layer = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val lc = android.graphics.Canvas(layer)
        val s = (size * scale).toInt(); val o = (size - s) / 2
        val art = (icon as? android.graphics.drawable.AdaptiveIconDrawable)?.let { a ->
            android.graphics.drawable.LayerDrawable(arrayOf(a.background, a.foreground).filterNotNull().toTypedArray()).also { it.setBounds(o - s / 4, o - s / 4, o + s + s / 4, o + s + s / 4) }
        } ?: icon.mutate().also { it.setBounds(o, o, o + s, o + s) }
        art.draw(lc)
        mask?.let { drawable(it) }?.let { m ->
            val mb = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            m.setBounds(0, 0, size, size); m.draw(android.graphics.Canvas(mb))
            lc.drawBitmap(mb, 0f, 0f, android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT) })
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        upon?.let { drawable(it) }?.let { u -> u.setBounds(0, 0, size, size); u.draw(canvas) }
        return bmp
    }

    @Suppress("DiscouragedApi") // icon packs are addressed by resource name
    private fun id(name: String): Int =
        res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", pkg)

    fun drawable(name: String): Drawable? = try {
        id(name).takeIf { it != 0 }?.let { res.getDrawable(it, null) }
    } catch (e: Exception) {
        null
    }

    /** Calls [onItem] for each tag (check parser.name) of res/xml/<file>.xml or assets/<file>.xml. */
    @Suppress("DiscouragedApi")
    private fun read(file: String, onItem: (XmlPullParser) -> Unit) {
        try {
            val xmlId = res.getIdentifier(file, "xml", pkg)
            val parser: XmlPullParser = if (xmlId != 0) res.getXml(xmlId) else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("$file.xml"), "UTF-8")
                }
            }
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) onItem(parser)
            }
        } catch (e: Exception) {
            // missing or malformed file: the pack just has fewer icons
        }
    }
}

object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME",
    )
    private val loaded = HashMap<String, IconPack?>()

    /** Installed packs as (package, label). */
    fun installed(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        return ACTIONS.flatMap { pm.queryIntentActivities(Intent(it), 0) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    @Synchronized // called from background threads too
    fun get(c: Context, pkg: String?): IconPack? {
        if (pkg.isNullOrEmpty()) return null
        return loaded.getOrPut(pkg) {
            try {
                val pm = c.packageManager
                val label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                IconPack(pkg, label, pm.getResourcesForApplication(pkg))
            } catch (e: Exception) {
                null // uninstalled
            }
        }
    }

    fun current(c: Context) = get(c, Prefs.get(c, Prefs.ICON_PACK))
}
