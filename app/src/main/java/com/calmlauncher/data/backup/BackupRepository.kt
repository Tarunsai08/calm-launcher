package com.calmlauncher.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.calmlauncher.data.db.AppMetaEntity
import com.calmlauncher.data.db.CalmDatabase
import com.calmlauncher.data.db.CategoryEntity
import com.calmlauncher.data.db.NoteEntity
import com.calmlauncher.data.db.NotificationRuleEntity
import com.calmlauncher.data.db.RuleEntity
import com.calmlauncher.data.db.TimeWindowEntity
import com.calmlauncher.data.db.TodoEntity
import com.calmlauncher.data.security.SecretStore
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.domain.backup.AppMetaBackup
import com.calmlauncher.domain.backup.BackupCodec
import com.calmlauncher.domain.backup.BackupDocument
import com.calmlauncher.domain.backup.CategoryBackup
import com.calmlauncher.domain.backup.NoteBackup
import com.calmlauncher.domain.backup.NotificationRuleBackup
import com.calmlauncher.domain.backup.RuleBackup
import com.calmlauncher.domain.backup.TodoBackup
import com.calmlauncher.domain.backup.WindowBackup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class ImportSummary(val apps: Int, val rules: Int, val schedules: Int)

/** Export/import of everything the user configured, as a local JSON file chosen via SAF. */
class BackupRepository(
    private val context: Context,
    private val db: CalmDatabase,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val io: CoroutineDispatcher,
) {
    suspend fun buildDocument(includeTools: Boolean = true): BackupDocument = withContext(io) {
        val windows = db.ruleDao().getWindows()
        val byOwner = windows.groupBy { it.owner }
        val rules = db.ruleDao().getRules()
        val ruleTargets = (rules.map { it.target } + byOwner.keys.filter { it != TimeWindowEntity.OWNER_FOCUS }).toSet()
        BackupDocument(
            exportedAt = System.currentTimeMillis(),
            settings = settings.toJsonMap(settings.settings.value),
            apps = db.appMetaDao().getAll().map {
                AppMetaBackup(it.id, it.alias, it.hidden, it.categoryId, it.favoriteOrder)
            },
            categories = db.categoryDao().getAll().map { CategoryBackup(it.id, it.name, it.sortOrder) },
            rules = ruleTargets.map { target ->
                val r = rules.firstOrNull { it.target == target }
                RuleBackup(
                    target = target,
                    pauseEnabled = r?.pauseEnabled ?: false,
                    pauseSeconds = r?.pauseSeconds ?: 5,
                    askReason = r?.askReason ?: true,
                    dailyLimitMinutes = r?.dailyLimitMinutes,
                    alwaysBlocked = r?.alwaysBlocked ?: false,
                    blockWindows = byOwner[target].orEmpty().map { it.toBackup() },
                )
            },
            focusSchedules = byOwner[TimeWindowEntity.OWNER_FOCUS].orEmpty().map { it.toBackup() },
            notificationRules = db.notificationDao().getRules().map { NotificationRuleBackup(it.packageName, it.mode) },
            notes = if (includeTools) db.toolsDao().getNotes().map { NoteBackup(it.text, it.updatedAt) } else emptyList(),
            todos = if (includeTools) {
                db.toolsDao().getTodos().map { TodoBackup(it.text, it.done, it.createdAt, it.sortOrder) }
            } else {
                emptyList()
            },
        )
    }

    suspend fun exportTo(uri: Uri, includeTools: Boolean = true) = withContext(io) {
        val text = BackupCodec.encode(buildDocument(includeTools))
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Could not open the selected file")
        stream.use { it.write(text.encodeToByteArray()) }
    }

    suspend fun importFrom(uri: Uri): ImportSummary = withContext(io) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Could not open the selected file")
        val text = stream.use { it.readBytes().decodeToString() }
        restore(BackupCodec.decode(text))
    }

    /** Replaces rules/metadata with the backup's contents. Notes and todos are merged (appended). */
    suspend fun restore(doc: BackupDocument): ImportSummary = withContext(io) {
        db.withTransaction {
            val meta = db.appMetaDao()
            meta.clear()
            meta.upsertAll(
                doc.apps.map {
                    AppMetaEntity(
                        id = it.id,
                        packageName = it.id.substringBefore('/'),
                        alias = it.alias,
                        hidden = it.hidden,
                        categoryId = it.categoryId,
                        favoriteOrder = it.favoriteOrder,
                    )
                },
            )
            db.categoryDao().clear()
            db.categoryDao().upsertAll(doc.categories.map { CategoryEntity(it.id, it.name, it.sortOrder) })
            val ruleDao = db.ruleDao()
            ruleDao.clearRules()
            ruleDao.clearWindows()
            ruleDao.upsertRules(
                doc.rules.map {
                    RuleEntity(it.target, it.pauseEnabled, it.pauseSeconds, it.askReason, it.dailyLimitMinutes, it.alwaysBlocked)
                },
            )
            val windows = doc.rules.flatMap { rule -> rule.blockWindows.map { it.toEntity(rule.target) } } +
                doc.focusSchedules.map { it.toEntity(TimeWindowEntity.OWNER_FOCUS) }
            if (windows.isNotEmpty()) ruleDao.insertWindows(windows)
            val notif = db.notificationDao()
            notif.clearRules()
            notif.upsertRules(doc.notificationRules.map { NotificationRuleEntity(it.packageName, it.mode) })
            if (doc.notes.isNotEmpty()) db.toolsDao().insertNotes(doc.notes.map { NoteEntity(text = it.text, updatedAt = it.updatedAt) })
            if (doc.todos.isNotEmpty()) {
                db.toolsDao().insertTodos(
                    doc.todos.map { TodoEntity(text = it.text, done = it.done, createdAt = it.createdAt, sortOrder = it.sortOrder) },
                )
            }
        }
        settings.importJsonMap(doc.settings)
        ImportSummary(doc.apps.size, doc.rules.size, doc.focusSchedules.size)
    }

    /** "Delete all data": wipes the database, settings, and the PIN. */
    suspend fun deleteEverything() = withContext(io) {
        db.clearAllTables()
        secrets.clear()
        settings.resetToDefaults(keepOnboarding = false)
    }

    private fun TimeWindowEntity.toBackup() = WindowBackup(daysMask, startMinute, endMinute, enabled, name)

    private fun WindowBackup.toEntity(owner: String) = TimeWindowEntity(
        owner = owner,
        name = name,
        daysMask = daysMask,
        startMinute = startMinute.coerceIn(0, 1439),
        endMinute = endMinute.coerceIn(0, 1439),
        enabled = enabled,
    )
}
