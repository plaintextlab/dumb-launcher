package com.plaintextlab.dumblauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Process
import android.widget.Toast

/**
 * Extra things that can go in the drawer, the Menu or on an arrow key, besides plain apps:
 *  - app shortcuts ("shortcut:<package>|<id>"): the actions other launchers show when you
 *    hold an icon. Android only gives these to the default launcher.
 *  - activities ("activity:<package>/<class>"): an app's individual screens.
 */
object Shortcuts {
    const val SHORTCUT = "shortcut:"
    const val ACTIVITY = "activity:"

    private fun la(c: Context) = c.getSystemService(LauncherApps::class.java)

    fun isExtra(target: String) = target.startsWith(SHORTCUT) || target.startsWith(ACTIVITY)

    /** Package that a target belongs to. */
    fun packageOf(target: String) = target.removePrefix(SHORTCUT).removePrefix(ACTIVITY).substringBefore('/').substringBefore('|')

    private fun appLabel(c: Context, pkg: String) = try {
        c.packageManager.getApplicationInfo(pkg, 0).loadLabel(c.packageManager).toString()
    } catch (e: Exception) { pkg }

    // ---- app shortcuts ----

    fun canUseShortcuts(c: Context) = try { la(c).hasShortcutHostPermission() } catch (e: Exception) { false }

    private fun query(c: Context, pkg: String, ids: List<String>? = null): List<ShortcutInfo> = try {
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        if (ids != null) q.setShortcutIds(ids)
        la(c).getShortcuts(q, Process.myUserHandle()).orEmpty().filter { it.isEnabled }
    } catch (e: Exception) {
        emptyList() // not the default launcher, or the app is gone
    }

    private fun shortcutEntry(c: Context, s: ShortcutInfo) = AppEntry(
        "$SHORTCUT${s.`package`}|${s.id}",
        "${appLabel(c, s.`package`)}: ${s.shortLabel ?: s.longLabel ?: s.id}",
        try { la(c).getShortcutIconDrawable(s, c.resources.displayMetrics.densityDpi) } catch (e: Exception) { null }
            ?: try { c.packageManager.getApplicationIcon(s.`package`) } catch (e: Exception) { null },
    )

    // ---- activities ----

    private fun activities(c: Context, pkg: String): List<AppEntry> = try {
        val pm = c.packageManager
        pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            .filter { it.exported && it.enabled && it.permission == null }
            .map {
                val short = it.name.substringAfterLast('.')
                val label = it.loadLabel(pm).toString()
                AppEntry("$ACTIVITY$pkg/${it.name}", if (label == short) label else "$label ($short)", it.loadIcon(pm))
            }
            .sortedBy { it.label.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    // ---- shared ----

    fun entry(c: Context, target: String): AppEntry? = when {
        target.startsWith(SHORTCUT) -> {
            val (pkg, id) = target.removePrefix(SHORTCUT).split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            query(c, pkg, listOf(id)).firstOrNull()?.let { shortcutEntry(c, it) }
        }
        target.startsWith(ACTIVITY) -> {
            val cn = ComponentName.unflattenFromString(target.removePrefix(ACTIVITY))
            try {
                val info = c.packageManager.getActivityInfo(cn!!, 0)
                AppEntry(target, info.loadLabel(c.packageManager).toString(), info.loadIcon(c.packageManager))
            } catch (e: Exception) { null }
        }
        else -> null
    }

    fun launch(a: Activity, target: String): Boolean = try {
        if (target.startsWith(SHORTCUT)) {
            val (pkg, id) = target.removePrefix(SHORTCUT).split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            la(a).startShortcut(pkg, id, null, null, Process.myUserHandle()); true
        } else {
            val cn = ComponentName.unflattenFromString(target.removePrefix(ACTIVITY))
            Apps.start(a, Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: Exception) {
        Toast.makeText(a, "Can't open that", Toast.LENGTH_SHORT).show(); false
    }

    // ---- extras shown in the app drawer ----

    private const val DRAWER = "drawer_extras"

    fun drawerExtras(c: Context): List<String> = Prefs.get(c, DRAWER)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    fun addToDrawer(c: Context, target: String) =
        Prefs.set(c, DRAWER, (drawerExtras(c) - target + target).joinToString("\n"))

    fun removeFromDrawer(c: Context, target: String) =
        Prefs.set(c, DRAWER, (drawerExtras(c) - target).joinToString("\n"))

    /** "Shortcuts…" / "Activities…": pick one, then choose where it goes. */
    fun showShortcuts(a: Activity, appTarget: String, label: String) {
        if (!canUseShortcuts(a)) {
            Toast.makeText(a, "Set Dumb Launcher as the default launcher to use app shortcuts", Toast.LENGTH_LONG).show()
            return
        }
        val list = query(a, packageOf(appTarget)).sortedBy { it.rank }.map { shortcutEntry(a, it) }
        if (list.isEmpty()) { Toast.makeText(a, "$label has no shortcuts", Toast.LENGTH_SHORT).show(); return }
        Ui.choose(a, "$label shortcuts", list.map { e -> e.label.substringAfter(": ") to { place(a, e) } })
    }

    fun showActivities(a: Activity, appTarget: String, label: String) {
        val list = activities(a, packageOf(appTarget))
        if (list.isEmpty()) { Toast.makeText(a, "$label has no openable activities", Toast.LENGTH_SHORT).show(); return }
        Ui.choose(a, "$label activities", list.map { e -> e.label to { place(a, e) } })
    }

    /** Where should this shortcut / activity go? */
    fun place(a: Activity, e: AppEntry) {
        val done = { where: String -> Toast.makeText(a, "Added to $where", Toast.LENGTH_SHORT).show() }
        Ui.choose(a, "Add \"${e.label}\" to", listOf<Pair<String, () -> Any?>>(
            "App drawer" to { addToDrawer(a, e.target); done("app drawer"); (a as? AppsActivity)?.reload() },
        ) + (if (Metro.on(a)) emptyList() else Prefs.DPAD.map { (pref, name) ->   // arrows select tiles in Metro
            "Home $name key" to { Prefs.set(a, pref, e.target); done("$name key") }
        }) + listOf(
            "Home number key…" to {
                Ui.choose(a, "Number key", (0..9).map { n ->
                    "$n  ${Apps.entry(a, Prefs.get(a, Prefs.num(n)))?.label ?: "not set"}" to
                        { Prefs.set(a, Prefs.num(n), e.target); done("key $n") }
                })
            },
        ))
    }
}
