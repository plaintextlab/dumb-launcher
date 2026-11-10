package com.plaintextlab.dumblauncher

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast

/**
 * Handles "Add to Home screen" from apps (e.g. a Chrome page, a contact, a WhatsApp chat):
 * the shortcut is accepted and put in the app drawer, where it can also be moved to a key.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        val info = req?.shortcutInfo
        if (req != null && info != null && req.isValid && req.accept()) {
            Shortcuts.addToDrawer(this, "${Shortcuts.SHORTCUT}${info.`package`}|${info.id}")
            Toast.makeText(this, "\"${info.shortLabel}\" added to the app drawer", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
