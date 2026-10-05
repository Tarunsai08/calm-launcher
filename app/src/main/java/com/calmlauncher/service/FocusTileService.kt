package com.calmlauncher.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.calmlauncher.MainActivity
import com.calmlauncher.R
import com.calmlauncher.data.di.ApplicationScope
import com.calmlauncher.data.rules.PolicyEvaluator
import com.calmlauncher.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick Settings tile to start/stop focus mode. Strict mode sends the user to the in-app exit flow. */
@AndroidEntryPoint
class FocusTileService : TileService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var policy: PolicyEvaluator
    @Inject lateinit var scheduler: Scheduler
    @Inject @field:ApplicationScope lateinit var scope: CoroutineScope

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val active = policy.isFocusActive()
        val s = settings.settings.value
        if (active && (s.focusStrict || !s.focusManualActive)) {
            // Strict mode or a schedule: ask in the app, where the exit challenge lives.
            openApp()
            return
        }
        scope.launch {
            settings.update { it.copy(focusManualActive = !active, focusManualUntil = 0L) }
            scheduler.rescheduleAll()
            render()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val active = policy.isFocusActive()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_focus)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (active) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, FocusTileService::class.java))
            }
        }
    }
}
