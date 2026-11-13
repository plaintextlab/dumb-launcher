package com.plaintextlab.dumblauncher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.telecom.TelecomManager

/**
 * Counts other apps' notifications for the Home screen (needs "notification access",
 * which some Android Go phones don't allow). Calls and SMS are counted separately from
 * the call log and SMS store, so the phone and messaging apps are skipped here.
 */
class NotifListener : NotificationListenerService() {

    companion object {
        /** package → number of notifications, newest app first. */
        @Volatile var counts: List<Pair<String, Int>> = emptyList()
            private set
        var onChange: (() -> Unit)? = null
        /** package → the album art on its media notification (YouTube puts it only there). */
        @Volatile var mediaArt: Map<String, android.graphics.Bitmap> = emptyMap()
            private set

        fun granted(c: Context): Boolean = try {
            val me = ComponentName(c, NotifListener::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                c.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(me)
            } else {
                Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
                    ?.contains(me.flattenToString()) == true
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() { counts = emptyList(); mediaArt = emptyMap(); onChange?.invoke() }
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) = recount()

    private fun recount() {
        val skip = setOfNotNull(
            packageName,
            try { getSystemService(TelecomManager::class.java)?.defaultDialerPackage } catch (e: Exception) { null },
            Telephony.Sms.getDefaultSmsPackage(this),
            "android", "com.android.systemui",
        )
        val active = try { activeNotifications.orEmpty() } catch (e: Exception) { emptyArray() }
        // Players re-post their notification often: keep the old picture when it is the same one,
        // so the Music tile only redraws when the cover changes.
        val before = mediaArt
        mediaArt = active.filter { it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }
            .mapNotNull { sbn ->
                largeIcon(sbn.notification)?.let { b ->
                    val old = before[sbn.packageName]
                    sbn.packageName to (if (old != null && old.sameAs(b)) old else b)
                }
            }.toMap()
        counts = active
            .filter { it.packageName !in skip && it.isClearable && !it.isOngoing }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .sortedByDescending { it.postTime }
            .groupingBy { it.packageName }.eachCount()
            .toList()
        onChange?.invoke()
    }

    /** The notification's big picture (the cover, on a media notification). */
    private fun largeIcon(n: Notification): android.graphics.Bitmap? = try {
        val d = n.getLargeIcon()?.loadDrawable(this)
        val b = (d as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: d?.let { dr ->
                val w = dr.intrinsicWidth.takeIf { it > 0 } ?: 256; val h = dr.intrinsicHeight.takeIf { it > 0 } ?: 256
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                    val cv = android.graphics.Canvas(bmp); dr.setBounds(0, 0, w, h); dr.draw(cv)
                }
            }
            ?: @Suppress("DEPRECATION") n.extras.getParcelable<android.graphics.Bitmap>(Notification.EXTRA_LARGE_ICON)
        b?.let { Music.scaled(it) }
    } catch (e: Exception) { null }
}
