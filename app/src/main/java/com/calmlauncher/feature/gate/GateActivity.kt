package com.calmlauncher.feature.gate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmlauncher.core.designsystem.CalmLauncherTheme
import com.calmlauncher.domain.model.AppKey
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen, calm interstitial shown before a paused or blocked app opens. A normal activity
 * (not an overlay window), so it never needs the "display over other apps" permission when
 * started from the launcher or the accessibility service.
 */
@AndroidEntryPoint
class GateActivity : ComponentActivity() {

    private val viewModel: GateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(state) {
                if (state is GateState.Done) finish()
            }
            CalmLauncherTheme(settings) {
                GateScreen(viewModel = viewModel, state = state, settings = settings)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A different app was gated while this one was showing: restart with the new target.
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        if (pkg != viewModel.packageName) {
            val key = intent.getStringExtra(EXTRA_KEY)?.let { AppKey.parse(it) } ?: AppKey(pkg, "", 0)
            val fresh = GateActivity.intent(this, key, intent.getBooleanExtra(EXTRA_EXTERNAL, false))
                .putExtra(EXTRA_PACKAGE, pkg)
            finish()
            startActivity(fresh)
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_KEY = "key"
        const val EXTRA_EXTERNAL = "external"

        fun intent(context: Context, key: AppKey, external: Boolean): Intent =
            Intent(context, GateActivity::class.java)
                .putExtra(EXTRA_PACKAGE, key.packageName)
                .putExtra(EXTRA_KEY, key.id)
                .putExtra(EXTRA_EXTERNAL, external)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
    }
}
