package com.plaintextlab.dumblauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast

/**
 * What is playing, and remote control for it.
 * With notification access we read the media session (title, artist, art). Without it we
 * can still tell that music is playing and send media keys, just without the details.
 */
object Music {
    /** [pkg] is the playing app (null without notification access); [key] tells songs apart. */
    data class Now(val title: String, val artist: String, val art: Bitmap?, val playing: Boolean,
                   val pkg: String? = null) {
        val key get() = "$pkg|$title|$artist"
    }

    private fun controller(c: Context): MediaController? {
        if (!NotifListener.granted(c)) return null
        return try {
            val msm = c.getSystemService(MediaSessionManager::class.java)
            val sessions = msm.getActiveSessions(ComponentName(c, NotifListener::class.java))
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        } catch (e: SecurityException) {
            null
        }
    }

    /** null = nothing to show. */
    fun now(c: Context): Now? {
        controller(c)?.let { mc ->
            val md = mc.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val app = try {
                c.packageManager.getApplicationInfo(mc.packageName, 0).loadLabel(c.packageManager).toString()
            } catch (e: Exception) { "" }
            return Now(title ?: app.ifEmpty { "Music" }, artist ?: app, art,
                mc.playbackState?.state == PlaybackState.STATE_PLAYING, mc.packageName)
        }
        val am = c.getSystemService(AudioManager::class.java)
        return if (am.isMusicActive) Now("Music playing", "", null, true) else null
    }

    // ---- Hiding the widget (Back on Home) until another song, or playback resumes ----
    @Volatile private var hiddenKey: String? = null
    @Volatile private var hiddenWhilePlaying = false

    fun hide(n: Now) { hiddenKey = n.key; hiddenWhilePlaying = n.playing }

    /** What the widget should show: [now] unless the user hid this song. */
    fun visible(n: Now?): Now? {
        if (n == null || hiddenKey == null) return n
        if (n.key != hiddenKey || (n.playing && !hiddenWhilePlaying)) { hiddenKey = null; return n }
        if (!n.playing) hiddenWhilePlaying = false // paused after hiding: show again when it plays
        return null
    }

    /** Open the player: its own "now playing" screen if it offers one, else the app. */
    fun open(a: Activity): Boolean {
        val mc = controller(a)
        if (mc == null) {
            Toast.makeText(a, "Allow notification access in Settings to open the player", Toast.LENGTH_LONG).show()
            return false
        }
        mc.sessionActivity?.let { pi ->
            try {
                val opts = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle() else null
                pi.send(a, 0, null, null, null, null, opts); return true
            } catch (e: Exception) { }
        }
        val launch = a.packageManager.getLaunchIntentForPackage(mc.packageName) ?: return false
        return Apps.start(a, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun playPause(c: Context) = send(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next(c: Context) = send(c, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous(c: Context) = send(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun send(c: Context, code: Int) {
        val mc = controller(c)
        if (mc != null) {
            val t = mc.transportControls
            when (code) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> t.skipToNext()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> t.skipToPrevious()
                else -> if (mc.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            }
            return
        }
        // No access: press a "media key" for whichever app is playing.
        val am = c.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }
}
