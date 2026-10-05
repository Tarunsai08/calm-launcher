package com.calmlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmlauncher.core.designsystem.CalmLauncherTheme
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.ui.LauncherRoot
import com.calmlauncher.ui.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

/** Events the activity forwards into Compose: home button presses and deep links. */
sealed interface ActivityEvent {
    data object HomePressed : ActivityEvent
    data class OpenRoute(val route: String) : ActivityEvent
}

/**
 * The single activity. It is the HOME activity (launchMode=singleTask), so pressing Home while
 * the launcher is already in front arrives here through [onNewIntent].
 *
 * Extends FragmentActivity only because BiometricPrompt requires it.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val events = MutableSharedFlow<ActivityEvent>(extraBufferCapacity = 8)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleDeepLink(intent, isNew = false)
        setContent {
            val loaded by settingsRepository.loaded.collectAsStateWithLifecycle()
            val settings = loaded
            if (settings == null) {
                // First frame while the (tiny) settings file is read: plain background, no flash.
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Transparent))
            } else {
                CalmLauncherTheme(settings) {
                    LauncherRoot(settings = settings, activityEvents = events, initialRoute = pendingRoute)
                }
            }
        }
    }

    private var pendingRoute: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent, isNew = true)
    }

    private fun handleDeepLink(intent: Intent?, isNew: Boolean) {
        val route = when (intent?.action) {
            ACTION_OPEN_DIGEST -> Routes.DIGEST
            ACTION_OPEN_FOCUS -> Routes.FOCUS
            ACTION_OPEN_SETTINGS -> Routes.SETTINGS
            else -> null
        }
        if (route != null) {
            if (isNew) events.tryEmit(ActivityEvent.OpenRoute(route)) else pendingRoute = route
            return
        }
        if (isNew && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            events.tryEmit(ActivityEvent.HomePressed)
        }
    }

    companion object {
        const val ACTION_OPEN_DIGEST = "com.calmlauncher.action.OPEN_DIGEST"
        const val ACTION_OPEN_FOCUS = "com.calmlauncher.action.OPEN_FOCUS"
        const val ACTION_OPEN_SETTINGS = "com.calmlauncher.action.OPEN_SETTINGS"
    }
}
