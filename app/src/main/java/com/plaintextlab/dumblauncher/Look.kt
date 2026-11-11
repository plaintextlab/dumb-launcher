package com.plaintextlab.dumblauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/** Applies the look settings (wallpaper, dimming, status bar) to a window. */
object Look {
    val WALL_MODES = listOf("Home screen only", "All launcher screens", "Off (black)")
    val DIMS = listOf(0, 25, 50, 75)
    val DRAWER_MODES = listOf("Blurred wallpaper", "See-through wallpaper", "Black")

    fun apply(a: Activity, isHome: Boolean, isDrawer: Boolean = false) {
        val w = a.window
        val dim = ColorDrawable(Color.argb(Prefs.getInt(a, Prefs.WALL_DIM, 50) * 255 / 100, 0, 0, 0))
        val wallMode = Prefs.getInt(a, Prefs.WALL_MODE, 0)
        // 0 = blurred, 1 = see-through, 2 = black
        val bg = if (isDrawer) Prefs.getInt(a, Prefs.DRAWER_BG, 0)
                 else if (wallMode == 1 || (wallMode == 0 && isHome)) 1 else 2
        val blurred = if (bg == 0) Blur.drawable(a) else null
        when {
            blurred != null -> {
                w.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                w.setBackgroundDrawable(LayerDrawable(arrayOf(blurred, dim)))
            }
            bg <= 1 -> { // see-through (also the fallback while the blur isn't ready)
                w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                w.setBackgroundDrawable(dim)
            }
            else -> {
                w.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                w.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            }
        }

        val hide = Prefs.getBool(a, Prefs.HIDE_STATUS, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ctl = w.insetsController ?: return
            if (hide) {
                ctl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctl.hide(WindowInsets.Type.statusBars())
            } else {
                ctl.show(WindowInsets.Type.statusBars())
            }
        } else {
            @Suppress("DEPRECATION")
            if (hide) w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }
}

/** Every launcher screen applies the look settings when it comes to the front. */
abstract class BaseActivity : Activity() {
    open val isHome = false
    open val isDrawer = false

    /** The app drawer zooms open (S60 style); other screens slide in from the right. */
    open val zooms = false

    @Suppress("DEPRECATION") // overrideActivityTransition is API 34+; this works on all versions
    override fun startActivity(intent: Intent?, options: Bundle?) {
        super.startActivity(intent, options)
        val target = intent?.component ?: return
        if (target.packageName != packageName || !Anim.on(this)) return
        val enter = if (target.className == AppsActivity::class.java.name) R.anim.zoom_in else R.anim.slide_in
        overridePendingTransition(enter, R.anim.hold)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        if (Anim.on(this)) overridePendingTransition(R.anim.hold, if (zooms) R.anim.zoom_out else R.anim.slide_out)
    }

    override fun onResume() {
        super.onResume()
        Look.apply(this, isHome, isDrawer)
        // First time in the drawer: make the blurred wallpaper in the background, then show it.
        if (isDrawer && Prefs.getInt(this, Prefs.DRAWER_BG, 0) == 0 && Blur.get(this) == null) {
            Thread { if (Blur.rebuild(this)) runOnUiThread { Look.apply(this, isHome, isDrawer) } }.start()
        }
    }
}
