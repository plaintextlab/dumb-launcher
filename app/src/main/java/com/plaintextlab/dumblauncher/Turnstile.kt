package com.plaintextlab.dumblauncher

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.Interpolator
import kotlin.math.pow

/**
 * Tile-UI motion, modelled on the phone tile UI's turnstile: every tile swings around one
 * shared hinge on the LEFT EDGE OF THE SCREEN (not its own edge), top rows first, with an
 * exponential ease. Everything runs as view-property animations on hardware layers, so each
 * frame only re-composites textures (no re-layout, no re-draw), which keeps it smooth on a
 * slow chip. Skipped entirely when animations are off in Settings.
 */
object Turnstile {
    /** Fast start, long soft landing (the "exponential ease-out" of the tile UI). */
    val EXPO_OUT = Interpolator { t -> if (t >= 1f) 1f else 1f - 2.0.pow(-10.0 * t).toFloat() }
    /** Slow start, then away. */
    val EXPO_IN = Interpolator { t -> if (t <= 0f) 0f else 2.0.pow(10.0 * (t - 1)).toFloat() }
    /** Gentle ease-out for things sliding into place. */
    val QUINT_OUT = Interpolator { t -> 1f - (1f - t).pow(5) }

    private const val IN_MS = 420L
    private const val OUT_MS = 260L
    private const val IN_ANGLE = -80f
    private const val OUT_ANGLE = 80f
    /** Extra delay from the top of the screen to the bottom, and from left to right. */
    private const val SPREAD_Y_MS = 170f
    private const val SPREAD_X_MS = 40f

    /** Hinge [v] on the screen's left edge; returns its delay from where it sits on screen. */
    private fun hinge(v: View): Long {
        val dm = v.resources.displayMetrics
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        v.cameraDistance = 12000 * dm.density
        v.pivotX = -loc[0].toFloat() + v.translationX
        v.pivotY = v.height / 2f
        val y = (loc[1].toFloat() / dm.heightPixels).coerceIn(0f, 1f)
        val x = (loc[0].toFloat() / dm.widthPixels).coerceIn(0f, 1f)
        return (y * SPREAD_Y_MS + x * SPREAD_X_MS).toLong()
    }

    private fun onScreen(v: View): Boolean {
        if (!v.isShown || v.height == 0) return false
        val loc = IntArray(2); v.getLocationInWindow(loc)
        return loc[1] + v.height > 0 && loc[1] < v.resources.displayMetrics.heightPixels
    }

    /**
     * Swing [views] in the next time [host] is about to draw (after it has laid out, so the
     * views' places are known). [views] is asked then, so a list can fill in first.
     */
    fun featherIn(host: View, views: () -> List<View>) {
        if (!Anim.on(host.context)) return
        var frames = 0
        host.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val shown = views().filter { onScreen(it) }
                if (shown.isEmpty() && ++frames < 30) return true       // nothing laid out yet: wait
                host.viewTreeObserver.removeOnPreDrawListener(this)
                shown.forEach { v ->
                    val delay = hinge(v)
                    v.animate().cancel()
                    v.rotationY = IN_ANGLE; v.alpha = 0f
                    v.animate().rotationY(0f).alpha(1f)
                        .setDuration(IN_MS).setStartDelay(delay).setInterpolator(EXPO_OUT)
                        .withLayer().start()
                }
                return true
            }
        })
    }

    /** Swing [views] out (top first), then run [then]. Runs [then] at once if animations are off. */
    fun out(views: List<View>, then: () -> Unit) {
        val shown = views.filter { onScreen(it) }
        if (shown.isEmpty() || !Anim.on(shown[0].context)) { then(); return }
        val delays = shown.map { hinge(it) }
        val last = delays.indices.maxByOrNull { delays[it] }!!
        shown.forEachIndexed { i, v ->
            v.animate().cancel()
            val a = v.animate().rotationY(OUT_ANGLE).alpha(0f)
                .setDuration(OUT_MS).setStartDelay(delays[i]).setInterpolator(EXPO_IN).withLayer()
            if (i == last) a.withEndAction { then() }
            a.start()
        }
    }

    /** Put views back after [out] (when coming back to the screen). */
    fun reset(views: List<View>) = views.forEach { v ->
        v.animate().cancel(); v.rotationY = 0f; v.rotationX = 0f; v.alpha = 1f
    }

    /** Pressing a tile tilts it toward the finger and pushes it in slightly. */
    @SuppressLint("ClickableViewAccessibility") // only adds the visual; clicks still go through
    fun tilt(v: View) {
        v.setOnTouchListener { view, e ->
            if (!Anim.on(view.context)) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.cameraDistance = 8000 * view.resources.displayMetrics.density
                    view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
                    val dx = (e.x / view.width - 0.5f).coerceIn(-0.5f, 0.5f)
                    val dy = (e.y / view.height - 0.5f).coerceIn(-0.5f, 0.5f)
                    view.animate().rotationY(dx * 16f).rotationX(-dy * 16f)
                        .scaleX(0.96f).scaleY(0.96f).setStartDelay(0).setDuration(120).setInterpolator(EXPO_OUT).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().rotationX(0f).rotationY(0f).scaleX(1f).scaleY(1f)
                        .setStartDelay(0).setDuration(220).setInterpolator(EXPO_OUT).start()
            }
            false
        }
    }

    /** The same push-in for a key press on the selected tile. */
    fun press(v: View) {
        if (!Anim.on(v.context)) return
        v.pivotX = v.width / 2f; v.pivotY = v.height / 2f
        v.animate().scaleX(0.94f).scaleY(0.94f).setStartDelay(0).setDuration(80).setInterpolator(EXPO_OUT).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(EXPO_OUT).start()
        }.start()
    }

    /** Live tile: flip over the horizontal axis, calling [swap] at the half-way point. */
    fun flip(v: View, swap: () -> Unit) {
        if (!Anim.on(v.context) || !v.isShown) { swap(); return }
        v.cameraDistance = 10000 * v.resources.displayMetrics.density
        v.pivotX = v.width / 2f; v.pivotY = v.height / 2f
        v.animate().rotationX(90f).setStartDelay(0).setDuration(260).setInterpolator(EXPO_IN).withLayer().withEndAction {
            swap()
            v.rotationX = -90f
            v.animate().rotationX(0f).setDuration(420).setInterpolator(EXPO_OUT).withLayer().start()
        }.start()
    }

    /** A tile sliding from where it was drawn to its new place (rearranging). */
    fun slide(v: View, dx: Float, dy: Float) {
        if (!Anim.on(v.context)) { v.translationX = dx; v.translationY = dy; return }
        v.animate().translationX(dx).translationY(dy).setStartDelay(0).setDuration(300).setInterpolator(QUINT_OUT).start()
    }
}
