package com.calmlauncher.data.tools

import com.calmlauncher.data.db.DigestItemEntity
import com.calmlauncher.data.db.LaunchLogDao
import com.calmlauncher.data.db.LaunchLogEntity
import com.calmlauncher.data.db.NoteEntity
import com.calmlauncher.data.db.NotificationDao
import com.calmlauncher.data.db.NotificationRuleEntity
import com.calmlauncher.data.db.TodoEntity
import com.calmlauncher.data.db.ToolsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LaunchLogRepository(private val dao: LaunchLogDao) {
    suspend fun log(packageName: String, outcome: String, reason: String? = null, note: String? = null) {
        dao.insert(
            LaunchLogEntity(
                packageName = packageName,
                timestamp = System.currentTimeMillis(),
                outcome = outcome,
                reason = reason?.take(MAX_TEXT),
                note = note?.trim()?.take(MAX_TEXT)?.takeIf { it.isNotEmpty() },
            ),
        )
        // Keep 90 days of history; this data is only for the on-device insights screen.
        dao.prune(System.currentTimeMillis() - RETENTION_MS)
    }

    suspend fun outcomeCounts(from: Long, to: Long): Map<String, Int> =
        dao.countOutcomes(from, to).associate { it.outcome to it.count }

    suspend fun recentNotes(limit: Int = 20): List<LaunchLogEntity> = dao.recentNotes(limit)

    companion object {
        private const val MAX_TEXT = 500
        private const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }
}

class ToolsRepository(private val dao: ToolsDao) {
    val notes: Flow<List<NoteEntity>> = dao.observeNotes()
    val todos: Flow<List<TodoEntity>> = dao.observeTodos()

    suspend fun saveNote(id: Long, text: String): Long {
        val trimmed = text.trimEnd()
        if (trimmed.isBlank()) {
            if (id != 0L) dao.deleteNote(id)
            return 0L
        }
        val result = dao.upsertNote(NoteEntity(id = id, text = trimmed, updatedAt = System.currentTimeMillis()))
        return if (id != 0L) id else result
    }

    suspend fun deleteNote(id: Long) = dao.deleteNote(id)

    suspend fun addTodo(text: String) {
        if (text.isBlank()) return
        dao.upsertTodo(TodoEntity(text = text.trim(), createdAt = System.currentTimeMillis()))
    }

    suspend fun setTodoDone(todo: TodoEntity, done: Boolean) = dao.upsertTodo(todo.copy(done = done))

    suspend fun deleteTodo(id: Long) = dao.deleteTodo(id)

    suspend fun clearDone() = dao.clearDoneTodos()
}

enum class NotificationMode(val storage: String) {
    ALLOW(NotificationRuleEntity.ALLOW),
    SILENCE(NotificationRuleEntity.SILENCE),
    DIGEST(NotificationRuleEntity.DIGEST),
    ;

    companion object {
        fun from(value: String?): NotificationMode = entries.firstOrNull { it.storage == value } ?: ALLOW
    }
}

class NotificationRepository(
    private val dao: NotificationDao,
    scope: CoroutineScope,
) {
    /** Package -> mode. Packages without an entry are allowed. Hot for the listener service. */
    val rules: StateFlow<Map<String, NotificationMode>> = dao.observeRules()
        .map { list -> list.associate { it.packageName to NotificationMode.from(it.mode) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val digestItems: Flow<List<DigestItemEntity>> = dao.observeItems()

    suspend fun setMode(packageName: String, mode: NotificationMode) {
        if (mode == NotificationMode.ALLOW) {
            dao.deleteRule(packageName)
        } else {
            dao.upsertRule(NotificationRuleEntity(packageName, mode.storage))
        }
    }

    suspend fun addToDigest(packageName: String, title: String, text: String, postedAt: Long, key: String) {
        dao.deleteByKey(key) // An updated notification replaces its earlier version.
        dao.insertItem(
            DigestItemEntity(
                packageName = packageName,
                title = title.take(MAX_TITLE),
                text = text.take(MAX_TEXT),
                postedAt = postedAt,
                notificationKey = key,
            ),
        )
    }

    suspend fun pendingCount(): Int = dao.countItems()

    suspend fun dismiss(id: Long) = dao.deleteItem(id)

    suspend fun clearDigest() = dao.clearItems()

    companion object {
        private const val MAX_TITLE = 200
        private const val MAX_TEXT = 1000
    }
}
