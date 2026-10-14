package com.calmlauncher.service.navigation

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import com.calmlauncher.R

/**
 * Quick switch for the bottom-edge sideways swipe, like stock Android and HyperOS:
 * swipe right along the bottom = previous app, keep swiping right to go further back, swipe
 * left to go forward again. After a short pause the order is "committed" and the app you
 * landed on becomes the most recent one.
 *
 * The order comes from the foreground-app events the focus helper already receives (package
 * names only, kept in memory, never stored).
 */
class AppSwitcher(private val context: Context) {
    private val recent = ArrayList<String>() // most recent first
    private var foreground: String? = null
    private var session: List<String>? = null
    private var index = 0
    private var lastSwitchAt = 0L
    private val homes: Set<String> by lazy { homePackages() }
    private val ignored: Set<String> by lazy { ignoredPackages() }

    fun onForeground(pkg: String) {
        if (pkg in homes) {
            foreground = pkg
            return
        }
        // Keyboards, the notification shade and system dialogs don't change the current app.
        if (pkg in ignored) return
        if (context.packageManager.getLaunchIntentForPackage(pkg) == null) return
        foreground = pkg
        if (sessionActive()) return
        recent.remove(pkg)
        recent.add(0, pkg)
        if (recent.size > MAX_RECENT) recent.removeAt(recent.lastIndex)
    }

    /** [previous] = swipe right (back in time), otherwise swipe left (forward). */
    fun switch(previous: Boolean): Boolean {
        if (!sessionActive()) {
            val fg = foreground
            // From home (or the launcher itself), "previous" is simply the last app used.
            val fromApp = fg != null && recent.firstOrNull() == fg
            session = recent.toList()
            index = if (fromApp) 0 else -1
        }
        val list = session ?: return false
        val target = if (previous) index + 1 else index - 1
        if (target < 0 || target >= list.size) return false
        index = target
        lastSwitchAt = SystemClock.uptimeMillis()
        return launch(list[target], previous)
    }

    private fun sessionActive(): Boolean {
        if (session == null) return false
        if (SystemClock.uptimeMillis() - lastSwitchAt > SESSION_MS) {
            // Commit: the app we ended on becomes the most recent.
            val landed = session?.getOrNull(index)
            session = null
            if (landed != null) {
                recent.remove(landed)
                recent.add(0, landed)
            }
            return false
        }
        return true
    }

    private fun launch(pkg: String, previous: Boolean): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options = if (previous) {
            ActivityOptions.makeCustomAnimation(context, R.anim.nav_enter_from_left, R.anim.nav_exit_to_right)
        } else {
            ActivityOptions.makeCustomAnimation(context, R.anim.nav_enter_from_right, R.anim.nav_exit_to_left)
        }
        return runCatching { context.startActivity(intent, options.toBundle()) }.isSuccess
    }

    private fun homePackages(): Set<String> {
        val set = mutableSetOf(context.packageName)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching { context.packageManager.queryIntentActivities(home, 0) }.getOrNull()
            ?.forEach { set += it.activityInfo.packageName }
        return set
    }

    private fun ignoredPackages(): Set<String> {
        val set = mutableSetOf("android", "com.android.systemui")
        runCatching { context.getSystemService(InputMethodManager::class.java)?.inputMethodList }.getOrNull()
            ?.forEach { set += it.packageName }
        return set
    }

    private companion object {
        const val MAX_RECENT = 20
        const val SESSION_MS = 2_500L
    }
}
