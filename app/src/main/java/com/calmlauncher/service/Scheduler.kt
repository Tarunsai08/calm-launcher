package com.calmlauncher.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.domain.focus.TimeWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules use inexact AlarmManager alarms only (no exact-alarm permission, no wakelocks).
 * Focus state itself is always computed from the current wall-clock time, so a missed or late
 * alarm can never leave focus mode stuck; alarms only refresh the tile/monitor and deliver
 * the digest. Rescheduled on boot, time change, time-zone change and app update.
 */
@Singleton
class Scheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val rules: RulesRepository,
    private val systemActions: SystemActions,
    private val enforcement: EnforcementController,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        expireManualFocus()
        scheduleDigest()
        scheduleFocusBoundary()
        FocusTileService.requestUpdate(context)
        refreshMonitor()
    }

    fun refreshMonitor() {
        val s = settings.settings.value
        MonitorService.startIfNeeded(
            context,
            enabled = s.enforcementEnabled && s.fallbackMonitorEnabled,
            needed = enforcement.isNeeded(),
            accessibilityConnected = systemActions.isServiceConnected(),
        )
    }

    private suspend fun expireManualFocus() {
        val s = settings.settings.value
        if (s.focusManualActive && s.focusManualUntil > 0 && s.focusManualUntil <= System.currentTimeMillis()) {
            settings.update { it.copy(focusManualActive = false, focusManualUntil = 0L) }
        }
    }

    private fun scheduleDigest() {
        val s = settings.settings.value
        val pi = pending(AlarmReceiver.ACTION_DIGEST, REQ_DIGEST)
        alarms.cancel(pi)
        if (!s.digestEnabled || s.digestTimes.isEmpty()) return
        val next = nextDailyOccurrence(s.digestTimes, ZonedDateTime.now()) ?: return
        setInexact(next.toInstant().toEpochMilli(), pi)
    }

    private fun scheduleFocusBoundary() {
        val pi = pending(AlarmReceiver.ACTION_FOCUS_BOUNDARY, REQ_FOCUS)
        alarms.cancel(pi)
        val now = ZonedDateTime.now()
        val candidates = ArrayList<ZonedDateTime>()
        rules.snapshot.value.focusSchedules.filter { it.enabled }.forEach { sched ->
            sched.window.nextBoundary(now)?.let { candidates += it }
        }
        rules.snapshot.value.rules.values.flatMap { it.blockWindows }.forEach { w ->
            w.nextBoundary(now)?.let { candidates += it }
        }
        val until = settings.settings.value.focusManualUntil
        if (until > System.currentTimeMillis()) {
            candidates += ZonedDateTime.ofInstant(Instant.ofEpochMilli(until), now.zone)
        }
        val next = candidates.minOrNull() ?: return
        setInexact(next.toInstant().toEpochMilli(), pi)
    }

    private fun setInexact(at: Long, pi: PendingIntent) {
        runCatching {
            // A 5-minute window lets the system batch this with other alarms.
            alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, pi)
        }
    }

    private fun pending(action: String, request: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        /** Next wall-clock occurrence (strictly after [now]) of any of the given minutes-of-day. */
        fun nextDailyOccurrence(minutes: Set<Int>, now: ZonedDateTime): ZonedDateTime? = minutes
            .filter { it in 0 until TimeWindow.MINUTES_PER_DAY }
            .map { minute ->
                val today = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
                if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(minute / 60, minute % 60).atZone(now.zone)
            }
            .minOrNull()

        private const val REQ_DIGEST = 200
        private const val REQ_FOCUS = 201
        private const val WINDOW_MS = 5 * 60 * 1000L
    }
}
