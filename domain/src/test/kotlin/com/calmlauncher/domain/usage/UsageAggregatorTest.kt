package com.calmlauncher.domain.usage

import com.calmlauncher.domain.usage.UsageEventLite.Type.PAUSED
import com.calmlauncher.domain.usage.UsageEventLite.Type.RESUMED
import com.calmlauncher.domain.usage.UsageEventLite.Type.SCREEN_OFF
import com.calmlauncher.domain.usage.UsageEventLite.Type.UNLOCK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UsageAggregatorTest {
    private fun e(pkg: String, type: UsageEventLite.Type, t: Long) = UsageEventLite(pkg, type, t)

    @Test
    fun `sums simple sessions`() {
        val events = listOf(e("a", RESUMED, 100), e("a", PAUSED, 400), e("b", RESUMED, 500), e("b", PAUSED, 600), e("a", RESUMED, 700), e("a", PAUSED, 800))
        val s = UsageAggregator.aggregate(events, 0, 1000)
        assertEquals(400L, s.perPackageMs["a"])
        assertEquals(100L, s.perPackageMs["b"])
        assertEquals(500L, s.totalMs)
    }

    @Test
    fun `app already open at window start counts from start`() {
        val s = UsageAggregator.aggregate(listOf(e("a", PAUSED, 300)), 100, 1000)
        assertEquals(200L, s.perPackageMs["a"])
    }

    @Test
    fun `app still open counts until window end`() {
        val s = UsageAggregator.aggregate(listOf(e("a", RESUMED, 900)), 0, 1000)
        assertEquals(100L, s.perPackageMs["a"])
    }

    @Test
    fun `screen off ends sessions`() {
        val s = UsageAggregator.aggregate(listOf(e("a", RESUMED, 100), e("", SCREEN_OFF, 200), e("a", PAUSED, 900)), 0, 1000)
        assertEquals(100L, s.perPackageMs["a"])
    }

    @Test
    fun `next app resuming closes the previous one`() {
        val s = UsageAggregator.aggregate(listOf(e("a", RESUMED, 0), e("b", RESUMED, 50), e("a", PAUSED, 60), e("b", PAUSED, 100)), 0, 1000)
        assertEquals(50L, s.perPackageMs["a"])
        assertEquals(50L, s.perPackageMs["b"])
    }

    @Test
    fun `events outside the window are clipped`() {
        val s = UsageAggregator.aggregate(listOf(e("a", RESUMED, 50), e("a", PAUSED, 150), e("a", RESUMED, 990), e("a", PAUSED, 2000)), 100, 1000)
        assertEquals(60L, s.perPackageMs["a"])
    }

    @Test
    fun `unlocks are counted when supported`() {
        val events = listOf(e("", UNLOCK, 10), e("", UNLOCK, 20), e("", UNLOCK, 5))
        assertEquals(2, UsageAggregator.aggregate(events, 8, 100).unlocks)
        assertNull(UsageAggregator.aggregate(events, 0, 100, unlockEventsSupported = false).unlocks)
    }

    @Test
    fun `top apps excludes and sorts`() {
        val s = UsageSummary(mapOf("a" to 10L, "b" to 30L, "c" to 20L, "launcher" to 99L, "z" to 0L), null)
        assertEquals(listOf("b" to 30L, "c" to 20L), s.top(2, exclude = setOf("launcher")))
    }
}
