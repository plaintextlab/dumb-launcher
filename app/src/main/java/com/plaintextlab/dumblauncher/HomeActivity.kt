package com.plaintextlab.dumblauncher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.Settings
import android.view.KeyEvent
import android.widget.TextView

/** Home screen: clock, missed calls / unread messages, and key shortcuts. */
class HomeActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        status = findViewById(R.id.status)
        findViewById<TextView>(R.id.hint).text = "● Menu    0-9 Dial    ☆ Options"
        Prefs.ensureDefaults(this)
        val needed = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) =
        refreshStatus()

    private fun refreshStatus() {
        val lines = mutableListOf<String>()
        val missed = count(Manifest.permission.READ_CALL_LOG, CallLog.Calls.CONTENT_URI,
            "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW}=1")
        if (missed > 0) lines += "$missed missed call" + if (missed > 1) "s" else ""
        val unread = count(Manifest.permission.READ_SMS, Uri.parse("content://sms/inbox"), "read=0")
        if (unread > 0) lines += "$unread new message" + if (unread > 1) "s" else ""
        status.text = lines.joinToString("\n")
    }

    private fun count(perm: String, uri: Uri, where: String): Int {
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) return 0
        return try {
            contentResolver.query(uri, arrayOf("_id"), where, null, null)?.use { it.count } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        if (Keys.isOk(code)) {
            event.startTracking(); return true
        }
        if (Keys.handleAppKey(this, code, event)) return true
        if (event.repeatCount > 0) return true
        Keys.digit(code)?.let { dial(it.toString()); return true }
        if (code == KeyEvent.KEYCODE_POUND) { dial("#"); return true }
        Keys.dpadPref(code)?.let { pref ->
            if (!Apps.launch(this, Prefs.get(this, pref))) {
                val name = Prefs.DPAD.first { it.first == pref }.second
                Ui.pick(this, pref, "Pick app for $name")
            }
            return true
        }
        if (Keys.isOptions(code)) { options(); return true }
        if (Keys.isCall(code)) {
            Apps.start(this, Intent(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE)); return true
        }
        if (code == KeyEvent.KEYCODE_BACK) return true // nothing to go back to
        return super.onKeyDown(code, event)
    }

    override fun onKeyLongPress(code: Int, event: KeyEvent): Boolean {
        when {
            Keys.isOk(code) -> options()
            else -> return super.onKeyLongPress(code, event)
        }
        return true
    }

    override fun onKeyUp(code: Int, event: KeyEvent): Boolean {
        val shortPress = event.isTracking && !event.isCanceled
        when {
            Keys.isOk(code) -> { if (shortPress) startActivity(Intent(this, MenuActivity::class.java)) }
            code == KeyEvent.KEYCODE_BACK -> {}
            else -> return super.onKeyUp(code, event)
        }
        return true
    }

    private fun dial(start: String) {
        Apps.start(this, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(start))))
    }

    private fun options() {
        val items = Prefs.DPAD.map { (pref, name) ->
            "Set $name shortcut" to { Ui.pick(this, pref, "Pick app for $name") }
        } + listOf(
            "Highlight colour" to { chooseColour() },
            "Icon pack" to { Icons.choosePack(this) {} },
            "Icon style (no pack)" to { chooseIconStyle() },
            "Key tester" to { startActivity(Intent(this, KeyTesterActivity::class.java)) },
            "Choose default launcher" to { Apps.start(this, Intent(Settings.ACTION_HOME_SETTINGS)) },
            "Phone settings" to { Apps.start(this, Intent(Settings.ACTION_SETTINGS)) },
        )
        Ui.choose(this, "Options", items)
    }

    private fun chooseColour() = Ui.choose(this, "Highlight colour",
        Ui.COLOURS.map { (name, colour) -> name to { Prefs.setInt(this, Prefs.HIGHLIGHT, colour) } })

    private fun chooseIconStyle() = Ui.choose(this, "Icon style",
        Icons.STYLES.mapIndexed { i, name -> name to { Prefs.setInt(this, Prefs.ICON_STYLE, i); Icons.clear() } })
}
