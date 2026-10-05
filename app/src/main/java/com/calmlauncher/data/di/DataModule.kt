package com.calmlauncher.data.di

import android.content.Context
import androidx.room.Room
import com.calmlauncher.data.apps.AppSource
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.apps.IconCache
import com.calmlauncher.data.backup.BackupRepository
import com.calmlauncher.data.db.CalmDatabase
import com.calmlauncher.data.db.Migrations
import com.calmlauncher.data.rules.ExemptPackages
import com.calmlauncher.data.rules.PassStore
import com.calmlauncher.data.rules.PolicyEvaluator
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.security.SecretStore
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.data.tools.LaunchLogRepository
import com.calmlauncher.data.tools.NotificationRepository
import com.calmlauncher.data.tools.ToolsRepository
import com.calmlauncher.data.usage.UsageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIo(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CalmDatabase =
        Room.databaseBuilder(context, CalmDatabase::class.java, CalmDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) =
        SettingsRepository(context, scope)

    @Provides
    @Singleton
    fun provideAppSource(@ApplicationContext context: Context) = AppSource(context)

    @Provides
    @Singleton
    fun provideApps(
        @ApplicationContext context: Context,
        source: AppSource,
        db: CalmDatabase,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher io: CoroutineDispatcher,
    ) = AppsRepository(context, source, db.appCacheDao(), db.appMetaDao(), scope, io)

    @Provides
    @Singleton
    fun provideIcons(source: AppSource, @IoDispatcher io: CoroutineDispatcher) = IconCache(source, io)

    @Provides
    @Singleton
    fun provideRules(db: CalmDatabase, @ApplicationScope scope: CoroutineScope) =
        RulesRepository(db.ruleDao(), db.categoryDao(), db.appMetaDao(), scope)

    @Provides
    @Singleton
    fun provideUsage(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher) =
        UsageRepository(context, io)

    @Provides
    @Singleton
    fun providePasses() = PassStore()

    @Provides
    @Singleton
    fun provideExempt(@ApplicationContext context: Context) = ExemptPackages(context)

    @Provides
    @Singleton
    fun providePolicy(
        settings: SettingsRepository,
        rules: RulesRepository,
        apps: AppsRepository,
        usage: UsageRepository,
        passes: PassStore,
        exempt: ExemptPackages,
    ) = PolicyEvaluator(settings, rules, apps, usage, passes, exempt)

    @Provides
    @Singleton
    fun provideLaunchLog(db: CalmDatabase) = LaunchLogRepository(db.launchLogDao())

    @Provides
    @Singleton
    fun provideTools(db: CalmDatabase) = ToolsRepository(db.toolsDao())

    @Provides
    @Singleton
    fun provideNotifications(db: CalmDatabase, @ApplicationScope scope: CoroutineScope) =
        NotificationRepository(db.notificationDao(), scope)

    @Provides
    @Singleton
    fun provideSecrets(@ApplicationContext context: Context) = SecretStore(context)

    @Provides
    @Singleton
    fun provideBackup(
        @ApplicationContext context: Context,
        db: CalmDatabase,
        settings: SettingsRepository,
        secrets: SecretStore,
        @IoDispatcher io: CoroutineDispatcher,
    ) = BackupRepository(context, db, settings, secrets, io)
}
