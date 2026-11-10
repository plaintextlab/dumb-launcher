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
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Launcher settings. Each row shows its current value; ● changes it. */
class SettingsActivity : BaseActivity() {

    private class Row(val title: String, val value: () -> String = { "" }, val action: () -> Unit)

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
        Row("Wallpaper dimming", { "${Prefs.getInt(this, Prefs.WALL_DIM, 50)}%" }) {
            Ui.choose(this, "Darken wallpaper by", Look.DIMS.map { d ->
                "$d%" to { Prefs.setInt(this, Prefs.WALL_DIM, d); Look.apply(this, false); refresh() } })
        },
        // Screen
        Row("Hide notification bar", { onOff(Prefs.HIDE_STATUS, false) }) { toggle(Prefs.HIDE_STATUS, false) },
        Row("Show missed calls & messages", { onOff(Prefs.SHOW_COUNTS, true) }) { toggle(Prefs.SHOW_COUNTS, true) },
        Row("Highlight colour", { Ui.COLOURS.firstOrNull { it.second == Prefs.getInt(this, Prefs.HIGHLIGHT, Ui.COLOURS[0].second) }?.first ?: "" }) {
            Ui.choose(this, "Highlight colour", Ui.COLOURS.map { (name, c) ->
                name to { Prefs.setInt(this, Prefs.HIGHLIGHT, c); refresh() } })
        },
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
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                Toast.makeText(this, if (ok) "Wallpaper set" else "Could not use that picture", Toast.LENGTH_SHORT).show()
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
            Ui.paint(v, pos == list.selectedItemPosition)
            return v
        }
    }
}
