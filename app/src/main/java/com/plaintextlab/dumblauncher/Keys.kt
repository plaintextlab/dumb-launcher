package com.plaintextlab.dumblauncher

import android.view.KeyEvent

/**
 * All physical-key meaning lives here, so remapping for a new phone is a one-file change.
 * Codes for the ALT MIVE Style Folder 2 specials (contacts, mail, camera, star) are not
 * known yet: run the Key tester and add them below.
 */
object Keys {
    /** "Options" key. Long-press of OK also opens options on every screen. */
    private val OPTIONS = setOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BOOKMARK)

    /** The 지움|취소 (delete/cancel) key. */
    private val DELETE = setOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_CLEAR, KeyEvent.KEYCODE_FORWARD_DEL)

    private val OK = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

    fun isOptions(code: Int) = code in OPTIONS
    fun isDelete(code: Int) = code in DELETE
    fun isOk(code: Int) = code in OK
    fun isCall(code: Int) = code == KeyEvent.KEYCODE_CALL

    /** 0-9 → the digit, otherwise null. */
    fun digit(code: Int): Int? = when (code) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    /** D-pad direction → preference key for the home-screen shortcut. */
    fun dpadPref(code: Int): String? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> Prefs.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Prefs.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Prefs.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Prefs.RIGHT
        else -> null
    }
}
