package com.calmlauncher.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppCacheEntity::class,
        AppMetaEntity::class,
        CategoryEntity::class,
        RuleEntity::class,
        TimeWindowEntity::class,
        LimitExtensionEntity::class,
        LaunchLogEntity::class,
        NotificationRuleEntity::class,
        DigestItemEntity::class,
        NoteEntity::class,
        TodoEntity::class,
    ],
    version = CalmDatabase.VERSION,
    exportSchema = true,
)
abstract class CalmDatabase : RoomDatabase() {
    abstract fun appCacheDao(): AppCacheDao
    abstract fun appMetaDao(): AppMetaDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun launchLogDao(): LaunchLogDao
    abstract fun notificationDao(): NotificationDao
    abstract fun toolsDao(): ToolsDao

    companion object {
        const val VERSION = 2
        const val NAME = "calm.db"
    }
}

/**
 * Schema history:
 * 1 – apps, categories, rules, windows, limits, launch log, notification rules, notes, todos.
 * 2 – adds `app_meta.launch_count` (frequency ranking) and the `digest_items` table.
 */
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `app_meta` ADD COLUMN `launch_count` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `digest_items` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`package_name` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`posted_at` INTEGER NOT NULL, " +
                    "`notification_key` TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_digest_items_posted_at` ON `digest_items` (`posted_at`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
