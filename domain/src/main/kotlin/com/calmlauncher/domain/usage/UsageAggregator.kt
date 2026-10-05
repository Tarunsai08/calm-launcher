package com.calmlauncher.domain.usage

/** Platform-neutral subset of `UsageEvents.Event`. */
data class UsageEventLite(
    val packageName: String,
    val type: Type,
    val timestamp: Long,
) {
    enum class Type { RESUMED, PAUSED, SCREEN_OFF, UNLOCK }
}

data class UsageSummary(
    val perPackageMs: Map<String, Long>,
    val unlocks: Int?,
) {
    val totalMs: Long get() = perPackageMs.values.sum()

    fun top(n: Int, exclude: Set<String> = emptySet()): List<Pair<String, Long>> =
        perPackageMs.entries
            .filter { it.key !in exclude && it.value > 0 }
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
}

/**
 * Turns a stream of foreground/background events into per-app foreground durations within
 * [windowStart, windowEnd). Robust to missing events: an app that was already in the
 * foreground at [windowStart] (first event is PAUSED) is counted from the window start, and
 * an app still in the foreground at the end is counted until [windowEnd].
 */
object UsageAggregator {

    fun aggregate(
        events: List<UsageEventLite>,
        windowStart: Long,
        windowEnd: Long,
        unlockEventsSupported: Boolean = true,
    ): UsageSummary {
        val totals = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val seen = HashSet<String>()
        var unlocks = 0

        fun add(pkg: String, from: Long, to: Long) {
            val start = from.coerceAtLeast(windowStart)
            val end = to.coerceAtMost(windowEnd)
            if (end > start) totals[pkg] = (totals[pkg] ?: 0L) + (end - start)
        }

        for (e in events.sortedBy { it.timestamp }) {
            if (e.timestamp >= windowEnd) break
            when (e.type) {
                UsageEventLite.Type.RESUMED -> {
                    // Another app resuming implies the previous foreground app left.
                    for ((pkg, start) in resumedAt.entries.toList()) {
                        if (pkg != e.packageName) {
                            add(pkg, start, e.timestamp)
                            resumedAt.remove(pkg)
                        }
                    }
                    if (e.packageName !in resumedAt) resumedAt[e.packageName] = e.timestamp
                    seen += e.packageName
                }
                UsageEventLite.Type.PAUSED -> {
                    val start = resumedAt.remove(e.packageName)
                    if (start != null) {
                        add(e.packageName, start, e.timestamp)
                    } else if (e.packageName !in seen) {
                        // Was in the foreground before the window started.
                        add(e.packageName, windowStart, e.timestamp)
                    }
                    seen += e.packageName
                }
                UsageEventLite.Type.SCREEN_OFF -> {
                    for ((pkg, start) in resumedAt) add(pkg, start, e.timestamp)
                    resumedAt.clear()
                }
                UsageEventLite.Type.UNLOCK -> if (e.timestamp >= windowStart) unlocks++
            }
        }
        for ((pkg, start) in resumedAt) add(pkg, start, windowEnd)
        return UsageSummary(totals, if (unlockEventsSupported) unlocks else null)
    }
}
