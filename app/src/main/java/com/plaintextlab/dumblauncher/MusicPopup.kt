package com.plaintextlab.dumblauncher

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * The music popup: a big cover, the song, how far into it, and ⏮ ⏯ ⏭.
 * Tap (or OK on) the cover to open the player. Keys: ◀ ▶ move between the buttons, Back closes.
 * Updates itself twice a second and closes when the music stops.
 */
class MusicPopup private constructor(private val a: Activity, private val onChange: () -> Unit) {
    companion object {
        /** Open the popup for what is playing now (if anything). */
        fun show(a: Activity, onChange: () -> Unit = {}) {
            if (Music.now(a) == null) return
            MusicPopup(a, onChange).open()
        }
    }

    private val dp = a.resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()
    private val accent = Prefs.getInt(a, Prefs.HIGHLIGHT, Ui.COLOURS[0].second)
    private val dialog = Dialog(a)
    private val ticker = Handler(Looper.getMainLooper())

    private val root = LinearLayout(a)
    private val appIcon = ImageView(a)
    private val appName = text(13f, a.getColor(R.color.dim))
    private val art = ImageView(a)
    private val title = text(20f, a.getColor(R.color.fg), bold = true)
    private val artist = text(14f, a.getColor(R.color.dim))
    private val progress = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal)
    private val elapsed = text(12f, a.getColor(R.color.dim))
    private val total = text(12f, a.getColor(R.color.dim))
    private val times = LinearLayout(a)
    private val hint = text(12f, a.getColor(R.color.dim))

    private var shown: Music.Now? = null
    private var shownArt: Bitmap? = null
    private var shownColour = 0

    private fun text(size: Float, colour: Int, bold: Boolean = false) = TextView(a).apply {
        textSize = size; setTextColor(colour); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** A round button that shows a white ring when the keys move onto it. */
    private fun button(icon: Int, sizeDp: Int, filled: Boolean, what: String, act: () -> Unit) = ImageView(a).apply {
        setImageResource(icon)
        val ring = { focused: Boolean ->
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (filled) accent else if (focused) 0x22FFFFFF else Color.TRANSPARENT)
                if (focused) setStroke(px(2), Color.WHITE)
            }
        }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), ring(true))
            addState(intArrayOf(android.R.attr.state_pressed), ring(true))
            addState(intArrayOf(), ring(false))
        }
        if (filled && light(accent)) setColorFilter(Color.BLACK) else setColorFilter(Color.WHITE)
        val p = px(sizeDp) / (if (filled) 4 else 5); setPadding(p, p, p, p)
        isFocusable = true; isClickable = true; contentDescription = what
        setOnClickListener { Turnstile.press(this); act(); onChange(); ticker.postDelayed({ refresh() }, 300) }
        layoutParams = LinearLayout.LayoutParams(px(sizeDp), px(sizeDp)).apply { marginStart = px(14); marginEnd = px(14) }
    }

    private fun open() {
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(px(20), px(16), px(20), px(20))

        // The player: its icon and name.
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(appIcon, LinearLayout.LayoutParams(px(20), px(20)))
        head.addView(appName, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(8) })
        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // The cover, square, as wide as the popup. Tap it for the player.
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.isFocusable = true; art.isClickable = true
        art.contentDescription = "Open the player"
        art.foreground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { setStroke(px(3), Color.WHITE) })
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        art.setOnClickListener { openPlayer() }
        val side = coverSide()
        root.addView(art, LinearLayout.LayoutParams(side, side).apply { topMargin = px(14); gravity = Gravity.CENTER_HORIZONTAL })

        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(18) })
        root.addView(artist, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(2) })

        // How far into the song.
        progress.max = 1000
        progress.progressTintList = android.content.res.ColorStateList.valueOf(accent)
        progress.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(4)).apply { topMargin = px(16) })
        times.orientation = LinearLayout.HORIZONTAL
        times.addView(elapsed, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(total)
        root.addView(times, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(4) })

        // ⏮ ⏯ ⏭
        val controls = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(button(R.drawable.ic_prev, 56, false, "Previous") { Music.previous(a) })
        val p = button(R.drawable.ic_pause, 72, true, "Play or pause") { Music.playPause(a) }
        controls.addView(p)
        controls.addView(button(R.drawable.ic_next, 56, false, "Next") { Music.next(a) })
        root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(14) })
        playButton = p

        hint.maxLines = 3; hint.gravity = Gravity.CENTER
        root.addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12) })

        val frame = FrameLayout(a).apply { addView(root) }
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(frame)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(popupWidth(), ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.75f)
        }
        dialog.setOnDismissListener { ticker.removeCallbacksAndMessages(null) }
        dialog.setOnKeyListener { _, code, e -> keys(code, e) }
        refresh()
        dialog.show()
        p.requestFocus()
        if (Anim.on(a)) {
            root.alpha = 0f; root.scaleX = 0.92f; root.scaleY = 0.92f
            root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(Turnstile.EXPO_OUT).start()
        }
        ticker.postDelayed(object : Runnable {
            override fun run() { refresh(); ticker.postDelayed(this, 500) }
        }, 500)
    }

    private var playButton: ImageView? = null

    private fun popupWidth(): Int {
        val w = a.resources.displayMetrics.widthPixels
        return minOf(w - px(24), px(380))
    }

    /** The cover fills the popup's width, but leaves room for the rest on short (keypad phone) screens. */
    private fun coverSide(): Int {
        val inner = popupWidth() - px(40)
        val room = a.resources.displayMetrics.heightPixels - px(330)
        return minOf(inner, room).coerceAtLeast(px(96))
    }

    private fun keys(code: Int, e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return false
        return when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { Music.playPause(a); later(); true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { Music.next(a); later(); true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { Music.previous(a); later(); true }
            else -> false
        }
    }

    private fun later() { onChange(); ticker.postDelayed({ refresh() }, 300) }

    private fun openPlayer() {
        if (shown?.pkg == null) {
            // No notification access: we can't tell which app is playing, so ask for it instead.
            Apps.start(a, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else Music.open(a)
        dialog.dismiss()
    }

    private fun refresh() {
        val n = Music.now(a)
        if (n == null) { dialog.dismiss(); return }
        val old = shown
        shown = n

        if (old?.pkg != n.pkg || old == null) {
            val icon = Music.appIcon(a, n)
            appIcon.visibility = if (icon == null) View.GONE else View.VISIBLE
            appIcon.setImageDrawable(icon)
            appName.text = n.app.ifEmpty { "Now playing" }
        }
        if (title.text != n.title) title.text = n.title
        artist.text = n.artist.takeIf { it != n.app } ?: ""
        artist.visibility = if (artist.text.isEmpty()) View.GONE else View.VISIBLE

        if (n.art !== shownArt || old == null) {
            shownArt = n.art
            if (n.art != null) {
                art.setImageBitmap(n.art); art.scaleType = ImageView.ScaleType.CENTER_CROP
                art.background = null
                art.setPadding(0, 0, 0, 0)
            } else {
                art.setImageResource(R.drawable.ic_music); art.scaleType = ImageView.ScaleType.FIT_CENTER
                art.background = ColorDrawable(0x1FFFFFFF)
                val p = coverSide() / 3; art.setPadding(p, p, p, p)
            }
            tint(n.art)
        }

        val prog = n.progress
        progress.visibility = if (prog == null) View.INVISIBLE else View.VISIBLE
        times.visibility = if (prog == null) View.GONE else View.VISIBLE
        if (prog != null) {
            progress.progress = (prog * 1000).toInt()
            elapsed.text = Music.time(n.positionMs); total.text = Music.time(n.durationMs)
        }
        playButton?.setImageResource(if (n.playing) R.drawable.ic_pause else R.drawable.ic_play)

        hint.text = if (n.pkg == null) "Allow notification access to see the song, its cover and progress. Tap the picture to allow it."
            else "Tap the cover to open ${n.app.ifEmpty { "the player" }}"
    }

    /** The popup takes on the cover's colour, fading down to black. */
    private fun tint(b: Bitmap?) {
        val c = b?.let { average(it) } ?: a.getColor(R.color.surface)
        if (c == shownColour) return
        shownColour = c
        val top = blend(c, Color.BLACK, 0.45f)
        val bottom = blend(c, Color.BLACK, 0.85f)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
    }

    private fun light(c: Int) = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c) > 150

    private fun average(b: Bitmap): Int = try {
        Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
    } catch (e: Exception) { a.getColor(R.color.surface) }

    private fun blend(c: Int, with: Int, f: Float): Int = Color.rgb(
        (Color.red(c) * (1 - f) + Color.red(with) * f).toInt(),
        (Color.green(c) * (1 - f) + Color.green(with) * f).toInt(),
        (Color.blue(c) * (1 - f) + Color.blue(with) * f).toInt())
}
