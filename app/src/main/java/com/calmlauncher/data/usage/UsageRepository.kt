package com.calmlauncher.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.calmlauncher.domain.usage.UsageAggregator
import com.calmlauncher.domain.usage.UsageEventLite
import com.calmlauncher.domain.usage.UsageSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads on-device usage statistics. Nothing is stored or sent anywhere; results are computed
 * on demand from the system's own records. Every call degrades to "no data" without the
 * special Usage Access permission.
 */
class UsageRepository(
    private val context: Context,
    private val io: CoroutineDispatcher,
) {
    private val usm: UsageStatsManager? = context.getSystemService(UsageStatsManager::class.java)
    private val mutex = Mutex()
    private var cachedToday: Pair<Long, UsageSummary>? = null
    private var cachedDay: Long = -1

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Today's usage, cached for a few seconds because launch decisions call this often. */
    suspend fun today(maxAgeMs: Long = CACHE_MS): UsageSummary? = mutex.withLock {
        if (!hasPermission()) return@withLock null
        val now = System.currentTimeMillis()
        val day = LocalDate.now().toEpochDay()
        val cached = cachedToday
        if (cached != null && cachedDay == day && now - cached.first < maxAgeMs) return@withLock cached.second
        val summary = summarize(LocalDate.now(), now)
        cachedToday = now to summary
        cachedDay = day
        summary
    }

    fun invalidate() {
        cachedToday = null
    }

    suspend fun usedTodayMs(packageName: String): Long = today()?.perPackageMs?.get(packageName) ?: 0L

    suspend fun usedTodayMs(packages: Collection<String>): Long {
        val summary = today() ?: return 0L
        return packages.sumOf { summary.perPackageMs[it] ?: 0L }
    }

    /** Usage for one calendar day in the current zone. */
    suspend fun summarize(day: LocalDate, now: Long = System.currentTimeMillis()): UsageSummary = withContext(io) {
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
        if (end <= start) return@withContext UsageSummary(emptyMap(), null)
        val events = readEvents(start, end)
        UsageAggregator.aggregate(
            events = events,
            windowStart = start,
            windowEnd = end,
            unlockEventsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
        )
    }

    /** The package currently in the foreground according to usage events, if known. */
    suspend fun currentForegroundPackage(lookBackMs: Long = 60_000L): String? = withContext(io) {
        val end = System.currentTimeMillis()
        val events = readEvents(end - lookBackMs, end)
        var last: String? = null
        for (e in events) {
            when (e.type) {
                UsageEventLite.Type.RESUMED -> last = e.packageName
                UsageEventLite.Type.PAUSED -> if (last == e.packageName) last = null
                UsageEventLite.Type.SCREEN_OFF -> last = null
                UsageEventLite.Type.UNLOCK -> Unit
            }
        }
        last
    }

    private fun readEvents(start: Long, end: Long): List<UsageEventLite> {
        val manager = usm ?: return emptyList()
        if (!hasPermission()) return emptyList()
        val result = ArrayList<UsageEventLite>()
        val events: UsageEvents = try {
            manager.queryEvents(start, end) ?: return emptyList()
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: IllegalStateException) {
            // Thrown while the user is still locked (direct boot).
            return emptyList()
        }
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(event)) break
            val type = mapType(event.eventType) ?: continue
            result += UsageEventLite(event.packageName ?: "", type, event.timeStamp)
        }
        return result
    }

    private fun mapType(type: Int): UsageEventLite.Type? = when (type) {
        // ACTIVITY_RESUMED == MOVE_TO_FOREGROUND (1), ACTIVITY_PAUSED == MOVE_TO_BACKGROUND (2)
        1 -> UsageEventLite.Type.RESUMED
        2 -> UsageEventLite.Type.PAUSED
        SCREEN_NON_INTERACTIVE -> UsageEventLite.Type.SCREEN_OFF
        KEYGUARD_HIDDEN -> UsageEventLite.Type.UNLOCK
        else -> null
    }

    companion object {
        private const val CACHE_MS = 10_000L
        private const val SCREEN_NON_INTERACTIVE = 16
        private const val KEYGUARD_HIDDEN = 18
    }
}
