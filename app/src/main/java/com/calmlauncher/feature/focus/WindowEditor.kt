package com.calmlauncher.feature.focus

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.core.designsystem.ToggleRow
import com.calmlauncher.domain.focus.Days
import com.calmlauncher.domain.focus.TimeWindow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

fun daysSummary(mask: Int, everyDay: String, weekdays: String, weekend: String): String = when (mask) {
    Days.EVERY_DAY -> everyDay
    Days.WEEKDAYS -> weekdays
    Days.WEEKEND -> weekend
    else -> Days.toList(mask).joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
fun windowSummary(window: TimeWindow, use24h: Boolean): String {
    val days = daysSummary(
        window.daysMask,
        stringResource(R.string.days_every_day),
        stringResource(R.string.days_weekdays),
        stringResource(R.string.days_weekend),
    )
    val start = TimeWindow.formatMinute(window.startMinute, use24h)
    val end = TimeWindow.formatMinute(window.endMinute, use24h)
    return "$days · $start – $end"
}

/** Editor for a weekly time window, used by focus schedules and app block windows. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WindowEditorDialog(
    title: String,
    initial: TimeWindow?,
    initialName: String = "",
    initialEnabled: Boolean = true,
    showName: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, window: TimeWindow, enabled: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val use24 = android.text.format.DateFormat.is24HourFormat(context)
    var mask by rememberSaveable { mutableIntStateOf(initial?.daysMask ?: Days.WEEKDAYS) }
    var start by rememberSaveable { mutableIntStateOf(initial?.startMinute ?: (22 * 60)) }
    var end by rememberSaveable { mutableIntStateOf(initial?.endMinute ?: (7 * 60)) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var enabled by rememberSaveable { mutableStateOf(initialEnabled) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = CalmTheme.type.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (showName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text(stringResource(R.string.window_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(R.string.window_days),
                    style = CalmTheme.type.label,
                    color = CalmTheme.colors.text,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    for (day in DayOfWeek.entries) {
                        val on = Days.contains(mask, day)
                        Text(
                            day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = CalmTheme.type.label,
                            color = if (on) CalmTheme.colors.accent else CalmTheme.colors.textSecondary,
                            modifier = Modifier
                                .heightIn(min = Spacing.touchTarget)
                                .border(1.dp, if (on) CalmTheme.colors.accent else CalmTheme.colors.divider, RoundedCornerShape(16.dp))
                                .clickable(role = Role.Checkbox) { mask = mask xor Days.bit(day) }
                                .semantics { selected = on }
                                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                        )
                    }
                }
                SettingRow(
                    title = stringResource(R.string.window_start),
                    value = TimeWindow.formatMinute(start, use24),
                    onClick = { picking = "start" },
                )
                SettingRow(
                    title = stringResource(R.string.window_end),
                    value = TimeWindow.formatMinute(end, use24),
                    description = if (end <= start) stringResource(R.string.window_overnight) else null,
                    onClick = { picking = "end" },
                )
                ToggleRow(
                    title = stringResource(R.string.window_enabled),
                    description = null,
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
        },
        confirmButton = {
            CalmTextButton(stringResource(R.string.action_save), enabled = mask != 0, emphasized = true, onClick = {
                onSave(name.trim(), TimeWindow(mask, start, end), enabled)
            })
        },
        dismissButton = {
            Column {
                if (onDelete != null) CalmTextButton(stringResource(R.string.action_delete), onClick = onDelete)
                CalmTextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
            }
        },
        containerColor = CalmTheme.colors.surface,
    )

    val which = picking
    if (which != null) {
        val initialMinute = if (which == "start") start else end
        val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = use24)
        AlertDialog(
            onDismissRequest = { picking = null },
            text = { TimePicker(state = state) },
            confirmButton = {
                CalmTextButton(stringResource(R.string.action_ok), emphasized = true, onClick = {
                    val value = state.hour * 60 + state.minute
                    if (which == "start") start = value else end = value
                    picking = null
                })
            },
            dismissButton = { CalmTextButton(stringResource(R.string.action_cancel), onClick = { picking = null }) },
            containerColor = CalmTheme.colors.surface,
        )
    }
}
