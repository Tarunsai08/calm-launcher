package com.calmlauncher.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmlauncher.data.db.AppMetaEntity
import com.calmlauncher.data.db.CalmDatabase
import com.calmlauncher.data.db.DigestItemEntity
import com.calmlauncher.data.db.LimitExtensionEntity
import com.calmlauncher.data.db.Migrations
import com.calmlauncher.data.db.RuleEntity
import com.calmlauncher.data.db.TimeWindowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: CalmDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CalmDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun metadataUpsertAndObserve() = runTest {
        val dao = db.appMetaDao()
        dao.upsert(AppMetaEntity(id = "a/B#0", packageName = "a", alias = "Mail", favoriteOrder = 0))
        dao.upsert(AppMetaEntity(id = "a/B#0", packageName = "a", alias = "Post", favoriteOrder = 1))
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Post", all[0].alias)
    }

    @Test
    fun rulesWindowsAndExtensions() = runTest {
        val dao = db.ruleDao()
        dao.upsertRule(RuleEntity("app:x", pauseEnabled = true, dailyLimitMinutes = 20))
        dao.upsertWindow(TimeWindowEntity(owner = "app:x", daysMask = 31, startMinute = 1320, endMinute = 420))
        dao.upsertExtension(LimitExtensionEntity("app:x", 100, 5))
        assertEquals(20, dao.getRules().single().dailyLimitMinutes)
        assertEquals("app:x", dao.getWindows().single().owner)
        assertEquals(5, dao.getExtension("app:x", 100)?.extraMinutes)
        dao.pruneExtensions(101)
        assertNull(dao.getExtension("app:x", 100))
    }

    @Test
    fun digestItemsReplaceByKey() = runTest {
        val dao = db.notificationDao()
        dao.insertItem(DigestItemEntity(packageName = "p", title = "t", text = "1", postedAt = 1, notificationKey = "k"))
        dao.deleteByKey("k")
        dao.insertItem(DigestItemEntity(packageName = "p", title = "t", text = "2", postedAt = 2, notificationKey = "k"))
        assertEquals(listOf("2"), dao.observeItems().first().map { it.text })
    }
}

/**
 * Creates a version-1 database by hand (exactly as Room created it at schema 1), then opens it
 * with the current schema and MIGRATION_1_2. Room validates every table after migrating, so
 * this fails if the migration doesn't produce the expected schema.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    private val v1 = listOf(
        "CREATE TABLE IF NOT EXISTS `app_cache` (`id` TEXT NOT NULL, `package_name` TEXT NOT NULL, `class_name` TEXT NOT NULL, `user_serial` INTEGER NOT NULL, `label` TEXT NOT NULL, `is_work` INTEGER NOT NULL, `is_clone` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_app_cache_package_name` ON `app_cache` (`package_name`)",
        "CREATE TABLE IF NOT EXISTS `app_meta` (`id` TEXT NOT NULL, `package_name` TEXT NOT NULL, `alias` TEXT, `hidden` INTEGER NOT NULL, `category_id` INTEGER, `favorite_order` INTEGER, `last_launched` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_app_meta_package_name` ON `app_meta` (`package_name`)",
        "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `rules` (`target` TEXT NOT NULL, `pause_enabled` INTEGER NOT NULL, `pause_seconds` INTEGER NOT NULL, `ask_reason` INTEGER NOT NULL, `daily_limit_minutes` INTEGER, `always_blocked` INTEGER NOT NULL, PRIMARY KEY(`target`))",
        "CREATE TABLE IF NOT EXISTS `time_windows` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `owner` TEXT NOT NULL, `name` TEXT NOT NULL, `days_mask` INTEGER NOT NULL, `start_minute` INTEGER NOT NULL, `end_minute` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_time_windows_owner` ON `time_windows` (`owner`)",
        "CREATE TABLE IF NOT EXISTS `limit_extensions` (`target` TEXT NOT NULL, `day` INTEGER NOT NULL, `extra_minutes` INTEGER NOT NULL, `unlimited` INTEGER NOT NULL, PRIMARY KEY(`target`, `day`))",
        "CREATE TABLE IF NOT EXISTS `launch_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `package_name` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `reason` TEXT, `note` TEXT)",
        "CREATE INDEX IF NOT EXISTS `index_launch_log_timestamp` ON `launch_log` (`timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_launch_log_package_name` ON `launch_log` (`package_name`)",
        "CREATE TABLE IF NOT EXISTS `notification_rules` (`package_name` TEXT NOT NULL, `mode` TEXT NOT NULL, PRIMARY KEY(`package_name`))",
        "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `updated_at` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `done` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL)",
    )

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @Test
    fun migrate1To2KeepsDataAndAddsColumns() = runTest {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        v1.forEach { db.execSQL(it) }
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO app_meta (id, package_name, alias, hidden, category_id, favorite_order, last_launched) VALUES ('a/B#0', 'a', 'Mail', 0, NULL, 0, 5)",
        )
        helper.close()

        val db = Room.databaseBuilder(context, CalmDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        val meta = db.appMetaDao().getAll().single()
        assertEquals("Mail", meta.alias)
        assertEquals(0, meta.launchCount)
        assertEquals(0, db.notificationDao().countItems())
        db.close()
    }
}
