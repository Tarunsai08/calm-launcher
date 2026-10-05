package com.calmlauncher.service

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.di.ApplicationScope
import com.calmlauncher.data.rules.PolicyEvaluator
import com.calmlauncher.domain.focus.LaunchDecision
import com.calmlauncher.domain.model.AppKey
import com.calmlauncher.feature.gate.GateActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one entry point for "open this app" from inside the launcher. Consults the focus
 * policy and either launches directly or shows the calm gate (pause / block) first.
 */
@Singleton
class LaunchController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val launcher: AppLauncher,
    private val policy: PolicyEvaluator,
    private val apps: AppsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun launch(key: AppKey, bounds: Rect? = null): LaunchDecision {
        val decision = policy.decide(key.packageName)
        when (decision) {
            is LaunchDecision.Allow -> launchNow(key, bounds)
            is LaunchDecision.Pause, is LaunchDecision.Block -> showGate(key, external = false)
        }
        return decision
    }

    /** Launch without policy checks: used after the user has passed the gate. */
    fun launchNow(key: AppKey, bounds: Rect? = null) {
        if (launcher.launchApp(key, bounds)) {
            scope.launch { apps.recordLaunch(key) }
        }
    }

    fun showGate(key: AppKey, external: Boolean) {
        val intent = GateActivity.intent(context, key, external)
        launcher.start(intent)
    }

    fun showGateForPackage(packageName: String, external: Boolean) {
        val app = apps.findByPackage(packageName)
        val key = app?.key ?: AppKey(packageName, "", 0)
        showGate(key, external)
    }

    fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        launcher.start(home)
    }
}
