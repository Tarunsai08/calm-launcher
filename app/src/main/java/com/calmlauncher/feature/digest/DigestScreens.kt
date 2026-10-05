package com.calmlauncher.feature.digest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmDivider
import com.calmlauncher.core.designsystem.CalmPage
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.EmptyState
import com.calmlauncher.core.designsystem.SectionHeader
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.core.designsystem.ToggleRow
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.db.DigestItemEntity
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.data.tools.NotificationMode
import com.calmlauncher.data.tools.NotificationRepository
import com.calmlauncher.domain.focus.TimeWindow
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.feature.appsheet.labelRes
import com.calmlauncher.service.AppLauncher
import com.calmlauncher.service.CalmNotificationListener
import com.calmlauncher.service.Scheduler
import com.calmlauncher.ui.ExplainKind
import com.calmlauncher.ui.Routes
import com.calmlauncher.ui.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DigestViewModel @Inject constructor(
    private val notifications: NotificationRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: Scheduler,
    val launcher: AppLauncher,
    apps: AppsRepository,
) : ViewModel() {
    val items: StateFlow<List<DigestItemEntity>> =
        notifications.digestItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules: StateFlow<Map<String, NotificationMode>> = notifications.rules
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
    val apps: StateFlow<List<LauncherApp>> = apps.apps

    fun listenerConnected(): Boolean = CalmNotificationListener.connected

    fun dismiss(id: Long) = viewModelScope.launch { notifications.dismiss(id) }
    fun clearAll() = viewModelScope.launch { notifications.clearDigest() }
    fun setMode(pkg: String, mode: NotificationMode) = viewModelScope.launch { notifications.setMode(pkg, mode) }

    fun setDigestEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(digestEnabled = enabled) }
        scheduler.rescheduleAll()
    }

    fun setDigestTimes(times: Set<Int>) = viewModelScope.launch {
        settingsRepository.update { it.copy(digestTimes = times) }
        scheduler.rescheduleAll()
    }
}

@Composable
fun DigestScreen(
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    vm: DigestViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val formatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    CalmPage(
        title = stringResource(R.string.digest_title),
        onBack = onBack,
        actions = {
            if (items.isNotEmpty()) CalmTextButton(stringResource(R.string.digest_clear_all), onClick = { vm.clearAll() })
        },
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            if (!vm.listenerConnected()) {
                item {
                    SettingRow(
                        title = stringResource(R.string.digest_needs_access),
                        description = stringResource(R.string.digest_needs_access_desc),
                        onClick = { navigate(Routes.explain(ExplainKind.NOTIFICATIONS)) },
                    )
                }
            }
            item {
                SettingRow(
                    title = stringResource(R.string.digest_rules),
                    description = stringResource(R.string.digest_rules_desc),
                    onClick = { navigate(Routes.NOTIFICATION_RULES) },
                )
                CalmDivider()
            }
            if (items.isEmpty()) item { EmptyState(stringResource(R.string.digest_empty)) }
            val grouped = items.groupBy { it.packageName }
            for ((pkg, group) in grouped) {
                item(key = "h$pkg") {
                    SectionHeader(apps.firstOrNull { it.key.packageName == pkg }?.label ?: pkg)
                }
                items(group, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (item.title.isNotBlank()) Text(item.title, style = CalmTheme.type.label, color = CalmTheme.colors.text)
                            if (item.text.isNotBlank()) Text(item.text, style = CalmTheme.type.body, color = CalmTheme.colors.text, maxLines = 6)
                            Text(formatter.format(Date(item.postedAt)), style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
                        }
                        CalmTextButton(stringResource(R.string.digest_dismiss), onClick = { vm.dismiss(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationRulesScreen(
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    vm: DigestViewModel = hiltViewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val use24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    var addingTime by rememberSaveable { mutableStateOf(false) }
    val distinctApps = remember(apps) { apps.filter { !it.hidden }.distinctBy { it.key.packageName } }

    CalmPage(title = stringResource(R.string.digest_rules), onBack = onBack) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                if (!vm.listenerConnected()) {
                    SettingRow(
                        title = stringResource(R.string.digest_needs_access),
                        description = stringResource(R.string.digest_needs_access_desc),
                        onClick = { navigate(Routes.explain(ExplainKind.NOTIFICATIONS)) },
                    )
                }
                ToggleRow(
                    title = stringResource(R.string.digest_deliver),
                    description = stringResource(R.string.digest_deliver_desc),
                    checked = settings.digestEnabled,
                    onCheckedChange = { vm.setDigestEnabled(it) },
                )
                SettingRow(
                    title = stringResource(R.string.digest_times),
                    value = settings.digestTimes.sorted().joinToString(", ") { TimeWindow.formatMinute(it, use24) }
                        .ifBlank { stringResource(R.string.digest_times_none) },
                    description = stringResource(R.string.digest_times_desc),
                    onClick = { addingTime = true },
                )
                if (settings.digestTimes.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = Spacing.xs)) {
                        for (t in settings.digestTimes.sorted()) {
                            CalmTextButton(
                                stringResource(R.string.digest_remove_time, TimeWindow.formatMinute(t, use24)),
                                onClick = { vm.setDigestTimes(settings.digestTimes - t) },
                            )
                        }
                    }
                }
                SectionHeader(stringResource(R.string.digest_per_app))
            }
            items(distinctApps, key = { it.key.id }) { app ->
                val mode = rules[app.key.packageName] ?: NotificationMode.ALLOW
                SettingRow(
                    title = app.label,
                    value = stringResource(mode.labelRes()),
                    onClick = {
                        val next = when (mode) {
                            NotificationMode.ALLOW -> NotificationMode.DIGEST
                            NotificationMode.DIGEST -> NotificationMode.SILENCE
                            NotificationMode.SILENCE -> NotificationMode.ALLOW
                        }
                        vm.setMode(app.key.packageName, next)
                    },
                )
            }
        }
    }

    if (addingTime) {
        TextInputDialog(
            title = stringResource(R.string.digest_add_time),
            initial = "18:00",
            supporting = stringResource(R.string.digest_add_time_hint),
            validator = { parseTime(it) != null },
            onDismiss = { addingTime = false },
            onConfirm = { text ->
                parseTime(text)?.let { vm.setDigestTimes(settings.digestTimes + it) }
                addingTime = false
            },
        )
    }
}

/** Parses "HH:mm" (24h) into minutes of day. */
fun parseTime(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}
