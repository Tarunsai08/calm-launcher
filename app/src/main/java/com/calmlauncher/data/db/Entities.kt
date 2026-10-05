package com.calmlauncher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Last known list of launchable activities, so the first frame never waits for PackageManager. */
@Entity(tableName = "app_cache", indices = [Index("package_name")])
data class AppCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "class_name") val className: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    val label: String,
    @ColumnInfo(name = "is_work") val isWork: Boolean,
    @ColumnInfo(name = "is_clone") val isClone: Boolean,
)

/** User metadata per app key. Survives app updates; cleaned up when the app is uninstalled. */
@Entity(tableName = "app_meta", indices = [Index("package_name")])
data class AppMetaEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    val alias: String? = null,
    val hidden: Boolean = false,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "favorite_order") val favoriteOrder: Int? = null,
    @ColumnInfo(name = "last_launched") val lastLaunched: Long = 0L,
    @ColumnInfo(name = "launch_count", defaultValue = "0") val launchCount: Int = 0,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

/** Focus rule for an app ("app:<pkg>") or a group ("group:<categoryId>"). */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val target: String,
    @ColumnInfo(name = "pause_enabled") val pauseEnabled: Boolean = false,
    @ColumnInfo(name = "pause_seconds") val pauseSeconds: Int = 5,
    @ColumnInfo(name = "ask_reason") val askReason: Boolean = true,
    @ColumnInfo(name = "daily_limit_minutes") val dailyLimitMinutes: Int? = null,
    @ColumnInfo(name = "always_blocked") val alwaysBlocked: Boolean = false,
)

/** Weekly time windows. owner = rule target for block windows, or "focus" for focus schedules. */
@Entity(tableName = "time_windows", indices = [Index("owner")])
data class TimeWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val owner: String,
    val name: String = "",
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int,
    val enabled: Boolean = true,
) {
    companion object {
        const val OWNER_FOCUS = "focus"
    }
}

/** Per-day extensions granted from the limit screen. day = LocalDate.toEpochDay(). */
@Entity(tableName = "limit_extensions", primaryKeys = ["target", "day"])
data class LimitExtensionEntity(
    val target: String,
    val day: Long,
    @ColumnInfo(name = "extra_minutes") val extraMinutes: Int = 0,
    val unlimited: Boolean = false,
)

/** Local record of launches through the gate, used only for on-device insights. */
@Entity(tableName = "launch_log", indices = [Index("timestamp"), Index("package_name")])
data class LaunchLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    val timestamp: Long,
    val outcome: String,
    val reason: String? = null,
    val note: String? = null,
) {
    companion object {
        const val LAUNCHED = "launched"
        const val PAUSE_CONTINUED = "pause_continued"
        const val PAUSE_CANCELLED = "pause_cancelled"
        const val BLOCK_CLOSED = "block_closed"
        const val BLOCK_EXTENDED = "block_extended"
        const val BLOCK_OVERRIDDEN = "block_overridden"
    }
}

@Entity(tableName = "notification_rules")
data class NotificationRuleEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    /** "allow", "silence" or "digest". */
    val mode: String,
) {
    companion object {
        const val ALLOW = "allow"
        const val SILENCE = "silence"
        const val DIGEST = "digest"
    }
}

@Entity(tableName = "digest_items", indices = [Index("posted_at")])
data class DigestItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    val title: String,
    val text: String,
    @ColumnInfo(name = "posted_at") val postedAt: Long,
    @ColumnInfo(name = "notification_key") val notificationKey: String,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val done: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
