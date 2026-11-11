package com.plaintextlab.dumblauncher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.AbsListView
import android.widget.GridView

/**
 * Small, cheap animations (view properties and window transitions only), so they stay
 * smooth on a low-end chip. All can be turned off in Settings; Android's own
 * "animation scale" developer setting also applies.
 */
object Anim {
    fun on(c: Context) = Prefs.getBool(c, Prefs.ANIM, true)

    /** Menu cells pop in one after another; list rows slide up in a cascade. */
    fun cascade(list: AbsListView) {
        if (!on(list.context)) return
        if (Icons.style(list.context) == Icons.METRO) { Turnstile.featherIn(list) { (0 until list.childCount).map { list.getChildAt(it) } }; return }
        val res = if (list is GridView) R.anim.grid_cascade else R.anim.list_cascade
        list.layoutAnimation = AnimationUtils.loadLayoutAnimation(list.context, res)
    }

    /** Zoom the selected icon with a little bounce. */
    fun zoom(icon: View, scale: Float, animate: Boolean) {
        icon.animate().cancel()
        if (animate && on(icon.context)) {
            icon.animate().scaleX(scale).scaleY(scale).setDuration(180)
                .setInterpolator(if (scale > 1f) OvershootInterpolator(2.5f) else DecelerateInterpolator())
                .start()
        } else {
            icon.scaleX = scale; icon.scaleY = scale
        }
    }

    /** Fade the highlight colour in instead of snapping. */
    fun fadeIn(bg: android.graphics.drawable.Drawable, c: Context) {
        if (!on(c)) return
        bg.alpha = 0
        ValueAnimator.ofInt(0, 255).apply {
            duration = 140
            addUpdateListener { bg.alpha = it.animatedValue as Int }
        }.start()
    }

    /** Home: clock drops in gently each time you come back. */
    fun homeArrive(clock: View, status: View) {
        if (!on(clock.context)) return
        clock.alpha = 0f
        clock.translationY = -24 * clock.resources.displayMetrics.density
        clock.animate().alpha(1f).translationY(0f).setDuration(350)
            .setInterpolator(DecelerateInterpolator()).start()
        // A soft blink draws the eye to new missed calls / messages.
        ObjectAnimator.ofFloat(status, View.ALPHA, 0f, 1f, 0.35f, 1f).apply {
            startDelay = 300; duration = 900
        }.start()
    }
}
