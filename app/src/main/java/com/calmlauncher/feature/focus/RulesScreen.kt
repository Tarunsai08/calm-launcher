package com.calmlauncher.feature.focus

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmDivider
import com.calmlauncher.core.designsystem.CalmPage
import com.calmlauncher.core.designsystem.EmptyState
import com.calmlauncher.core.designsystem.RadioRow
import com.calmlauncher.core.designsystem.SectionHeader
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.SliderRow
import com.calmlauncher.core.designsystem.ToggleRow
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.db.CategoryEntity
import com.calmlauncher.data.db.TimeWindowEntity
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.rules.RulesSnapshot
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.data.usage.UsageRepository
import com.calmlauncher.domain.focus.FocusRule
import com.calmlauncher.domain.focus.RuleTarget
import com.calmlauncher.domain.focus.TimeWindow
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.service.Scheduler
import com.calmlauncher.ui.ExplainKind
import com.calmlauncher.ui.Routes
import com.calmlauncher.ui.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val rules: RulesRepository,
    private val scheduler: Scheduler,
    private val usage: UsageRepository,
    settings: SettingsRepository,
    apps: AppsRepository,
) : ViewModel() {
    val targetKey: String = savedState.get<String>("target").orEmpty()
    val target: RuleTarget? = RuleTarget.parse(targetKey)
    val snapshot: StateFlow<RulesSnapshot> = rules.snapshot
    val apps: StateFlow<List<LauncherApp>> = apps.apps
    val categories: StateFlow<List<CategoryEntity>> = rules.categories
    val defaultPause: Int = settings.settings.value.defaultPauseSeconds
    val defaultAskReason: Boolean = settings.settings.value.askReasonByDefault

    fun hasUsageAccess(): Boolean = usage.hasPermission()

    fun current(snapshot: RulesSnapshot): FocusRule {
        val t = target ?: return FocusRule(RuleTarget.App(""))
        return snapshot.ruleFor(t) ?: FocusRule(t, pauseSeconds = defaultPause, askReason = defaultAskReason)
    }

    fun save(rule: FocusRule) {
        viewModelScope.launch {
            rules.saveRule(rule)
            scheduler.rescheduleAll()
        }
    }

    fun saveWindow(id: Long, window: TimeWindow, enabled: Boolean) {
        val t = target ?: return
        viewModelScope.launch {
            // Make sure a rule row exists so the window shows up even with no other settings.
            rules.saveWindow(
                TimeWindowEntity(
                    id = id,
                    owner = t.storageKey,
                    daysMask = window.daysMask,
                    startMinute = window.startMinute,
                    endMinute = window.endMinute,
                    enabled = enabled,
                ),
            )
            scheduler.rescheduleAll()
        }
    }

    fun deleteWindow(id: Long) {
        viewModelScope.launch {
            rules.deleteWindow(id)
            scheduler.rescheduleAll()
        }
    }

    fun clearAll() {
        val t = target ?: return
        viewModelScope.launch {
            rules.clearRule(t)
            scheduler.rescheduleAll()
        }
    }
}

private val LIMIT_CHOICES = listOf(null, 5, 15, 30, 45, 60, 90, 120)

