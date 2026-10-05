package com.calmlauncher.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.calmlauncher.MainActivity
import com.calmlauncher.R
import com.calmlauncher.domain.model.AppKey
import com.calmlauncher.feature.gate.GateActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** All notifications this app can post. Every one is local and optional. */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DIGEST, context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_digest_desc) },
                NotificationChannel(CHANNEL_LIMITS, context.getString(R.string.channel_limits), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_limits_desc) },
                NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_MIN)
                    .apply {
                        description = context.getString(R.string.channel_monitor_desc)
                        setShowBadge(false)
                    },
            ),
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showDigest(count: Int) {
        if (!canPost() || count <= 0) return
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_DIGEST)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, REQ_DIGEST, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_stat_calm)
            .setContentTitle(context.getString(R.string.digest_ready_title))
            .setContentText(context.resources.getQuantityString(R.plurals.digest_ready_text, count, count))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        notify(ID_DIGEST, notification)
    }

    fun showGateNotification(packageName: String) {
        if (!canPost()) return
        val intent = GateActivity.intent(context, AppKey(packageName, "", 0), external = true)
        val pi = PendingIntent.getActivity(context, REQ_GATE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_LIMITS)
            .setSmallIcon(R.drawable.ic_stat_calm)
            .setContentTitle(context.getString(R.string.gate_notification_title))
            .setContentText(context.getString(R.string.gate_notification_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        notify(ID_GATE, notification)
    }

    fun monitorNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, REQ_MONITOR, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_calm)
            .setContentTitle(context.getString(R.string.monitor_notification_title))
            .setContentText(context.getString(R.string.monitor_notification_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    fun cancelGate() = manager.cancel(ID_GATE)

    @android.annotation.SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification) {
        if (!canPost()) return
        runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val CHANNEL_DIGEST = "digest"
        const val CHANNEL_LIMITS = "limits"
        const val CHANNEL_MONITOR = "monitor"
        const val ID_DIGEST = 10
        const val ID_GATE = 11
        const val ID_MONITOR = 12
        private const val REQ_DIGEST = 100
        private const val REQ_GATE = 101
        private const val REQ_MONITOR = 102
    }
}
