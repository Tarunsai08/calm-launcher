package com.calmlauncher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.calmlauncher.data.di.ApplicationScope
import com.calmlauncher.data.tools.NotificationMode
import com.calmlauncher.data.tools.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opt-in. Applies per-app rules the user set: allow (default), silence (dismiss), or move into
 * the digest. Digest items (title + text) are stored only in the local database so the user can
 * read them later inside the launcher; nothing leaves the device.
 *
 * Ongoing, foreground-service, call and alarm notifications are always left alone.
 */
@AndroidEntryPoint
class CalmNotificationListener : NotificationListenerService() {

    @Inject lateinit var notifications: NotificationRepository
    @Inject @field:ApplicationScope lateinit var scope: CoroutineScope

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        val mode = notifications.rules.value[notification.packageName] ?: NotificationMode.ALLOW
        if (mode == NotificationMode.ALLOW) return
        if (!isClearable(notification)) return

        if (mode == NotificationMode.DIGEST) {
            val extras = notification.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString().orEmpty()
            // Group summaries carry no content of their own.
            val isSummary = notification.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
            if (!isSummary && (title.isNotBlank() || text.isNotBlank())) {
                scope.launch {
                    notifications.addToDigest(notification.packageName, title, text, notification.postTime, notification.key)
                }
            }
        }
        runCatching { cancelNotification(notification.key) }
    }

    private fun isClearable(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.isOngoing || !sbn.isClearable) return false
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        val category = n.category
        return category != Notification.CATEGORY_CALL &&
            category != Notification.CATEGORY_ALARM &&
            category != Notification.CATEGORY_NAVIGATION
    }

    companion object {
        @Volatile
        var connected: Boolean = false
            private set
    }
}
