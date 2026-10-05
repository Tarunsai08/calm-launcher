package com.calmlauncher.ui

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val HIDDEN_APPS = "hidden_apps"
    const val CATEGORIES = "categories"
    const val FAVORITES = "favorites"
    const val FOCUS = "focus"
    const val RULES = "rules"
    const val RULES_PATTERN = "rules/{target}"
    const val INSIGHTS = "insights"
    const val NOTES = "notes"
    const val TODOS = "todos"
    const val DIGEST = "digest"
    const val NOTIFICATION_RULES = "notification_rules"
    const val OEM_HELP = "oem_help"
    const val PERMISSION_EXPLAINER_PATTERN = "explain/{kind}"
    const val ABOUT = "about"

    fun rules(target: String) = "rules/" + Uri.encode(target)

    fun explain(kind: String) = "explain/$kind"

    fun settings(section: String) = "$SETTINGS?section=$section"
    const val SETTINGS_PATTERN = "$SETTINGS?section={section}"
}

/** Kinds for the permission explanation screen. */
object ExplainKind {
    const val ACCESSIBILITY = "accessibility"
    const val NOTIFICATIONS = "notifications"
    const val USAGE = "usage"
    const val OVERLAY = "overlay"
}
