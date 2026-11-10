package com.plaintextlab.dumblauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Home screen: clock, missed calls / unread messages, and key shortcuts. */
class HomeActivity : BaseActivity() {

    override val isHome = true

    private lateinit var notifs: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        notifs = findViewById(R.id.notifs)
        findViewById<TextView>(R.id.hint).text = "● Menu    0-9 Dial    hold ● Settings"
        Prefs.ensureDefaults(this)
        // The buttons also work by touch.
        findViewById<View>(R.id.musicPrev).setOnClickListener { Music.previous(this); refreshMusicSoon() }
        findViewById<View>(R.id.musicPlay).setOnClickListener { Music.playPause(this); refreshMusicSoon() }
        findViewById<View>(R.id.musicNext).setOnClickListener { Music.next(this); refreshMusicSoon() }
        val needed = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshShortcuts()
        Anim.homeArrive(findViewById(R.id.clockBox), notifs)
    }

    override fun onStart() {
        super.onStart()
        NotifListener.onChange = { runOnUiThread { refreshStatus() } }
        ticker.post(tick)
        audio.registerAudioDeviceCallback(audioDevices, null)
    }

    override fun onStop() {
        super.onStop()
        NotifListener.onChange = null
        ticker.removeCallbacks(tick)
        audio.unregisterAudioDeviceCallback(audioDevices)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) =
        refreshStatus()

    private fun refreshStatus() {
        notifs.removeAllViews()
        if (!Prefs.getBool(this, Prefs.SHOW_COUNTS, true)) return
        val missed = count(Manifest.permission.READ_CALL_LOG, CallLog.Calls.CONTENT_URI,
            "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW}=1")
        val unread = count(Manifest.permission.READ_SMS, Uri.parse("content://sms/inbox"), "read=0")
        if (headphonesConnected()) addChip(getDrawable(R.drawable.ic_headphones), 0)
        if (missed > 0) addChip(getDrawable(R.drawable.ic_call), missed)
        if (unread > 0) addChip(getDrawable(R.drawable.ic_message), unread)

        // Other apps (only if notification access is allowed). Room for 5 chips in total.
        val others = NotifListener.counts
        val room = 5 - notifs.childCount  // about 5 chips fit across a 4.3" screen
        others.take(if (others.size > room) room - 1 else room).forEach { (pkg, n) -> addChip(appIcon(pkg), n) }
        val rest = others.drop(if (others.size > room) room - 1 else room).sumOf { it.second }
        if (rest > 0) addChip(null, rest, prefix = "+")
    }

    /** One notification chip: icon and count. */
    private fun addChip(icon: android.graphics.drawable.Drawable?, n: Int, prefix: String = "") {
        val dp = resources.displayMetrics.density
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((5 * dp).toInt(), 0, (5 * dp).toInt(), 0)
        }
        if (icon != null) chip.addView(ImageView(this).apply { setImageDrawable(icon) },
            LinearLayout.LayoutParams((26 * dp).toInt(), (26 * dp).toInt()))
        if (n > 0) chip.addView(TextView(this).apply {
            text = "$prefix$n"; textSize = 20f; setTextColor(getColor(R.color.fg))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((4 * dp).toInt(), 0, 0, 0)
        })
        notifs.addView(chip)
    }

    private val audio by lazy { getSystemService(android.media.AudioManager::class.java) }

    /** Wired, USB-C or Bluetooth earphones / headset. */
    private fun headphonesConnected(): Boolean {
        val kinds = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        )
        return audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in kinds }
    }

    /** Plugging earphones in or out updates the icon at once. */
    private val audioDevices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refreshStatus()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refreshStatus()
    }

    private fun appIcon(pkg: String) =
        packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToString()
            ?.let { Apps.entry(this, it) }?.let { Icons.get(this, it) }
            ?: try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    /** Fill the D-pad cross with each arrow's app icon (or a faint arrow if unset). */
    private fun refreshShortcuts() {
        val dpad = findViewById<View>(R.id.dpad)
        dpad.visibility = if (Prefs.getBool(this, Prefs.SHOW_DPAD, true)) View.VISIBLE else View.GONE
        val arms = listOf(R.id.sc_up to "▲", R.id.sc_down to "▼", R.id.sc_left to "◀", R.id.sc_right to "▶")
        for ((i, arm) in arms.withIndex()) {
            val v = findViewById<View>(arm.first)
            val entry = Apps.entry(this, Prefs.get(this, Prefs.DPAD[i].first))
            val icon = entry?.let { Icons.get(this, it) }
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(icon)
            v.findViewById<TextView>(R.id.label).text = if (icon == null) arm.second else ""
            v.contentDescription = entry?.label
        }
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
        if (Keys.isOk(code) || Keys.dpadPref(code) != null) {
            event.startTracking(); return true
        }
        if (Keys.handleAppKey(this, code, event)) return true
        if (event.repeatCount > 0) return true
        Keys.digit(code)?.let { n ->
            if (!Apps.launch(this, Prefs.get(this, Prefs.num(n)))) Ui.pick(this, Prefs.num(n), "Pick app for key $n")
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
        if (musicView.visibility == View.VISIBLE) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { Music.previous(this); refreshMusicSoon(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { Music.next(this); refreshMusicSoon(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { Music.playPause(this); refreshMusicSoon(); return true }
            }
        }
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
            Keys.dpadPref(code) != null -> { if (shortPress) openShortcut(Keys.dpadPref(code)!!) }
            code == KeyEvent.KEYCODE_BACK -> {}
            else -> return super.onKeyUp(code, event)
        }
        return true
    }

    private fun openShortcut(pref: String) {
        if (!Apps.launch(this, Prefs.get(this, pref))) {
            val name = Prefs.DPAD.first { it.first == pref }.second
            Ui.pick(this, pref, "Pick app for $name")
        }
    }

    // ---- Music widget: refreshed every second while Home is on screen ----

    private val musicView by lazy { findViewById<View>(R.id.music) }
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refreshMusic(); ticker.postDelayed(this, 1000) }
    }

    private fun refreshMusicSoon() = ticker.postDelayed({ refreshMusic() }, 350)

    private fun refreshMusic() {
        val now = if (Prefs.getBool(this, Prefs.SHOW_MUSIC, true)) Music.now(this) else null
        if (now == null) { musicView.visibility = View.GONE; return }
        musicView.visibility = View.VISIBLE
        val art = findViewById<ImageView>(R.id.musicArt)
        if (now.art != null) art.setImageBitmap(now.art) else art.setImageResource(R.drawable.ic_music)
        findViewById<TextView>(R.id.musicTitle).text = now.title
        findViewById<TextView>(R.id.musicArtist).apply {
            text = now.artist; visibility = if (now.artist.isEmpty()) View.GONE else View.VISIBLE
        }
        findViewById<ImageView>(R.id.musicPlay).setImageResource(if (now.playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun options() = startActivity(Intent(this, SettingsActivity::class.java))
}
