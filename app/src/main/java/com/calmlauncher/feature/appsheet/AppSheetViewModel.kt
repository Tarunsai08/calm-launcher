package com.calmlauncher.feature.appsheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calmlauncher.R
import com.calmlauncher.data.db.CategoryEntity
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.tools.NotificationMode
import com.calmlauncher.data.tools.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

fun NotificationMode.labelRes(): Int = when (this) {
    NotificationMode.ALLOW -> R.string.notif_mode_allow
    NotificationMode.SILENCE -> R.string.notif_mode_silence
    NotificationMode.DIGEST -> R.string.notif_mode_digest
}

@HiltViewModel
class AppSheetViewModel @Inject constructor(
    rules: RulesRepository,
    private val notifications: NotificationRepository,
) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = rules.categories
    val notificationModes: StateFlow<Map<String, NotificationMode>> = notifications.rules

    fun cycleNotificationMode(packageName: String) {
        val current = notifications.rules.value[packageName] ?: NotificationMode.ALLOW
        val next = when (current) {
            NotificationMode.ALLOW -> NotificationMode.DIGEST
            NotificationMode.DIGEST -> NotificationMode.SILENCE
            NotificationMode.SILENCE -> NotificationMode.ALLOW
        }
        viewModelScope.launch { notifications.setMode(packageName, next) }
    }
}