@Composable
fun RulesScreen(
    target: String,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    vm: RulesViewModel = hiltViewModel(),
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val use24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val t = vm.target
    if (t == null || target.isBlank()) {
        CalmPage(title = stringResource(R.string.rules_title), onBack = onBack) { EmptyState(stringResource(R.string.rules_invalid)) }
        return
    }
    val rule = vm.current(snapshot)
    val name = when (t) {
        is RuleTarget.App -> apps.firstOrNull { it.key.packageName == t.packageName }?.label ?: t.packageName
        is RuleTarget.Group -> categories.firstOrNull { it.id == t.categoryId }?.name ?: stringResource(R.string.category_deleted)
    }
    val windows = snapshot.windowsByOwner[vm.targetKey].orEmpty()
    var pauseSeconds by rememberSaveable(rule.pauseSeconds) { mutableFloatStateOf(rule.pauseSeconds.toFloat()) }
    var customLimit by rememberSaveable { mutableStateOf(false) }
    var editingWindow by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingWindow by rememberSaveable { mutableStateOf(false) }

    CalmPage(title = stringResource(R.string.rules_for, name), onBack = onBack) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                SectionHeader(stringResource(R.string.rules_pause))
                ToggleRow(
                    title = stringResource(R.string.rules_pause_toggle),
                    description = stringResource(R.string.rules_pause_desc),
                    checked = rule.pauseEnabled,
                    onCheckedChange = { vm.save(rule.copy(pauseEnabled = it)) },
                )
                if (rule.pauseEnabled) {
                    SliderRow(
                        title = stringResource(R.string.rules_pause_length),
                        description = null,
                        value = pauseSeconds,
                        valueRange = FocusRule.MIN_PAUSE_SECONDS.toFloat()..FocusRule.MAX_PAUSE_SECONDS.toFloat(),
                        steps = FocusRule.MAX_PAUSE_SECONDS - FocusRule.MIN_PAUSE_SECONDS - 1,
                        valueLabel = stringResource(R.string.seconds_short, pauseSeconds.toInt()),
                        onValueChange = { pauseSeconds = it },
                        onValueChangeFinished = { vm.save(rule.copy(pauseSeconds = pauseSeconds.toInt())) },
                    )
                    ToggleRow(
                        title = stringResource(R.string.rules_ask_reason),
                        description = stringResource(R.string.rules_ask_reason_desc),
                        checked = rule.askReason,
                        onCheckedChange = { vm.save(rule.copy(askReason = it)) },
                    )
                }
                CalmDivider()
                SectionHeader(stringResource(R.string.rules_limit))
                if (!vm.hasUsageAccess()) {
                    SettingRow(
                        title = stringResource(R.string.rules_limit_needs_usage),
                        description = stringResource(R.string.rules_limit_needs_usage_desc),
                        onClick = { navigate(Routes.explain(ExplainKind.USAGE)) },
                    )
                }
            }
            items(LIMIT_CHOICES) { minutes ->
                RadioRow(
                    title = if (minutes == null) stringResource(R.string.rules_limit_none) else pluralStringResource(R.plurals.minutes_per_day, minutes, minutes),
                    selected = rule.dailyLimitMinutes == minutes,
                    onSelect = { vm.save(rule.copy(dailyLimitMinutes = minutes)) },
                )
            }
            item {
                val custom = rule.dailyLimitMinutes
                RadioRow(
                    title = stringResource(R.string.rules_limit_custom),
                    description = if (custom != null && custom !in LIMIT_CHOICES) pluralStringResource(R.plurals.minutes_per_day, custom, custom) else null,
                    selected = custom != null && custom !in LIMIT_CHOICES,
                    onSelect = { customLimit = true },
                )
                CalmDivider()
                SectionHeader(stringResource(R.string.rules_block))
                ToggleRow(
                    title = stringResource(R.string.rules_always_block),
                    description = stringResource(R.string.rules_always_block_desc),
                    checked = rule.alwaysBlocked,
                    onCheckedChange = { vm.save(rule.copy(alwaysBlocked = it)) },
                )
            }
            items(windows, key = { "w" + it.id }) { w ->
                val window = runCatching { TimeWindow(w.daysMask, w.startMinute, w.endMinute) }.getOrNull()
                if (window != null) {
                    SettingRow(
                        title = stringResource(R.string.rules_block_window),
                        description = windowSummary(window, use24) + if (!w.enabled) " · " + stringResource(R.string.state_off) else "",
                        onClick = { editingWindow = w.id },
                    )
                }
            }
            item {
                SettingRow(title = stringResource(R.string.rules_add_window), onClick = { creatingWindow = true })
                CalmDivider()
                SettingRow(title = stringResource(R.string.rules_clear), onClick = { vm.clearAll() })
            }
        }
    }

    if (customLimit) {
        TextInputDialog(
            title = stringResource(R.string.rules_limit_custom),
            initial = rule.dailyLimitMinutes?.toString().orEmpty(),
            label = stringResource(R.string.rules_limit_minutes),
            keyboardType = KeyboardType.Number,
            validator = { it.toIntOrNull()?.let { m -> m in 1..1440 } ?: false },
            onDismiss = { customLimit = false },
            onConfirm = {
                vm.save(rule.copy(dailyLimitMinutes = it.toIntOrNull()?.coerceIn(1, 1440)))
                customLimit = false
            },
        )
    }
    if (creatingWindow) {
        WindowEditorDialog(
            title = stringResource(R.string.rules_add_window),
            initial = null,
            showName = false,
            onDismiss = { creatingWindow = false },
            onSave = { _, window, enabled ->
                vm.saveWindow(0, window, enabled)
                creatingWindow = false
            },
        )
    }
    editingWindow?.let { id ->
        val w = windows.firstOrNull { it.id == id }
        val window = w?.let { runCatching { TimeWindow(it.daysMask, it.startMinute, it.endMinute) }.getOrNull() }
        if (w == null || window == null) {
            editingWindow = null
        } else {
            WindowEditorDialog(
                title = stringResource(R.string.rules_block_window),
                initial = window,
                initialEnabled = w.enabled,
                showName = false,
                onDismiss = { editingWindow = null },
                onSave = { _, newWindow, enabled ->
                    vm.saveWindow(id, newWindow, enabled)
                    editingWindow = null
                },
                onDelete = {
                    vm.deleteWindow(id)
                    editingWindow = null
                },
            )
        }
    }
}
