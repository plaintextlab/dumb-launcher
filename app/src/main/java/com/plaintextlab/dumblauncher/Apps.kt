package com.plaintextlab.dumblauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast

data class AppEntry(val target: String, val label: String, val icon: Drawable?) {
    /** Each word of the label as T9 digits, plus the whole label without spaces. */
    val t9: List<String> by lazy {
        val words = label.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        (words + words.joinToString("")).map { T9.encode(it) }
    }

    fun matches(query: String) = query.isEmpty() || t9.any { it.startsWith(query) }
}

object T9 {
    private val map = HashMap<Char, Char>().apply {
        val groups = listOf("0" to " ", "1" to "", "2" to "abc", "3" to "def", "4" to "ghi",
            "5" to "jkl", "6" to "mno", "7" to "pqrs", "8" to "tuv", "9" to "wxyz")
        for ((d, letters) in groups) { put(d[0], d[0]); letters.forEach { put(it, d[0]) } }
    }

    fun encode(s: String) = buildString { for (ch in s) map[ch]?.let { append(it) } }
}

object Apps {
    fun all(c: Context): List<AppEntry> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val home = ComponentName(c, HomeActivity::class.java)
        val hidden = Prefs.hidden(c)
        return pm.queryIntentActivities(intent, 0)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) to it }
            .filter { it.first != home && it.first.flattenToString() !in hidden }
            .map { (cn, ri) -> AppEntry(cn.flattenToString(), ri.loadLabel(pm).toString(), null) } // icons load later
            .plus(Shortcuts.drawerExtras(c).filter { it !in hidden }.mapNotNull { Shortcuts.entry(c, it) })
            .map { renamed(c, it) }
            .sortedBy { it.label.lowercase() }
    }

    /** Apply the user's own name for this app, if they gave it one. */
    fun renamed(c: Context, e: AppEntry): AppEntry =
        Prefs.customLabel(c, e.target)?.let { e.copy(label = it) } ?: e

    fun entry(c: Context, target: String?): AppEntry? = rawEntry(c, target)?.let { renamed(c, it) }

    /** The original label, ignoring any rename. */
    fun rawEntry(c: Context, target: String?): AppEntry? {
        if (target == null) return null
        if (target == Prefs.SPECIAL_APPS) {
            return AppEntry(target, "All apps", c.getDrawable(R.drawable.ic_launcher))
        }
        if (Shortcuts.isExtra(target)) return Shortcuts.entry(c, target)
        val cn = ComponentName.unflattenFromString(target) ?: return null
        return try {
            val info = c.packageManager.getActivityInfo(cn, 0)
            AppEntry(target, info.loadLabel(c.packageManager).toString(), info.loadIcon(c.packageManager))
        } catch (e: Exception) {
            null // app was uninstalled
        }
    }

    /** The app's own icon, read on demand (background thread). */
    fun rawIcon(c: Context, target: String): Drawable? = try {
        if (Shortcuts.isExtra(target)) Shortcuts.entry(c, target)?.icon
        else c.packageManager.getActivityIcon(ComponentName.unflattenFromString(target)!!)
    } catch (e: Exception) {
        null
    }

    fun launch(a: Activity, target: String?): Boolean {
        if (target == null) return false
        if (target == Prefs.SPECIAL_APPS) {
            a.startActivity(Intent(a, AppsActivity::class.java)); return true
        }
        val ok = if (Shortcuts.isExtra(target)) Shortcuts.launch(a, target) else {
            val cn = ComponentName.unflattenFromString(target) ?: return false
            start(a, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
        if (ok) Usage.record(a, target)
        return ok
    }

    fun start(a: Activity, i: Intent): Boolean = try {
        a.startActivity(i); true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(a, "No app for that", Toast.LENGTH_SHORT).show(); false
    } catch (e: SecurityException) {
        Toast.makeText(a, "Not allowed", Toast.LENGTH_SHORT).show(); false
    }

    /** Default apps for the dock: Phone, Messages, Contacts, Camera, Gallery, Browser, Clock, Settings. */
    fun defaultSlots(c: Context): List<String?> {
        val sel = { cat: String -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat) }
        return listOf(
            Intent(Intent.ACTION_DIAL),
            sel(Intent.CATEGORY_APP_MESSAGING),
            sel(Intent.CATEGORY_APP_CONTACTS),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            sel(Intent.CATEGORY_APP_GALLERY),
            sel(Intent.CATEGORY_APP_BROWSER),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Settings.ACTION_SETTINGS),
        ).map { resolve(c, it) }
    }

    /** Resolve an intent to the launcher component of the app that handles it. */
    private fun resolve(c: Context, i: Intent): String? {
        val pm = c.packageManager
        val pkg = pm.resolveActivity(i, 0)?.activityInfo?.packageName ?: return null
        if (pkg == "android") return null // chooser: several apps, no default
        return pm.getLaunchIntentForPackage(pkg)?.component?.flattenToString()
    }
}
