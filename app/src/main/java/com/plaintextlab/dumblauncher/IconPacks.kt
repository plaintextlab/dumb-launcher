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

    /** Every icon name in the pack, for the picker. */
    val names: List<String> by lazy {
        val list = LinkedHashSet<String>()
        read("drawable") { p -> p.getAttributeValue(null, "drawable")?.let { list += it } }
        if (list.isEmpty()) list += byComponent.values
        list.filter { id(it) != 0 }
    }

    init {
        read("appfilter") { p ->
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

    @Suppress("DiscouragedApi") // icon packs are addressed by resource name
    private fun id(name: String): Int =
        res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", pkg)

    fun drawable(name: String): Drawable? = try {
        id(name).takeIf { it != 0 }?.let { res.getDrawable(it, null) }
    } catch (e: Exception) {
        null
    }

    /** Calls [onItem] for each <item> tag of res/xml/<file>.xml or assets/<file>.xml. */
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
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") onItem(parser)
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
