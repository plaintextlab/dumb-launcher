package com.plaintextlab.dumblauncher

import android.annotation.SuppressLint
import android.graphics.Camera
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.LayoutAnimationController
import android.view.animation.Transformation

/**
 * Tile-UI motion: content swings in and out like a turnstile hinged on the left edge of the
 * screen, one row after another ("feathered"); tiles tilt under your finger; live tiles flip.
 * Everything is skipped when animations are off in Settings.
 */
object Turnstile {
    private const val IN_MS = 300L
    private const val OUT_MS = 180L
    private const val STAGGER_MS = 35L

    /** Rotates a view around the Y axis, hinged on its left edge, with perspective. */
    class Swing(private val from: Float, private val to: Float) : Animation() {
        private val camera = Camera()
        override fun applyTransformation(t: Float, tr: Transformation) {
            val deg = from + (to - from) * t
            val m = tr.matrix
            camera.save()
            camera.rotateY(deg)
            camera.getMatrix(m)
            camera.restore()
            m.preTranslate(0f, -height / 2f)
            m.postTranslate(0f, height / 2f)
        }

        private var height = 0
        override fun initialize(w: Int, h: Int, pw: Int, ph: Int) {
            super.initialize(w, h, pw, ph); height = h
        }
    }

    /** Rows of [group] swing in one after another the next time it lays out. */
    fun featherIn(group: ViewGroup) {
        if (!Anim.on(group.context)) { group.layoutAnimation = null; return }
        val set = AnimationSet(false).apply {
            addAnimation(Swing(-75f, 0f).apply { interpolator = DecelerateInterpolator(2f) })
            addAnimation(AlphaAnimation(0f, 1f).apply { interpolator = DecelerateInterpolator() })
            duration = IN_MS
        }
        group.layoutAnimation = LayoutAnimationController(set, 0.12f)
        group.scheduleLayoutAnimation()
    }

    /** Swing [views] out (top first), then run [then]. Runs [then] at once if animations are off. */
    fun out(views: List<View>, then: () -> Unit) {
        val shown = views.filter { it.isShown }
        if (shown.isEmpty() || !Anim.on(shown[0].context)) { then(); return }
        shown.forEachIndexed { i, v ->
            v.cameraDistance = 8000 * v.resources.displayMetrics.density
            v.pivotX = 0f; v.pivotY = v.height / 2f
            val a = v.animate().rotationY(80f).alpha(0f).setDuration(OUT_MS)
                .setStartDelay(i * STAGGER_MS).setInterpolator(AccelerateInterpolator(1.5f))
            if (i == shown.lastIndex) a.withEndAction { then() }
            a.start()
        }
    }

    /** Put views back after [out] (when coming back to the screen). */
    fun reset(views: List<View>) = views.forEach { v ->
        v.animate().cancel(); v.rotationY = 0f; v.alpha = 1f
    }

    /** Pressing a tile tilts it toward the finger and pushes it in slightly. */
    @SuppressLint("ClickableViewAccessibility") // only adds the visual; clicks still go through
    fun tilt(v: View) {
        v.setOnTouchListener { view, e ->
            if (!Anim.on(view.context)) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.cameraDistance = 6000 * view.resources.displayMetrics.density
                    val dx = (e.x / view.width - 0.5f).coerceIn(-0.5f, 0.5f)
                    val dy = (e.y / view.height - 0.5f).coerceIn(-0.5f, 0.5f)
                    view.animate().rotationY(dx * 18f).rotationX(-dy * 18f)
                        .scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().rotationX(0f).rotationY(0f).scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    /** The same push-in for a key press on the selected tile. */
    fun press(v: View) {
        if (!Anim.on(v.context)) return
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
        }.start()
    }

    /** Live tile: flip over the horizontal axis, calling [swap] at the half-way point. */
    fun flip(v: View, swap: () -> Unit) {
        if (!Anim.on(v.context)) { swap(); return }
        v.cameraDistance = 8000 * v.resources.displayMetrics.density
        v.animate().rotationX(90f).setDuration(220).setInterpolator(AccelerateInterpolator()).withEndAction {
            swap()
            v.rotationX = -90f
            v.animate().rotationX(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        }.start()
    }
}
