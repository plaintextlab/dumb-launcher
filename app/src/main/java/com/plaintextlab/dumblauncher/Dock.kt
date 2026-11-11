package com.plaintextlab.dumblauncher

import android.content.Context

/** How often each app/shortcut was opened from this launcher. */
object Usage {
    private fun sp(c: Context) = c.getSharedPreferences("usage", Context.MODE_PRIVATE)

    fun record(c: Context, target: String) {
        if (target == Prefs.SPECIAL_APPS) return
        val p = sp(c)
        val e = p.edit().putInt(target, p.getInt(target, 0) + 1)
        // Halve everything now and then, so apps you stopped using drift down.
        if (p.all.values.sumOf { (it as? Int) ?: 0 } > 400) {
            p.all.forEach { (k, v) -> if (k != target) e.putInt(k, ((v as? Int) ?: 0) / 2) }
        }
        e.apply()
    }

    fun ranked(c: Context): List<String> =
        sp(c).all.entries.filter { ((it.value as? Int) ?: 0) > 0 }
            .sortedByDescending { it.value as Int }.map { it.key }

    fun clear(c: Context) = sp(c).edit().clear().apply()
}

/**
 * The Home dock shows what keys 1-5 open. A key the user set (Number key shortcuts) shows
 * that app; an unset key is filled automatically with the most-used apps, falling back to
 * the phone's default apps until there is enough history.
 */
object Dock {
    val KEYS = 1..5

    data class Slot(val key: Int, val entry: AppEntry?, val auto: Boolean)

    /** Phone, Messages, Contacts, Camera… used until there is usage history. */
    private var defaultApps: List<String>? = null
    private fun defaults(c: Context) = defaultApps ?: Apps.defaultSlots(c).filterNotNull().also { defaultApps = it }

    fun enabled(c: Context) = Prefs.getBool(c, Prefs.SHOW_DOCK, true)

    fun slots(c: Context): List<Slot> {
        val pinned = KEYS.associateWith { Prefs.get(c, Prefs.num(it)) }
        val taken = pinned.values.filterNotNull().toMutableSet()
        val hidden = Prefs.hidden(c)
        val candidates = (Usage.ranked(c) + defaults(c))
            .filter { it != Prefs.SPECIAL_APPS && it !in hidden }
            .distinct()
            .iterator()
        return KEYS.map { k ->
            val own = pinned[k]
            if (own != null) return@map Slot(k, Apps.entry(c, own), auto = false)
            var pick: AppEntry? = null
            while (pick == null && candidates.hasNext()) {
                val t = candidates.next()
                if (t !in taken) Apps.entry(c, t)?.let { pick = it; taken += t }
            }
            Slot(k, pick, auto = true)
        }
    }

    fun target(c: Context, key: Int) = slots(c).firstOrNull { it.key == key }?.entry?.target
}
