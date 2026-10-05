package com.calmlauncher.data.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import java.io.OutputStream

/** JSON-backed typed DataStore serializer. Unknown keys are ignored; missing keys use defaults. */
object SettingsSerializer : Serializer<LauncherSettings> {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val defaultValue: LauncherSettings = LauncherSettings.DEFAULT

    override suspend fun readFrom(input: InputStream): LauncherSettings {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(LauncherSettings.serializer(), text)
        } catch (e: SerializationException) {
            throw CorruptionException("Settings unreadable", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Settings unreadable", e)
        }
    }

    override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
        output.write(json.encodeToString(LauncherSettings.serializer(), t).encodeToByteArray())
    }
}

class SettingsRepository(
    context: Context,
    scope: CoroutineScope,
) {
    private val store: DataStore<LauncherSettings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { LauncherSettings.DEFAULT },
        // File IO on the IO dispatcher; lifetime tied to the application scope.
        scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
        produceFile = { context.applicationContext.dataStoreFile("launcher_settings.json") },
    )

    val flow: Flow<LauncherSettings> = store.data.catch { emit(LauncherSettings.DEFAULT) }

    /** Hot, always-available settings. Starts with defaults until the file is read (a few ms). */
    val settings: StateFlow<LauncherSettings> = flow.stateIn(scope, SharingStarted.Eagerly, LauncherSettings.DEFAULT)

    /** Null until the settings file has been read once; lets the UI avoid a flash of defaults. */
    val loaded: StateFlow<LauncherSettings?> = flow.stateIn<LauncherSettings?>(scope, SharingStarted.Eagerly, null)

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.updateData { transform(it) }
    }

    suspend fun resetToDefaults(keepOnboarding: Boolean = true) {
        store.updateData { current -> LauncherSettings.DEFAULT.copy(onboardingDone = keepOnboarding && current.onboardingDone) }
    }

    /** Export as a JSON object map for backups. */
    fun toJsonMap(settings: LauncherSettings): Map<String, JsonElement> =
        SettingsSerializer.json.encodeToJsonElement(LauncherSettings.serializer(), settings).jsonObject.toMap()

    /** Import from a backup map; unknown keys ignored, missing keys keep defaults. */
    suspend fun importJsonMap(map: Map<String, JsonElement>) {
        val decoded = SettingsSerializer.json.decodeFromJsonElement(LauncherSettings.serializer(), JsonObject(map))
        store.updateData { decoded.copy(onboardingDone = true) }
    }
}
