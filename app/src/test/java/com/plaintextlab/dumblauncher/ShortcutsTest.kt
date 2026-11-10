package com.plaintextlab.dumblauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutsTest {
    @Test fun packageOfEveryTargetKind() {
        assertEquals("com.whatsapp", Shortcuts.packageOf("com.whatsapp/com.whatsapp.Main"))
        assertEquals("com.android.chrome", Shortcuts.packageOf("shortcut:com.android.chrome|new_tab"))
        assertEquals("com.android.settings", Shortcuts.packageOf("activity:com.android.settings/.wifi.WifiSettings"))
    }

    @Test fun recognisesExtras() {
        assertTrue(Shortcuts.isExtra("shortcut:a|b"))
        assertTrue(Shortcuts.isExtra("activity:a/b"))
        assertFalse(Shortcuts.isExtra("a/b"))
        assertFalse(Shortcuts.isExtra(Prefs.SPECIAL_APPS))
    }
}
