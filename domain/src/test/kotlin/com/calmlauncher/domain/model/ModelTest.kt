package com.calmlauncher.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTest {
    @Test
    fun `app key round trips`() {
        val key = AppKey("com.example", "com.example.MainActivity", 10)
        assertEquals(key, AppKey.parse(key.id))
        assertNull(AppKey.parse("garbage"))
        assertNull(AppKey.parse("com.x/#0"))
        assertNull(AppKey.parse("com.x/Main#abc"))
    }

    @Test
    fun `label falls back to package when blank`() {
        val app = LauncherApp(AppKey("com.blank", "A", 0), systemLabel = "  ")
        assertEquals("com.blank", app.label)
        assertEquals("Mine", app.copy(alias = "Mine").label)
        assertEquals("Real", app.copy(systemLabel = "Real", alias = " ").label)
    }

    @Test
    fun `launcher actions round trip`() {
        for (a in LauncherAction.builtIns) assertEquals(a, LauncherAction.parse(a.storageValue))
        val app = LauncherAction.LaunchApp(AppKey("p", "c", 0))
        assertEquals(app, LauncherAction.parse(app.storageValue))
        assertEquals(LauncherAction.OpenDrawer, LauncherAction.parse("unknown", LauncherAction.OpenDrawer))
        assertEquals(LauncherAction.None, LauncherAction.parse(null))
        assertTrue(LauncherAction.parse("app:broken") is LauncherAction.None)
    }
}
