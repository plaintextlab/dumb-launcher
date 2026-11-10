package com.plaintextlab.dumblauncher

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Launcher settings. Each row shows its current value; ● changes it. */
class SettingsActivity : BaseActivity() {

    private class Row(val title: String, val value: () -> String = { "" }, val action: () -> Unit) {
        val icon = iconFor(title)
    }

    companion object {
        private fun iconFor(title: String) = when {
            title.startsWith("Wallpaper from gallery") -> R.drawable.s_image
            title.startsWith("Wallpaper") && title.contains("dimming") -> R.drawable.s_dim
            title.startsWith("Wallpaper") || title.startsWith("Show wallpaper") -> R.drawable.s_wallpaper
            title.contains("drawer background") || title.contains("blurred") -> R.drawable.s_blur
            title.startsWith("Hide notification bar") -> R.drawable.s_fullscreen
            title.contains("arrow-key") || title.contains("shortcut") -> R.drawable.s_dpad
            title.startsWith("Number key") -> R.drawable.s_dialpad
            title.startsWith("Music") -> R.drawable.ic_music
            title.contains("otification") -> R.drawable.s_bell
            title.startsWith("Highlight") -> R.drawable.s_palette
            title.startsWith("Animations") -> R.drawable.s_motion
            title.startsWith("Zoom") -> R.drawable.s_zoom
            title.startsWith("Icon pack") -> R.drawable.s_grid
            title.startsWith("Icon style") -> R.drawable.s_shape
            title.startsWith("Reset") -> R.drawable.s_refresh
            title.startsWith("Key tester") -> R.drawable.s_keyboard
            title.contains("default launcher") -> R.drawable.s_home
            else -> R.drawable.s_tune
        }
    }

    private lateinit var list: ListView
    private lateinit var rows: List<Row>
    private val pickImage = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        findViewById<TextView>(R.id.query).text = "Settings"
        findViewById<TextView>(R.id.hint).text = "● Change    ↩ Back"
        rows = buildRows()
        list = findViewById(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> rows[pos].action() }
        Ui.trackHighlight(list)
        Anim.cascade(list)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() = adapter.notifyDataSetChanged()

    private fun onOff(key: String, def: Boolean) = if (Prefs.getBool(this, key, def)) "On" else "Off"

    private fun toggle(key: String, def: Boolean) {
        Prefs.setBool(this, key, !Prefs.getBool(this, key, def)); Look.apply(this, false); refresh()
    }

