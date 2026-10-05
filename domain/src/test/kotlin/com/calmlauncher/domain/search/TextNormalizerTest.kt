package com.calmlauncher.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextNormalizerTest {
    @Test
    fun `normalize strips accents and case`() {
        assertEquals("cafe uber", TextNormalizer.normalize("  Café   Über "))
        assertEquals("strasse", TextNormalizer.normalize("Straße"))
        assertEquals("lodz", TextNormalizer.normalize("Łódź"))
    }

    @Test
    fun `initials of words`() {
        assertEquals("ym", TextNormalizer.initials("YouTube Music"))
        assertEquals("gm", TextNormalizer.initials("Google-Maps"))
    }

    @Test
    fun `word starts include camel humps`() {
        val starts = TextNormalizer.wordStarts("YouTube Music")
        assertTrue("youtube" in starts)
        assertTrue("tube" in starts)
        assertTrue("music" in starts)
    }

    @Test
    fun `usage score grows with launches and decays with age`() {
        val now = 1_000_000_000_000L
        val fresh = UsageScore.compute(10, now, now)
        val old = UsageScore.compute(10, now - 30L * 24 * 3600 * 1000, now)
        val rare = UsageScore.compute(1, now, now)
        assertTrue(fresh > old)
        assertTrue(fresh > rare)
        assertEquals(0.0, UsageScore.compute(0, 0, now))
    }
}
