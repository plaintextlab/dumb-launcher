package com.plaintextlab.dumblauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/** Applies the look settings (wallpaper, dimming, status bar) to a window. */
object Look {
    val WALL_MODES = listOf("Home screen only", "All launcher screens", "Off (black)")
    val DIMS = listOf(0, 25, 50, 75)

    fun apply(a: Activity, isHome: Boolean) {
        val w = a.window
        val mode = Prefs.getInt(a, Prefs.WALL_MODE, 0)
        val show = mode == 1 || (mode == 0 && isHome)
        if (show) {
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            val dim = Prefs.getInt(a, Prefs.WALL_DIM, 50)
            w.setBackgroundDrawable(ColorDrawable(Color.argb(dim * 255 / 100, 0, 0, 0)))
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            w.setBackgroundDrawable(ColorDrawable(Color.BLACK))
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

    override fun onResume() {
        super.onResume()
        Look.apply(this, isHome)
    }
}
