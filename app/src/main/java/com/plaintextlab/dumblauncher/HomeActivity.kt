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
        findViewById<TextView>(R.id.hint).text = "● Apps    1-9 Shortcuts    0 Apps    hold ● Settings"
        // The buttons also work by touch.
        listOf(R.id.musicPrev, R.id.musicPlay, R.id.musicNext).forEach {
            findViewById<View>(it).apply { isFocusable = false; isFocusableInTouchMode = false }
        }
        findViewById<View>(R.id.musicPrev).setOnClickListener { Music.previous(this); refreshMusicSoon() }
        findViewById<View>(R.id.musicPlay).setOnClickListener { Music.playPause(this); refreshMusicSoon() }
        findViewById<View>(R.id.musicNext).setOnClickListener { Music.next(this); refreshMusicSoon() }
        findViewById<View>(R.id.music).setOnClickListener { Music.open(this) }
        // Touch: tap the clock for the Clock app, swipe up anywhere for All apps.
        findViewById<View>(R.id.clockBox).setOnClickListener {
            if (!Apps.launch(this, Apps.clock(this))) {
                android.widget.Toast.makeText(this, "No clock app found", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // Location and photos only for the Weather and Photos live tiles, and only if they are on.
        val tiles = if (!metro) emptyList() else listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION.takeIf { Weather.enabled(this) },
            Photos.permission.takeIf { Photos.enabled(this) },
            People.permission.takeIf { StartTiles.load(this).any { it.id == StartTiles.PEOPLE } })
        val needed = (listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS) + tiles)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        AppList.warmUp(this)
        showMode()
        if (metro) { buildStart(animate = true); return }
        findViewById<View>(R.id.clockBox).visibility =
            if (Prefs.getBool(this, Prefs.SHOW_CLOCK, true)) View.VISIBLE else View.GONE
        refreshStatus()
        refreshShortcuts()
        refreshDock()
        Anim.homeArrive(findViewById(R.id.clockBox), notifs)
    }

    // ---- Metro Start screen (when the icon style is Metro) ----

    private val metro get() = Icons.style(this) == Icons.METRO
    private val start by lazy { StartScreen(this, findViewById(R.id.startColumn)) { onTile(it) } }

    /** Show either the Start screen or the classic Home. */
    private fun showMode() {
        val m = metro
        val classic = if (m) View.GONE else View.VISIBLE
        listOf(R.id.clockBox, R.id.notifs, R.id.spacer, R.id.dpad, R.id.dock).forEach { findViewById<View>(it).visibility = classic }
        if (m) musicView.visibility = View.GONE
        findViewById<View>(R.id.startScroll).visibility = if (m) View.VISIBLE else View.GONE
        // Classic Home keeps focus off its views (keys are shortcuts); Start lets arrows select tiles.
        (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.view.ViewGroup).descendantFocusability =
            if (m) android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS else android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun buildStart(animate: Boolean) {
        val counts = HashMap<String, Int>()
        if (Prefs.getBool(this, Prefs.SHOW_COUNTS, true)) {
            NotifListener.counts.forEach { (pkg, n) -> counts[pkg] = n }
            val missed = count(Manifest.permission.READ_CALL_LOG, CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW}=1")
            val unread = count(Manifest.permission.READ_SMS, Uri.parse("content://sms/inbox"), "read=0")
            packageOf(Intent(Intent.ACTION_DIAL))?.let { if (missed > 0) counts[it] = missed }
            android.provider.Telephony.Sms.getDefaultSmsPackage(this)?.let { if (unread > 0) counts[it] = unread }
        }
        start.build(counts, animate)
        start.restoreFocus()
        start.music(shownMusic)
        if (animate) loadLiveTiles()
    }

    /** Weather (cached at once, refreshed online when old) and recent photos, off the UI thread. */
    private fun loadLiveTiles(forceWeather: Boolean = false) {
        val app = applicationContext
        if (Weather.enabled(this)) {
            start.weather(Weather.cached(this))
            Thread {
                val w = Weather.refresh(app, forceWeather)
                runOnUiThread { start.weather(w) }
            }.start()
        }
        if (Photos.enabled(this)) {
            val px = (resources.displayMetrics.widthPixels * 0.66f).toInt()
            Thread {
                val thumbs = Photos.recent(app, 8).mapNotNull { Photos.thumb(app, it, px / 2) }
                runOnUiThread { start.photos(thumbs) }
            }.start()
        }
        if (People.hasPermission(this)) Thread {
            val faces = People.faces(app, 27)
            runOnUiThread { start.people(faces) }
        }.start()
    }

    private fun packageOf(i: Intent) = packageManager.resolveActivity(i, 0)?.activityInfo?.packageName

    /** A tile was tapped (or its key pressed): swing the tiles out, then open it. */
    private fun onTile(id: String) {
        val toast = { s: String -> android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show() }
        // A live tile the user set to open a chosen app.
        if (id in StartTiles.LIVE) Prefs.get(this, StartTiles.openKey(id))?.let { t ->
            leave { if (!Apps.launch(this, t)) toast("Can't open that app") }; return
        }
        when (id) {
            StartScreen.ALL_APPS -> openDrawer()
            StartTiles.PEOPLE -> leave {
                Apps.start(this, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            StartTiles.CLOCK -> leave { if (!Apps.launch(this, Apps.clock(this))) toast("No clock app found") }
            StartTiles.CALENDAR -> leave { if (!Apps.launch(this, Apps.calendar(this))) toast("No calendar app found") }
            StartTiles.WEATHER -> { toast("Updating weather…"); loadLiveTiles(forceWeather = true) }
            StartTiles.PHOTOS -> leave {
                Apps.start(this, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            StartTiles.MUSIC -> leave {
                // The playing app if there is one, else the phone's music app.
                if (shownMusic == null || !Music.open(this)) Apps.start(this,
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            else -> id.removePrefix(StartTiles.APP).let { t -> leave { if (!Apps.launch(this, t)) toast("Can't open that app") } }
        }
    }

    /** In the Metro style, opening something first swings the tiles away. */
    private fun leave(action: () -> Unit) {
        if (metro) Turnstile.out(start.rows) { action() } else action()
    }

    override fun onStart() {
        super.onStart()
        NotifListener.onChange = { runOnUiThread { if (metro) buildStart(animate = false) else refreshStatus() } }
        ticker.post(tick)
        audio.registerAudioDeviceCallback(audioDevices, null)
    }

    override fun onStop() {
        super.onStop()
        NotifListener.onChange = null
        ticker.removeCallbacks(tick)
        audio.unregisterAudioDeviceCallback(audioDevices)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (metro) { buildStart(animate = false); loadLiveTiles() } else refreshStatus()
    }

    private fun refreshStatus() {
        notifs.removeAllViews()
        if (!Prefs.getBool(this, Prefs.SHOW_COUNTS, true)) return
        val missed = count(Manifest.permission.READ_CALL_LOG, CallLog.Calls.CONTENT_URI,
            "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW}=1")
        val unread = count(Manifest.permission.READ_SMS, Uri.parse("content://sms/inbox"), "read=0")
        if (headphonesConnected()) addChip(getDrawable(R.drawable.ic_headphones), 0)
        if (missed > 0) addChip(getDrawable(R.drawable.ic_call), missed) { openCallLog() }
        if (unread > 0) addChip(getDrawable(R.drawable.ic_message), unread) {
            Apps.start(this, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        // Other apps (only if notification access is allowed). Room for 5 chips in total.
        val others = NotifListener.counts
        val room = 5 - notifs.childCount  // about 5 chips fit across a 4.3" screen
        others.take(if (others.size > room) room - 1 else room).forEach { (pkg, n) ->
            addChip(appIcon(pkg), n) { packageManager.getLaunchIntentForPackage(pkg)?.let { Apps.start(this, it) } }
        }
        val rest = others.drop(if (others.size > room) room - 1 else room).sumOf { it.second }
        if (rest > 0) addChip(null, rest, prefix = "+")
    }

    /** One notification chip: icon and count on a soft rounded pill. */
    private fun addChip(icon: android.graphics.drawable.Drawable?, n: Int, prefix: String = "",
                        onTap: (() -> Unit)? = null) {
        val dp = resources.displayMetrics.density
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_chip)
            setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
        }
        if (icon != null) chip.addView(ImageView(this).apply { setImageDrawable(icon) },
            LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
        if (n > 0) chip.addView(TextView(this).apply {
            text = "$prefix$n"; textSize = 15f; setTextColor(getColor(R.color.fg))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(if (icon != null) (5 * dp).toInt() else 0, 0, 0, 0)
        })
        chip.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { setMargins((2 * dp).toInt(), 0, (2 * dp).toInt(), 0) }
        onTap?.let { tap -> chip.setOnClickListener { tap() } }
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

    /** Bottom row: the apps on keys 1-5, each with its number under it. */
    private fun refreshDock() {
        val dock = findViewById<LinearLayout>(R.id.dock)
        dock.removeAllViews()
        if (!Dock.enabled(this)) { dock.visibility = View.GONE; return }
        dock.visibility = View.VISIBLE
        for (slot in Dock.slots(this)) {
            val v = layoutInflater.inflate(R.layout.item_dock, dock, false)
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(slot.entry?.let { Icons.get(this, it) })
            v.findViewById<TextView>(R.id.label).text = slot.key.toString()
            v.contentDescription = slot.entry?.label
            v.setOnClickListener { openNumber(slot.key) }
            dock.addView(v)
        }
    }

    /** The D-pad cross hides while the music widget is showing, to leave room for the dock. */
    private fun updateDpadVisibility() {
        val show = Prefs.getBool(this, Prefs.SHOW_DPAD, true) && musicView.visibility != View.VISIBLE
        val dpad = findViewById<View>(R.id.dpad)
        val want = if (show) View.VISIBLE else View.GONE
        if (dpad.visibility != want) dpad.visibility = want
    }

    /** Fill the D-pad cross with each arrow's app icon (or a faint arrow if unset). */
    private fun refreshShortcuts() {
        updateDpadVisibility()
        val arms = listOf(R.id.sc_up to "▲", R.id.sc_down to "▼", R.id.sc_left to "◀", R.id.sc_right to "▶")
        for ((i, arm) in arms.withIndex()) {
            val v = findViewById<View>(arm.first)
            val entry = Apps.entry(this, Prefs.get(this, Prefs.DPAD[i].first))
            val icon = entry?.let { Icons.get(this, it) }
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(icon)
            v.findViewById<TextView>(R.id.label).text = if (icon == null) arm.second else ""
            v.contentDescription = entry?.label
            v.setOnClickListener { openShortcut(Prefs.DPAD[i].first) }
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
        // Metro: the arrow keys move the selection between tiles (left to the views), not shortcuts.
        if (Keys.isOk(code) || (!metro && Keys.dpadPref(code) != null)) {
            event.startTracking(); return true
        }
        if (Keys.handleAppKey(this, code, event)) return true
        if (event.repeatCount > 0) return true
        Keys.digit(code)?.let { n ->
            if (n == 0) openDrawer() else openNumber(n)   // 0 = All apps
            return true
        }
        if (Keys.isCall(code)) {
            openCallLog(); return true
        }
        if (code == KeyEvent.KEYCODE_BACK) { event.startTracking(); return true } // Back hides the music widget
        return super.onKeyDown(code, event)
    }

    override fun onKeyLongPress(code: Int, event: KeyEvent): Boolean {
        if (shownMusic != null && !metro) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { Music.previous(this); refreshMusicSoon(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { Music.next(this); refreshMusicSoon(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { Music.playPause(this); refreshMusicSoon(); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { Music.open(this); return true }
            }
        }
        when {
            // Metro: hold OK on a tile = its menu (move, resize, colour, unpin, settings); elsewhere Settings.
            Keys.isOk(code) -> if (!(metro && start.menuForSelected())) options()
            else -> return super.onKeyLongPress(code, event)
        }
        return true
    }

    override fun onKeyUp(code: Int, event: KeyEvent): Boolean {
        val shortPress = event.isTracking && !event.isCanceled
        when {
            Keys.isOk(code) -> { if (shortPress) { if (metro) openSelected() else openDrawer() } }
            !metro && Keys.dpadPref(code) != null -> { if (shortPress) openShortcut(Keys.dpadPref(code)!!) }
            code == KeyEvent.KEYCODE_BACK -> { if (shortPress) hideMusic() }
            else -> return super.onKeyUp(code, event)
        }
        return true
    }

    /** Metro: OK opens the selected tile (with a press), or All apps if nothing is selected. */
    private fun openSelected() {
        val v = currentFocus
        if (v == null || v === window.decorView) { openDrawer(); return }
        Turnstile.press(v)
        v.performClick()
    }

    /** Metro: OK goes to our own handling (tap vs hold), not straight to the selected tile. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (metro && start.moving >= 0) {
            // Moving a tile: arrows push it through the list (a row is about three tiles); OK or Back ends.
            val delta = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                KeyEvent.KEYCODE_DPAD_UP -> -3; KeyEvent.KEYCODE_DPAD_DOWN -> 3
                else -> 0
            }
            val done = Keys.isOk(event.keyCode) || event.keyCode == KeyEvent.KEYCODE_BACK
            if (delta != 0 || done) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (delta != 0) start.moveBy(delta)
                    else { start.stopMoving(); android.widget.Toast.makeText(this, "Tile moved", android.widget.Toast.LENGTH_SHORT).show() }
                }
                return true
            }
        }
        if (metro && Keys.isOk(event.keyCode)) return event.dispatch(this, window.decorView.keyDispatcherState, this)
        return super.dispatchKeyEvent(event)
    }

    private fun openDrawer() = leave { startActivity(Intent(this, AppsActivity::class.java)) }

    private fun openCallLog() =
        Apps.start(this, Intent(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE))

    /** Number key [n] (or its dock icon): open its app, or pick one if the key is empty. */
    private fun openNumber(n: Int) {
        val target = if (!metro && n in Dock.KEYS && Dock.enabled(this)) Dock.target(this, n) else Prefs.get(this, Prefs.num(n))
        if (target == null) { Ui.pick(this, Prefs.num(n), "Pick app for key $n"); return }
        leave { if (!Apps.launch(this, target)) Ui.pick(this, Prefs.num(n), "Pick app for key $n") }
    }

    /**
     * Swipes on Home: left → All apps (as on a tile phone), down → the notification shade,
     * up → All apps. On a Start screen taller than the display, up and down scroll it first:
     * they only count at the bottom (up) or the top (down).
     */
    private val swipe by lazy {
        val minDist = 80 * resources.displayMetrics.density
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                val s = e1 ?: return false
                val dx = e2.x - s.x
                val dy = e2.y - s.y
                val scroll = if (metro) findViewById<View>(R.id.startScroll) else null
                when {
                    -dx > minDist && -dx > 2 * Math.abs(dy) && vx < 0 -> openDrawer()
                    dy > minDist && dy > 2 * Math.abs(dx) && vy > 0 && scroll?.canScrollVertically(-1) != true -> expandShade()
                    -dy > minDist && -dy > 2 * Math.abs(dx) && vy < 0 && scroll?.canScrollVertically(1) != true -> openDrawer()
                    else -> return false
                }
                return true
            }
        })
    }

    /** Pull down the notification shade (needs only the normal EXPAND_STATUS_BAR permission). */
    @android.annotation.SuppressLint("WrongConstant")
    private fun expandShade() {
        try {
            val sb = getSystemService("statusbar") ?: return
            sb.javaClass.getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Exception) { /* not available on this phone */ }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        swipe.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun openShortcut(pref: String) {
        val name = Prefs.DPAD.first { it.first == pref }.second
        val target = Prefs.get(this, pref) ?: run { Ui.pick(this, pref, "Pick app for $name"); return }
        leave { if (!Apps.launch(this, target)) Ui.pick(this, pref, "Pick app for $name") }
    }

    // ---- Music widget: refreshed every second while Home is on screen ----

    private val musicView by lazy { findViewById<View>(R.id.music) }
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refreshMusic(); ticker.postDelayed(this, 1000) }
    }

    private var shownMusic: Music.Now? = null

    /** Back: hide the widget until another song plays (the music keeps going). */
    private fun hideMusic() {
        shownMusic?.let { Music.hide(it); refreshMusic() }
    }

    private fun refreshMusicSoon() = ticker.postDelayed({ refreshMusic() }, 350)

    private fun refreshMusic() {
        val now = if (Prefs.getBool(this, Prefs.SHOW_MUSIC, true)) Music.visible(Music.now(this)) else null
        shownMusic = now
        if (metro) { start.music(now); start.tick(); return }
        musicView.visibility = if (now == null) View.GONE else View.VISIBLE
        updateDpadVisibility()
        if (now == null) return
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
