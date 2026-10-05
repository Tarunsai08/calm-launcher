package com.calmlauncher

import com.calmlauncher.data.settings.AccentColor
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsSerializer
import com.calmlauncher.data.settings.ThemeMode
import com.calmlauncher.domain.model.AppKey
import com.calmlauncher.domain.model.LauncherAction
import com.calmlauncher.feature.digest.parseTime
import com.calmlauncher.feature.drawer.DrawerViewModel
import com.calmlauncher.service.Scheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId

class AppLogicTest {

    @Test
    fun settingsSerializerRoundTripsEveryField() = runTest {
        val custom = LauncherSettings(
            themeMode = ThemeMode.AMOLED,
            accent = AccentColor.SAGE,
            swipeLeft = LauncherAction.LaunchApp(AppKey("com.x", "com.x.Main", 0)),
            focusAllowlist = setOf("a", "b"),
            digestTimes = setOf(480),
        )
        val out = ByteArrayOutputStream()
        SettingsSerializer.writeTo(custom, out)
        val back = SettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
        assertEquals(custom, back)
    }

    @Test
    fun settingsSerializerToleratesUnknownAndMissingKeys() = runTest {
        val json = """{"themeMode":"DARK","someFutureSetting":true}"""
        val s = SettingsSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(LauncherSettings.DEFAULT.swipeUp, s.swipeUp)
    }

    @Test
    fun emptySettingsFileIsDefaults() = runTest {
        assertEquals(LauncherSettings.DEFAULT, SettingsSerializer.readFrom(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun digestTimeParsing() {
        assertEquals(18 * 60, parseTime("18:00"))
        assertEquals(7 * 60 + 5, parseTime(" 7:05 "))
        assertNull(parseTime("24:00"))
        assertNull(parseTime("noon"))
    }

    @Test
    fun nextDailyOccurrencePicksSoonestFutureTime() {
        val zone = ZoneId.of("UTC")
        val now = LocalDateTime.of(2026, 10, 5, 13, 0).atZone(zone)
        val next = Scheduler.nextDailyOccurrence(setOf(12 * 60, 18 * 60), now)
        assertEquals(18, next?.hour)
        val late = LocalDateTime.of(2026, 10, 5, 19, 0).atZone(zone)
        val tomorrow = Scheduler.nextDailyOccurrence(setOf(12 * 60, 18 * 60), late)
        assertEquals(6, tomorrow?.dayOfMonth)
        assertEquals(12, tomorrow?.hour)
        assertNull(Scheduler.nextDailyOccurrence(emptySet(), now))
    }

    @Test
    fun sectionLetters() {
        assertEquals("A", DrawerViewModel.sectionLetter("apps"))
        assertEquals("E", DrawerViewModel.sectionLetter("Éclair"))
        assertEquals("#", DrawerViewModel.sectionLetter("1Password"))
        assertEquals("#", DrawerViewModel.sectionLetter(""))
    }
}
