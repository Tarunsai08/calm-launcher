package com.calmlauncher.service

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calmlauncher.data.di.ApplicationScope
import com.calmlauncher.data.tools.NotificationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun scheduler(): Scheduler
    fun notifications(): NotificationRepository
    fun notifier(): Notifier

    @ApplicationScope
    fun scope(): CoroutineScope
}

private fun Context.receiverDeps(): ReceiverEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReceiverEntryPoint::class.java)

/** Restores schedules after reboot, app update, or any clock / time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = context.receiverDeps()
        val pending = goAsync()
        deps.scope().launch {
            try {
                deps.scheduler().rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Fires at digest times and at focus/block schedule boundaries. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = context.receiverDeps()
        val pending = goAsync()
        deps.scope().launch {
            try {
                if (intent.action == ACTION_DIGEST) {
                    deps.notifier().ensureChannels()
                    deps.notifier().showDigest(deps.notifications().pendingCount())
                }
                deps.scheduler().rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DIGEST = "com.calmlauncher.action.DIGEST"
        const val ACTION_FOCUS_BOUNDARY = "com.calmlauncher.action.FOCUS_BOUNDARY"
    }
}

/**
 * Device-admin receiver used ONLY for the optional double-tap-to-lock fallback on devices or
 * Android versions where the accessibility lock action isn't available. Its policy file asks
 * for "force-lock" and nothing else.
 */
class LockAdminReceiver : DeviceAdminReceiver()
