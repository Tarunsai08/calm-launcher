package com.calmlauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppCacheDao {
    @Query("SELECT * FROM app_cache")
    abstract suspend fun getAll(): List<AppCacheEntity>

    @Upsert
    abstract suspend fun upsertAll(items: List<AppCacheEntity>)

    @Query("DELETE FROM app_cache WHERE id IN (:ids)")
    abstract suspend fun deleteIds(ids: List<String>)

    @Query("DELETE FROM app_cache WHERE package_name = :packageName AND user_serial = :userSerial")
    abstract suspend fun deletePackage(packageName: String, userSerial: Long)

    @Transaction
    open suspend fun replaceAll(items: List<AppCacheEntity>) {
        val keep = items.map { it.id }.toSet()
        val stale = getAll().map { it.id }.filter { it !in keep }
        if (stale.isNotEmpty()) stale.chunked(500).forEach { deleteIds(it) }
        upsertAll(items)
    }

    @Query("DELETE FROM app_cache")
    abstract suspend fun clear()
}

@Dao
interface AppMetaDao {
    @Query("SELECT * FROM app_meta")
    fun observeAll(): Flow<List<AppMetaEntity>>

    @Query("SELECT * FROM app_meta")
    suspend fun getAll(): List<AppMetaEntity>

    @Query("SELECT * FROM app_meta WHERE id = :id")
    suspend fun get(id: String): AppMetaEntity?

    @Upsert
    suspend fun upsert(item: AppMetaEntity)

    @Upsert
    suspend fun upsertAll(items: List<AppMetaEntity>)

    @Query("DELETE FROM app_meta WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    @Query("UPDATE app_meta SET category_id = NULL WHERE category_id = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("DELETE FROM app_meta")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort_order, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sort_order, name")
    suspend fun getAll(): List<CategoryEntity>

    @Insert
    suspend fun insert(item: CategoryEntity): Long

    @Upsert
    suspend fun upsertAll(items: List<CategoryEntity>)

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    fun observeRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun getRules(): List<RuleEntity>

    @Upsert
    suspend fun upsertRule(rule: RuleEntity)

    @Upsert
    suspend fun upsertRules(rules: List<RuleEntity>)

    @Query("DELETE FROM rules WHERE target = :target")
    suspend fun deleteRule(target: String)

    @Query("SELECT * FROM time_windows ORDER BY start_minute")
    fun observeWindows(): Flow<List<TimeWindowEntity>>

    @Query("SELECT * FROM time_windows ORDER BY start_minute")
    suspend fun getWindows(): List<TimeWindowEntity>

    @Upsert
    suspend fun upsertWindow(window: TimeWindowEntity): Long

    @Insert
    suspend fun insertWindows(windows: List<TimeWindowEntity>)

    @Query("DELETE FROM time_windows WHERE id = :id")
    suspend fun deleteWindow(id: Long)

    @Query("DELETE FROM time_windows WHERE owner = :owner")
    suspend fun deleteWindowsFor(owner: String)

    @Query("SELECT * FROM limit_extensions WHERE day = :day")
    fun observeExtensions(day: Long): Flow<List<LimitExtensionEntity>>

    @Query("SELECT * FROM limit_extensions WHERE day = :day")
    suspend fun getExtensions(day: Long): List<LimitExtensionEntity>

    @Query("SELECT * FROM limit_extensions WHERE target = :target AND day = :day")
    suspend fun getExtension(target: String, day: Long): LimitExtensionEntity?

    @Upsert
    suspend fun upsertExtension(ext: LimitExtensionEntity)

    @Query("DELETE FROM limit_extensions WHERE day < :beforeDay")
    suspend fun pruneExtensions(beforeDay: Long)

    @Query("DELETE FROM rules")
    suspend fun clearRules()

    @Query("DELETE FROM time_windows")
    suspend fun clearWindows()

    @Query("DELETE FROM limit_extensions")
    suspend fun clearExtensions()
}

data class OutcomeCount(val outcome: String, val count: Int)

@Dao
interface LaunchLogDao {
    @Insert
    suspend fun insert(item: LaunchLogEntity)

    @Query("SELECT outcome, COUNT(*) AS count FROM launch_log WHERE timestamp >= :from AND timestamp < :to GROUP BY outcome")
    suspend fun countOutcomes(from: Long, to: Long): List<OutcomeCount>

    @Query("SELECT * FROM launch_log WHERE note IS NOT NULL AND note != '' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentNotes(limit: Int): List<LaunchLogEntity>

    @Query("DELETE FROM launch_log WHERE timestamp < :before")
    suspend fun prune(before: Long)

    @Query("DELETE FROM launch_log")
    suspend fun clear()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_rules")
    fun observeRules(): Flow<List<NotificationRuleEntity>>

    @Query("SELECT * FROM notification_rules")
    suspend fun getRules(): List<NotificationRuleEntity>

    @Upsert
    suspend fun upsertRule(rule: NotificationRuleEntity)

    @Upsert
    suspend fun upsertRules(rules: List<NotificationRuleEntity>)

    @Query("DELETE FROM notification_rules WHERE package_name = :packageName")
    suspend fun deleteRule(packageName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: DigestItemEntity)

    @Query("SELECT * FROM digest_items ORDER BY posted_at DESC")
    fun observeItems(): Flow<List<DigestItemEntity>>

    @Query("SELECT COUNT(*) FROM digest_items")
    suspend fun countItems(): Int

    @Query("DELETE FROM digest_items WHERE notification_key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM digest_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM digest_items")
    suspend fun clearItems()

    @Query("DELETE FROM notification_rules")
    suspend fun clearRules()
}

@Dao
interface ToolsDao {
    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    suspend fun getNotes(): List<NoteEntity>

    @Upsert
    suspend fun upsertNote(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT * FROM todos ORDER BY done, sort_order, created_at")
    fun observeTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos ORDER BY done, sort_order, created_at")
    suspend fun getTodos(): List<TodoEntity>

    @Upsert
    suspend fun upsertTodo(todo: TodoEntity): Long

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteTodo(id: Long)

    @Query("DELETE FROM todos WHERE done = 1")
    suspend fun clearDoneTodos()

    @Insert
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Insert
    suspend fun insertTodos(todos: List<TodoEntity>)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM todos")
    suspend fun clearTodos()
}
