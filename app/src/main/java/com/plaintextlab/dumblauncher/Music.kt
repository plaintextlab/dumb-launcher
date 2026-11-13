package com.plaintextlab.dumblauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * What is playing, and remote control for it. Works with any player that has a media session
 * (YouTube, YouTube Music, Spotify, Auxio, …).
 * With notification access we read the media session (title, artist, art, progress). Without it we
 * can still tell that music is playing and send media keys, just without the details.
 */
object Music {
    /**
     * [pkg] is the playing app (null without notification access); [key] tells songs apart.
     * [positionMs] / [durationMs] are -1 when the player doesn't say.
     */
    data class Now(val title: String, val artist: String, val art: Bitmap?, val playing: Boolean,
                   val pkg: String? = null, val app: String = "", val positionMs: Long = -1, val durationMs: Long = -1) {
        val key get() = "$pkg|$title|$artist"
        /** 0..1, or null when the length isn't known. */
        val progress: Float? get() =
            if (durationMs > 0 && positionMs >= 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
    }

    private val ACTIVE = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM)

    private fun active(mc: MediaController) = mc.playbackState?.state in ACTIVE

    private fun controller(c: Context): MediaController? {
        if (!NotifListener.granted(c)) return null
        return try {
            val msm = c.getSystemService(MediaSessionManager::class.java)
            val sessions = msm.getActiveSessions(ComponentName(c, NotifListener::class.java))
            // Playing (or about to: YouTube buffers between videos) first, then paused, then any with a song.
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull { active(it) }
                ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
                ?: sessions.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }
        } catch (e: Exception) {
            null
        }
    }

    /** null = nothing to show. */
    fun now(c: Context): Now? {
        controller(c)?.let { mc ->
            val md = mc.metadata
            val pkg = mc.packageName
            val app = label(c, pkg)
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val state = mc.playbackState
            val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: -1L
            val now = Now(title ?: app.ifEmpty { "Music" }, artist ?: app, null, active(mc), pkg, app,
                position(state), duration)
            return now.copy(art = art(c, now, md))
        }
        val am = c.getSystemService(AudioManager::class.java)
        return if (am.isMusicActive) Now("Music playing", "", null, true) else null
    }

    /** Where the song is now: the last reported place, moved on by the time since, while it plays. */
    private fun position(s: PlaybackState?): Long {
        if (s == null || s.position < 0) return -1
        if (s.state != PlaybackState.STATE_PLAYING || s.lastPositionUpdateTime <= 0) return s.position
        val since = SystemClock.elapsedRealtime() - s.lastPositionUpdateTime
        return s.position + (since * s.playbackSpeed).toLong()
    }

    // ---- Album art ----
    // Players hand it over in different ways: a picture in the session (most), a link to one
    // (YouTube Music, some others), or only on their media notification (YouTube). Try them all.

    private val artCache = ConcurrentHashMap<String, Bitmap>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private const val ART_PX = 512

    private fun art(c: Context, n: Now, md: MediaMetadata?): Bitmap? {
        val b = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (b != null) {
            // Each read hands over a new copy of the same picture: keep one per song, so the screens
            // can tell when the cover really changes (and only redraw then).
            val id = "${n.key}|${b.width}x${b.height}"
            artCache[id]?.let { return it }
            if (artCache.size > 20) artCache.clear()
            return scaled(b).also { artCache[id] = it }
        }
        val uri = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).firstNotNullOfOrNull { md?.getString(it)?.takeIf { s -> s.isNotBlank() } }
        if (uri != null) {
            artCache[uri]?.let { return it }
            if (loading.add(uri)) {
                val app = c.applicationContext
                Bg.run({ load(app, uri) }) { bmp ->
                    loading.remove(uri)
                    if (bmp != null) { if (artCache.size > 20) artCache.clear(); artCache[uri] = bmp }
                }
            }
        }
        // Still loading, or no link at all: the picture on the player's notification.
        return n.pkg?.let { NotifListener.mediaArt[it] }
    }

    private fun load(c: Context, uri: String): Bitmap? = try {
        val u = Uri.parse(uri)
        val stream = when (u.scheme) {
            "http", "https" -> (URL(uri).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000; readTimeout = 5000
            }.inputStream
            else -> c.contentResolver.openInputStream(u)
        }
        stream?.use { BitmapFactory.decodeStream(it) }?.let { scaled(it) }
    } catch (e: Exception) { null }

    /** Keep pictures small: some players hand over full-size covers. */
    fun scaled(b: Bitmap): Bitmap {
        val big = maxOf(b.width, b.height)
        if (big <= ART_PX) return b
        val f = ART_PX.toFloat() / big
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    private val labels = ConcurrentHashMap<String, String>()
    private fun label(c: Context, pkg: String) = labels.getOrPut(pkg) {
        try { c.packageManager.getApplicationInfo(pkg, 0).loadLabel(c.packageManager).toString() } catch (e: Exception) { "" }
    }

    /** The player's own icon (for the now-playing bar and the popup). */
    fun appIcon(c: Context, n: Now): Drawable? = n.pkg?.let {
        try { c.packageManager.getApplicationIcon(it) } catch (e: Exception) { null }
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
            // Use the player's own controls when it says it has them; otherwise press a media
            // button on its session, which every player understands.
            val actions = mc.playbackState?.actions ?: 0L
            val t = mc.transportControls
            val playing = active(mc)
            val want = when (code) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> PlaybackState.ACTION_SKIP_TO_NEXT
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
                else -> if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
            }
            val toggles = code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            if (actions and (if (toggles) want or PlaybackState.ACTION_PLAY_PAUSE else want) != 0L) {
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> t.skipToNext()
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> t.skipToPrevious()
                    else -> if (playing) t.pause() else t.play()
                }
            } else {
                val key = if (toggles)
                    (if (playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY) else code
                mc.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                mc.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            }
            return
        }
        // No access: press a "media key" for whichever app is playing.
        val am = c.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    /** "3:07", or "1:02:45" for long videos. */
    fun time(ms: Long): String {
        if (ms < 0) return ""
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
