package com.plaintextlab.dumblauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent

/**
 * What is playing, and remote control for it.
 * With notification access we read the media session (title, artist, art). Without it we
 * can still tell that music is playing and send media keys, just without the details.
 */
object Music {
    data class Now(val title: String, val artist: String, val art: Bitmap?, val playing: Boolean)

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
                mc.playbackState?.state == PlaybackState.STATE_PLAYING)
        }
        val am = c.getSystemService(AudioManager::class.java)
        return if (am.isMusicActive) Now("Music playing", "", null, true) else null
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
