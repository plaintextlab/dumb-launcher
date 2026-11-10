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
    override fun onListenerDisconnected() { counts = emptyList(); onChange?.invoke() }
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
        counts = active
            .filter { it.packageName !in skip && it.isClearable && !it.isOngoing }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .sortedByDescending { it.postTime }
            .groupingBy { it.packageName }.eachCount()
            .toList()
        onChange?.invoke()
    }
}
