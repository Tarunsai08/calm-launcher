package com.calmlauncher.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.calmlauncher.data.usage.UsageRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fallback for devices where the user does not want the accessibility service. Polls the
 * system usage log for the foreground app every few seconds, but only while a focus rule
 * could apply; it stops itself as soon as nothing needs enforcing. No wakelocks are held,
 * so it naturally pauses while the screen is off.
 */
@AndroidEntryPoint
class MonitorService : LifecycleService() {

    @Inject lateinit var usage: UsageRepository
    @Inject lateinit var enforcement: EnforcementController
    @Inject lateinit var notifier: Notifier

    private var loop: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        notifier.ensureChannels()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, Notifier.ID_MONITOR, notifier.monitorNotification(), type)
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) {
            loop = lifecycleScope.launch {
                while (isActive) {
                    if (!enforcement.isNeeded() || !usage.hasPermission()) {
                        stopSelf()
                        break
                    }
                    usage.currentForegroundPackage()?.let { enforcement.onForeground(it, fromAccessibility = false) }
                    delay(POLL_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val POLL_MS = 3_000L

        /** Starts the monitor if it's enabled, needed, and the accessibility path isn't active. */
        fun startIfNeeded(context: Context, enabled: Boolean, needed: Boolean, accessibilityConnected: Boolean) {
            if (!enabled || !needed || accessibilityConnected) {
                stop(context)
                return
            }
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MonitorService::class.java)) }
        }
    }
}