    private fun buildRows() = listOf(
        // Wallpaper
        Row("Wallpaper from gallery", action = {
            val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            try { startActivityForResult(Intent.createChooser(i, "Pick a picture"), pickImage) }
            catch (e: Exception) { Toast.makeText(this, "No gallery app", Toast.LENGTH_SHORT).show() }
        }),
        Row("Wallpaper from system picker", action = {
            Apps.start(this, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
        }),
        Row("Show wallpaper on", { Look.WALL_MODES[Prefs.getInt(this, Prefs.WALL_MODE, 0)] }) {
            Ui.choose(this, "Show wallpaper on", Look.WALL_MODES.mapIndexed { i, s ->
                s to { Prefs.setInt(this, Prefs.WALL_MODE, i); Look.apply(this, false); refresh() } })
        },
        Row("App drawer background", { Look.DRAWER_MODES[Prefs.getInt(this, Prefs.DRAWER_BG, 0)] }) {
            Ui.choose(this, "App drawer background", Look.DRAWER_MODES.mapIndexed { i, s ->
                s to { Prefs.setInt(this, Prefs.DRAWER_BG, i); refresh() } })
        },
        Row("Refresh blurred wallpaper", action = { rebuildBlur() }),
        Row("Wallpaper dimming", { "${Prefs.getInt(this, Prefs.WALL_DIM, 50)}%" }) {
            Ui.choose(this, "Darken wallpaper by", Look.DIMS.map { d ->
                "$d%" to { Prefs.setInt(this, Prefs.WALL_DIM, d); Look.apply(this, false); refresh() } })
        },
        // Screen
        Row("Hide notification bar", { onOff(Prefs.HIDE_STATUS, false) }) { toggle(Prefs.HIDE_STATUS, false) },
        Row("Show arrow-key apps on Home", { onOff(Prefs.SHOW_DPAD, true) }) { toggle(Prefs.SHOW_DPAD, true) },
        Row("Show notifications on Home", { onOff(Prefs.SHOW_COUNTS, true) }) { toggle(Prefs.SHOW_COUNTS, true) },
        Row("Music widget on Home", { onOff(Prefs.SHOW_MUSIC, true) }) { toggle(Prefs.SHOW_MUSIC, true) },
        Row("Notification access (other apps, song info)", { if (NotifListener.granted(this)) "Allowed" else "Not allowed: calls, SMS and basic music controls still work" }) {
            if (!Apps.start(this, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
                Toast.makeText(this, "This phone doesn't allow notification access", Toast.LENGTH_LONG).show()
            }
        },
        Row("Highlight colour", { Ui.COLOURS.firstOrNull { it.second == Prefs.getInt(this, Prefs.HIGHLIGHT, Ui.COLOURS[0].second) }?.first ?: "" }) {
            Ui.choose(this, "Highlight colour", Ui.COLOURS.map { (name, c) ->
                name to { Prefs.setInt(this, Prefs.HIGHLIGHT, c); refresh() } })
        },
        Row("Animations", { onOff(Prefs.ANIM, true) }) { toggle(Prefs.ANIM, true) },
        Row("Zoom selected icon", { onOff(Prefs.ZOOM, true) }) { toggle(Prefs.ZOOM, true) },
        // Icons
        Row("Icon pack", { IconPacks.current(this)?.label ?: "None" }) { Icons.choosePack(this) { refresh() } },
        Row("Icon style (no pack)", { Icons.STYLES[Prefs.getInt(this, Prefs.ICON_STYLE, 0)] }) {
            Ui.choose(this, "Icon style", Icons.STYLES.mapIndexed { i, s ->
                s to { Prefs.setInt(this, Prefs.ICON_STYLE, i); Icons.clear(); refresh() } })
        },
        // Shortcuts
        *Prefs.DPAD.map { (pref, name) ->
            Row("Home $name shortcut", { Apps.entry(this, Prefs.get(this, pref))?.label ?: "Not set" }) {
                Ui.pick(this, pref, "Pick app for $name")
            }
        }.toTypedArray(),
        Row("Number key shortcuts (0-9)", { "${(0..9).count { Prefs.get(this, Prefs.num(it)) != null }} of 10 set" }) {
            Ui.choose(this, "Number key shortcuts", (0..9).map { n ->
                "$n   ${Apps.entry(this, Prefs.get(this, Prefs.num(n)))?.label ?: "not set"}" to {
                    Ui.choose(this, "Key $n", listOf(
                        "Choose app or shortcut" to { Ui.pick(this, Prefs.num(n), "Pick app for key $n") },
                        "Clear" to { Prefs.set(this, Prefs.num(n), null); refresh() },
                    ))
                }
            })
        },
        Row("Reset menu to defaults", action = {
            Prefs.resetSlots(this); Toast.makeText(this, "Menu reset", Toast.LENGTH_SHORT).show()
        }),
        // System
        Row("Key tester", action = { startActivity(Intent(this, KeyTesterActivity::class.java)) }),
        Row("Choose default launcher", action = { Apps.start(this, Intent(Settings.ACTION_HOME_SETTINGS)) }),
        Row("Phone settings", action = { Apps.start(this, Intent(Settings.ACTION_SETTINGS)) }),
    )

    @Deprecated("Activity result API needs AndroidX; plain Activity is enough here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (requestCode != pickImage || resultCode != RESULT_OK || uri == null) return
        Ui.choose(this, "Use picture for", listOf(
            "Home screen" to { setWallpaper(uri, WallpaperManager.FLAG_SYSTEM) },
            "Lock screen" to { setWallpaper(uri, WallpaperManager.FLAG_LOCK) },
            "Both" to { setWallpaper(uri, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) },
        ))
    }

    private fun setWallpaper(uri: Uri, which: Int) {
        Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = try {
                contentResolver.openInputStream(uri)!!.use {
                    WallpaperManager.getInstance(this).setStream(it, null, true, which)
                }
                if (which and WallpaperManager.FLAG_SYSTEM != 0) { Blur.saveSource(this, uri); Blur.rebuild(this) }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                Toast.makeText(this, if (ok) "Wallpaper set" else "Could not use that picture", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun rebuildBlur() {
        Thread {
            val ok = Blur.rebuild(this)
            runOnUiThread {
                Toast.makeText(this,
                    if (ok) "Blurred wallpaper updated"
                    else "Android won't share this wallpaper. Set it with \"Wallpaper from gallery\" instead.",
                    Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        if (Keys.handleAppKey(this, code, event)) return true
        return super.onKeyDown(code, event)
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(pos: Int) = rows[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
            val v = convert ?: layoutInflater.inflate(R.layout.item_setting, parent, false)
            v.findViewById<TextView>(R.id.label).text = rows[pos].title
            v.findViewById<TextView>(R.id.value).text = rows[pos].value()
            v.findViewById<ImageView>(R.id.sicon).setImageResource(rows[pos].icon)
            Ui.paint(v, pos == list.selectedItemPosition)
            return v
        }
    }
}
