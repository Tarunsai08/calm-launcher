package com.calmlauncher.feature.focus

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmDivider
import com.calmlauncher.core.designsystem.CalmPage
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.RadioRow
import com.calmlauncher.core.designsystem.SectionHeader
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.core.designsystem.ToggleRow
import com.calmlauncher.data.settings.FocusHideMode
import com.calmlauncher.domain.focus.RuleTarget
import com.calmlauncher.feature.gate.FrictionChallenge
import com.calmlauncher.feature.home.rememberNow
import com.calmlauncher.ui.ExplainKind
import com.calmlauncher.ui.Routes
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FocusScreen(
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    vm: FocusViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val now = rememberNow(withSeconds = false)
    val active = remember(now, settings, snapshot) { vm.isActive() }
    val until = remember(now, settings, snapshot) { vm.activeUntil() }
    val paused = remember(now, settings) { vm.rulesPaused() }
    val use24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

    var exitChallenge by rememberSaveable { mutableStateOf(false) }
    var emergencyChallenge by rememberSaveable { mutableStateOf(false) }
    var pickingAllowlist by rememberSaveable { mutableStateOf(false) }
    var editingScheduleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingSchedule by rememberSaveable { mutableStateOf(false) }
    var pickingRuleApp by rememberSaveable { mutableStateOf(false) }

    CalmPage(title = stringResource(R.string.focus_title), onBack = onBack) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                val status = when {
                    paused -> stringResource(R.string.focus_status_paused)
                    active && until != null -> stringResource(
                        R.string.focus_status_on_until,
                        until.format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm a", Locale.getDefault())),
                    )
                    active -> stringResource(R.string.focus_status_on)
                    else -> stringResource(R.string.focus_status_off)
                }
                Text(
                    status,
                    style = CalmTheme.type.body,
                    color = if (active) CalmTheme.colors.accent else CalmTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
                if (active) {
                    if (settings.focusStrict) {
                        SettingRow(
                            title = stringResource(R.string.focus_end_strict),
                            description = stringResource(R.string.focus_end_strict_desc),
                            onClick = { exitChallenge = true },
                        )
                    } else {
                        SettingRow(title = stringResource(R.string.focus_end), onClick = { vm.stopFocus() })
                    }
                    if (exitChallenge) {
                        FrictionChallenge(
                            challenge = stringResource(R.string.focus_exit_phrase),
                            onCancel = { exitChallenge = false },
                            onPassed = {
                                exitChallenge = false
                                vm.stopFocus()
                            },
                        )
                    }
                } else {
                    SectionHeader(stringResource(R.string.focus_start))
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs)) {
                        CalmTextButton(stringResource(R.string.focus_25), onClick = { vm.startFocus(25) })
                        CalmTextButton(stringResource(R.string.focus_45), onClick = { vm.startFocus(45) })
                        CalmTextButton(stringResource(R.string.focus_90), onClick = { vm.startFocus(90) })
                        CalmTextButton(stringResource(R.string.focus_untimed), onClick = { vm.startFocus(null) })
                    }
                }
                if (paused) {
                    SettingRow(title = stringResource(R.string.focus_resume_rules), onClick = { vm.resumeRules() })
                }
            }

            item {
                SectionHeader(stringResource(R.string.focus_allowed_apps))
                val names = settings.focusAllowlist.map { pkg -> apps.firstOrNull { it.key.packageName == pkg }?.label ?: pkg }.sorted()
                SettingRow(
                    title = stringResource(R.string.focus_allowlist),
                    description = stringResource(R.string.focus_allowlist_desc),
                    value = if (names.isEmpty()) stringResource(R.string.focus_allowlist_empty) else names.joinToString(", "),
                    onClick = { pickingAllowlist = true },
                )
                ToggleRow(
                    title = stringResource(R.string.focus_strict),
                    description = stringResource(R.string.focus_strict_desc),
                    checked = settings.focusStrict,
                    onCheckedChange = { vm.setStrict(it) },
                    enabled = !active || !settings.focusStrict,
                )
                SectionHeader(stringResource(R.string.focus_other_apps))
                RadioRow(
                    stringResource(R.string.focus_dim),
                    settings.focusHideMode == FocusHideMode.DIM,
                    onSelect = { vm.setHideMode(FocusHideMode.DIM) },
                )
                RadioRow(
                    stringResource(R.string.focus_hide),
                    settings.focusHideMode == FocusHideMode.HIDE,
                    onSelect = { vm.setHideMode(FocusHideMode.HIDE) },
                )
            }

            item { SectionHeader(stringResource(R.string.focus_schedules)) }
            items(snapshot.focusSchedules, key = { "s" + it.id }) { schedule ->
                SettingRow(
                    title = schedule.name.ifBlank { stringResource(R.string.focus_schedule_unnamed) },
                    description = windowSummary(schedule.window, use24) +
                        if (!schedule.enabled) " · " + stringResource(R.string.state_off) else "",
                    onClick = { editingScheduleId = schedule.id },
                )
            }
            item {
                SettingRow(title = stringResource(R.string.focus_add_schedule), onClick = { creatingSchedule = true })
                CalmDivider()
            }

            item {
                SectionHeader(stringResource(R.string.focus_app_rules))
                Text(
                    stringResource(R.string.focus_app_rules_desc),
                    style = CalmTheme.type.caption,
                    color = CalmTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
            items(vm.ruleTargets(snapshot), key = { it.first.storageKey }) { (target, rule) ->
                val name = when (target) {
                    is RuleTarget.App -> apps.firstOrNull { it.key.packageName == target.packageName }?.label ?: target.packageName
                    is RuleTarget.Group -> categories.firstOrNull { it.id == target.categoryId }?.name
                        ?: stringResource(R.string.category_deleted)
                }
                SettingRow(
                    title = name,
                    description = ruleSummary(rule, use24),
                    onClick = { navigate(Routes.rules(target.storageKey)) },
                )
            }
            item {
                SettingRow(title = stringResource(R.string.focus_add_app_rule), onClick = { pickingRuleApp = true })
                SettingRow(title = stringResource(R.string.focus_group_rules), onClick = { navigate(Routes.CATEGORIES) })
            }

            item {
                SectionHeader(stringResource(R.string.focus_enforcement))
                ToggleRow(
                    title = stringResource(R.string.focus_enforce_outside),
                    description = stringResource(
                        if (vm.enforcementReady()) R.string.focus_enforce_outside_desc_ready else R.string.focus_enforce_outside_desc,
                    ),
                    checked = settings.enforcementEnabled,
                    onCheckedChange = { checked ->
                        vm.setEnforcement(checked)
                        if (checked && !vm.enforcementReady()) navigate(Routes.explain(ExplainKind.ACCESSIBILITY))
                    },
                )
                ToggleRow(
                    title = stringResource(R.string.focus_fallback_monitor),
                    description = stringResource(R.string.focus_fallback_monitor_desc),
                    checked = settings.fallbackMonitorEnabled,
                    enabled = settings.enforcementEnabled,
                    onCheckedChange = { checked ->
                        vm.setFallbackMonitor(checked)
                        if (checked) navigate(Routes.explain(ExplainKind.USAGE))
                    },
                )
                SettingRow(
                    title = stringResource(R.string.focus_battery_help),
                    description = stringResource(R.string.focus_battery_help_desc),
                    onClick = { navigate(Routes.OEM_HELP) },
                )
            }

            item {
                SectionHeader(stringResource(R.string.focus_emergency))
                EmergencyRow(onTriggered = { emergencyChallenge = true })
                if (emergencyChallenge) {
                    FrictionChallenge(
                        challenge = stringResource(R.string.focus_exit_phrase),
                        onCancel = { emergencyChallenge = false },
                        onPassed = {
                            emergencyChallenge = false
                            vm.pauseEverything()
                        },
                    )
                }
            }
        }
    }

    if (pickingAllowlist) {
        AppMultiPickerDialog(
            title = stringResource(R.string.focus_allowlist),
            apps = apps.filter { !it.hidden }.distinctBy { it.key.packageName },
            selected = settings.focusAllowlist,
            onToggle = { vm.toggleAllowed(it) },
            onDismiss = { pickingAllowlist = false },
        )
    }

    if (pickingRuleApp) {
        AppSinglePickerDialog(
            title = stringResource(R.string.focus_add_app_rule),
            apps = apps.filter { !it.hidden }.distinctBy { it.key.packageName },
            onPick = {
                pickingRuleApp = false
                navigate(Routes.rules("app:" + it.key.packageName))
            },
            onDismiss = { pickingRuleApp = false },
        )
    }

    if (creatingSchedule) {
        WindowEditorDialog(
            title = stringResource(R.string.focus_add_schedule),
            initial = null,
            showName = true,
            onDismiss = { creatingSchedule = false },
            onSave = { name, window, enabled ->
                vm.saveSchedule(0, name, window, enabled)
                creatingSchedule = false
            },
        )
    }

    editingScheduleId?.let { id ->
        val schedule = snapshot.focusSchedules.firstOrNull { it.id == id }
        if (schedule == null) {
            editingScheduleId = null
        } else {
            WindowEditorDialog(
                title = stringResource(R.string.focus_edit_schedule),
                initial = schedule.window,
                initialName = schedule.name,
                initialEnabled = schedule.enabled,
                showName = true,
                onDismiss = { editingScheduleId = null },
                onSave = { name, window, enabled ->
                    vm.saveSchedule(id, name, window, enabled)
                    editingScheduleId = null
                },
                onDelete = {
                    vm.deleteSchedule(id)
                    editingScheduleId = null
                },
            )
        }
    }
}

/**
 * Hard-to-trigger-accidentally escape route: press and hold for 3 seconds, then pass a short
 * friction challenge. TalkBack users get the same flow through a custom action.
 */
@Composable
private fun EmergencyRow(onTriggered: () -> Unit) {
    val label = stringResource(R.string.focus_emergency_action)
    SettingRow(
        title = label,
        description = stringResource(R.string.focus_emergency_desc),
        modifier = Modifier
            .heightIn(min = Spacing.touchTarget)
            .semantics { customActions = listOf(CustomAccessibilityAction(label) { onTriggered(); true }) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val released = withTimeoutOrNull(3_000L) { waitForUpOrCancellation() }
                    if (released == null) onTriggered()
                }
            },
    )
}
