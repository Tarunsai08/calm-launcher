package com.calmlauncher.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Versioned, human-readable backup of all settings and rules. Written to a file the user
 * picks through the Storage Access Framework; never uploaded anywhere.
 */
@Serializable
data class BackupDocument(
    val schemaVersion: Int = CURRENT_VERSION,
    val app: String = APP_ID,
    val exportedAt: Long = 0L,
    /** Settings as key -> JSON value (boolean, number, string, or array of strings). */
    val settings: Map<String, JsonElement> = emptyMap(),
    val apps: List<AppMetaBackup> = emptyList(),
    val categories: List<CategoryBackup> = emptyList(),
    val rules: List<RuleBackup> = emptyList(),
    val focusSchedules: List<WindowBackup> = emptyList(),
    val notificationRules: List<NotificationRuleBackup> = emptyList(),
    val notes: List<NoteBackup> = emptyList(),
    val todos: List<TodoBackup> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 2
        const val APP_ID = "calm-launcher"
    }
}

@Serializable
data class AppMetaBackup(
    val id: String,
    val alias: String? = null,
    val hidden: Boolean = false,
    val categoryId: Long? = null,
    val favoriteOrder: Int? = null,
)

@Serializable
data class CategoryBackup(val id: Long, val name: String, val sortOrder: Int = 0)

@Serializable
data class WindowBackup(
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    val name: String = "",
)

@Serializable
data class RuleBackup(
    val target: String,
    val pauseEnabled: Boolean = false,
    val pauseSeconds: Int = 5,
    val askReason: Boolean = true,
    val dailyLimitMinutes: Int? = null,
    val alwaysBlocked: Boolean = false,
    val blockWindows: List<WindowBackup> = emptyList(),
)

@Serializable
data class NotificationRuleBackup(
    val packageName: String,
    @SerialName("mode") val mode: String,
)

@Serializable
data class NoteBackup(val text: String, val updatedAt: Long)

@Serializable
data class TodoBackup(val text: String, val done: Boolean, val createdAt: Long, val sortOrder: Int = 0)

class BackupFormatException(message: String) : Exception(message)

object BackupCodec {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(doc: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), doc)

    /** Parses any supported schema version, migrating it to [BackupDocument.CURRENT_VERSION]. */
    fun decode(text: String): BackupDocument {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: SerializationException) {
            throw BackupFormatException("Not a valid backup file: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Not a valid backup file: ${e.message}")
        }
        val migrated = BackupMigrations.migrateToCurrent(root)
        return try {
            json.decodeFromJsonElement(BackupDocument.serializer(), migrated)
        } catch (e: SerializationException) {
            throw BackupFormatException("Backup is damaged: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Backup is damaged: ${e.message}")
        }
    }
}

object BackupMigrations {

    fun versionOf(root: JsonObject): Int =
        (root["schemaVersion"] ?: root["version"])?.jsonPrimitive?.intOrNull ?: 1

    fun migrateToCurrent(input: JsonObject): JsonObject {
        var root = input
        var version = versionOf(root)
        if (version > BackupDocument.CURRENT_VERSION) {
            throw BackupFormatException(
                "This backup was made by a newer version (schema $version). Please update the launcher first.",
            )
        }
        while (version < BackupDocument.CURRENT_VERSION) {
            root = when (version) {
                1 -> v1ToV2(root)
                else -> throw BackupFormatException("Unsupported backup schema $version")
            }
            version = versionOf(root)
        }
        return root
    }

    /**
     * v1 stored favorites, hidden apps and aliases as three separate collections and used
     * "version" as the version key. v2 merges them into per-app records.
     */
    fun v1ToV2(v1: JsonObject): JsonObject {
        val favorites = v1["favorites"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val hidden = v1["hidden"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet()
        val aliases = v1["aliases"]?.jsonObject?.mapValues { it.value.jsonPrimitive.contentOrNull } ?: emptyMap()
        val ids = LinkedHashSet<String>().apply {
            addAll(favorites)
            addAll(hidden)
            addAll(aliases.keys)
        }
        val apps = buildJsonArray {
            for (id in ids) {
                add(
                    buildJsonObject {
                        put("id", id)
                        aliases[id]?.let { put("alias", it) }
                        put("hidden", id in hidden)
                        val order = favorites.indexOf(id)
                        if (order >= 0) put("favoriteOrder", order)
                    },
                )
            }
        }
        val settings = (v1["settings"] as? JsonObject) ?: JsonObject(emptyMap())
        // v1 called the 24h clock flag "clock24"; v2 uses an enum string.
        val migratedSettings = settings.toMutableMap()
        migratedSettings.remove("clock24")?.let { old ->
            val is24 = (old as? JsonPrimitive)?.contentOrNull == "true"
            migratedSettings["clockFormat"] = JsonPrimitive(if (is24) "H24" else "H12")
        }
        return buildJsonObject {
            put("schemaVersion", 2)
            put("app", BackupDocument.APP_ID)
            put("exportedAt", v1["exportedAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L)
            put("settings", JsonObject(migratedSettings))
            put("apps", apps)
            v1["rules"]?.let { put("rules", it as? JsonArray ?: JsonArray(emptyList())) }
        }
    }
}
