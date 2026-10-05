package com.calmlauncher

import android.app.Application
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.apps.IconCache
import com.calmlauncher.data.di.ApplicationScope
import com.calmlauncher.service.Notifier
import com.calmlauncher.service.Scheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CalmApplication : Application() {

    @Inject lateinit var apps: AppsRepository
    @Inject lateinit var icons: IconCache
    @Inject lateinit var scheduler: Scheduler
    @Inject lateinit var notifier: Notifier
    @Inject @field:ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        apps.start()
        registerComponentCallbacks(icons)
        scope.launch {
            notifier.ensureChannels()
            scheduler.rescheduleAll()
        }
    }
}
