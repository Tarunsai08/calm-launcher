package com.calmlauncher.domain.backup

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCodecTest {

    @Test
    fun `current schema round trips`() {
        val doc = BackupDocument(
            exportedAt = 42,
            settings = mapOf("themeMode" to JsonPrimitive("DARK"), "showSeconds" to JsonPrimitive(true)),
            apps = listOf(AppMetaBackup("a/b#0", alias = "Mail", hidden = false, favoriteOrder = 0)),
            categories = listOf(CategoryBackup(1, "Work")),
            rules = listOf(RuleBackup("app:a", pauseEnabled = true, dailyLimitMinutes = 30, blockWindows = listOf(WindowBackup(31, 1320, 420)))),
            focusSchedules = listOf(WindowBackup(31, 540, 1020, name = "Work hours")),
            notificationRules = listOf(NotificationRuleBackup("a", "digest")),
            notes = listOf(NoteBackup("hello", 1)),
            todos = listOf(TodoBackup("milk", false, 2)),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(doc))
        assertEquals(doc, decoded)
    }

    @Test
    fun `v1 backups migrate favorites hidden and aliases`() {
        val v1 = """
            {
              "version": 1,
              "exportedAt": "1700000000000",
              "favorites": ["x/A#0", "y/B#0"],
              "hidden": ["z/C#0"],
              "aliases": { "y/B#0": "Bee" },
              "settings": { "clock24": "true", "showDate": false }
            }
        """.trimIndent()
        val doc = BackupCodec.decode(v1)
        assertEquals(BackupDocument.CURRENT_VERSION, doc.schemaVersion)
        assertEquals(1_700_000_000_000L, doc.exportedAt)
        val byId = doc.apps.associateBy { it.id }
        assertEquals(0, byId.getValue("x/A#0").favoriteOrder)
        assertEquals(1, byId.getValue("y/B#0").favoriteOrder)
        assertEquals("Bee", byId.getValue("y/B#0").alias)
        assertTrue(byId.getValue("z/C#0").hidden)
        assertNull(byId.getValue("z/C#0").favoriteOrder)
        assertEquals(JsonPrimitive("H24"), doc.settings["clockFormat"])
        assertNull(doc.settings["clock24"])
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """{"schemaVersion":2,"app":"calm-launcher","somethingNew":123,"apps":[]}"""
        assertEquals(0, BackupCodec.decode(json).apps.size)
    }

    @Test
    fun `newer schema is rejected with a clear message`() {
        val e = assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"schemaVersion":99}""") }
        assertTrue(e.message!!.contains("newer"))
    }

    @Test
    fun `garbage is rejected`() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"schemaVersion":2,"apps":"nope"}""") }
    }
}
