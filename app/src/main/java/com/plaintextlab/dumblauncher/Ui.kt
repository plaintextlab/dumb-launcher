package com.plaintextlab.dumblauncher

import android.app.Activity
import android.app.AlertDialog

object Ui {
    /** A D-pad navigable list dialog. */
    fun choose(a: Activity, title: String, items: List<Pair<String, () -> Any?>>) {
        AlertDialog.Builder(a)
            .setTitle(title)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    /** Open the app list in "pick" mode; the chosen app is saved under [prefKey]. */
    fun pick(a: Activity, prefKey: String, title: String) {
        a.startActivity(AppsActivity.pickIntent(a, prefKey, title))
    }
}
