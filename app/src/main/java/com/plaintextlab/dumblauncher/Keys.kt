package com.plaintextlab.dumblauncher

import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import android.view.KeyEvent

/**
 * All physical-key meaning lives here, so remapping for a new phone is a one-file change.
 * Codes below were measured with the Key tester on an ALT MIVE Style Folder 2 (AT-M140).
 * Home, Recents and the red End key never reach apps.
 */
object Keys {
    /** ☆ key: sends F3 (133), and on some presses the vendor code 1000. Long-press OK works too. */
    private const val STAR_VENDOR = 1000
    private val OPTIONS = setOf(KeyEvent.KEYCODE_F3, STAR_VENDOR, KeyEvent.KEYCODE_MENU)

    /** 👤 key = F1, ✉ key = F2, 📷 key = CAMERA. */
    private const val CONTACTS = KeyEvent.KEYCODE_F1
    private const val MESSAGES = KeyEvent.KEYCODE_F2

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

    /** Handles the 👤 ✉ 📷 keys on any screen. Returns true if the key was one of them. */
    fun handleAppKey(a: Activity, code: Int, event: KeyEvent): Boolean {
        val intent = when (code) {
            CONTACTS -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
            MESSAGES -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
            KeyEvent.KEYCODE_CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            else -> return false
        }
        if (event.repeatCount == 0) Apps.start(a, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
