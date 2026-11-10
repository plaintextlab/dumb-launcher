package com.plaintextlab.dumblauncher

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.TextView

/**
 * Shows the Android keycode of every key press. Keys that never appear here
 * (likely Home and Recents) are taken by the system before any app sees them.
 */
class KeyTesterActivity : Activity() {

    private lateinit var last: TextView
    private lateinit var log: TextView
    private val lines = ArrayDeque<String>()
    private var lastBack = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keytester)
        last = findViewById(R.id.last)
        log = findViewById(R.id.log)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val name = KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_")
        val action = when (e.action) { KeyEvent.ACTION_DOWN -> "down"; KeyEvent.ACTION_UP -> "up"; else -> "multi" }
        val long = if (e.isLongPress) " LONG" else ""
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) last.text = "$name  (${e.keyCode})"
        if (e.repeatCount == 0 || e.isLongPress) {
            lines.addFirst("%-5s %-16s code=%-3d scan=%-3d%s".format(action, name, e.keyCode, e.scanCode, long))
            while (lines.size > 60) lines.removeLast()
            log.text = lines.joinToString("\n")
        }
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) {
            val now = SystemClock.uptimeMillis()
            if (now - lastBack < 1000) finish()
            lastBack = now
        }
        return true
    }
}
