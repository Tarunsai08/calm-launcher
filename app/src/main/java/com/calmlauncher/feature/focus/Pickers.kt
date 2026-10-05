package com.calmlauncher.feature.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.domain.focus.FocusRule
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.domain.search.TextNormalizer

@Composable
fun ruleSummary(rule: FocusRule, use24: Boolean): String {
    val parts = ArrayList<String>()
    if (rule.pauseEnabled) parts += stringResource(R.string.rule_summary_pause, rule.pauseSeconds)
    val limit = rule.dailyLimitMinutes
    if (limit != null) parts += pluralStringResource(R.plurals.rule_summary_limit, limit, limit)
    if (rule.alwaysBlocked) parts += stringResource(R.string.rule_summary_blocked)
    for (w in rule.blockWindows) parts += stringResource(R.string.rule_summary_window, windowSummary(w, use24))
    return if (parts.isEmpty()) stringResource(R.string.rule_summary_none) else parts.joinToString(" · ")
}

@Composable
private fun FilterField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun filterApps(apps: List<LauncherApp>, query: String): List<LauncherApp> {
    if (query.isBlank()) return apps
    val q = TextNormalizer.normalize(query)
    return apps.filter { TextNormalizer.normalize(it.label).contains(q) }
}

@Composable
fun AppMultiPickerDialog(
    title: String,
    apps: List<LauncherApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { filterApps(apps, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = CalmTheme.type.title) },
        text = {
            Column {
                FilterField(query) { query = it }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered, key = { it.key.id }) { app ->
                        val checked = app.key.packageName in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.touchTarget)
                                .toggleable(checked, role = Role.Checkbox) { onToggle(app.key.packageName) }
                                .padding(vertical = Spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(app.label, style = CalmTheme.type.body, color = CalmTheme.colors.text)
                        }
                    }
                }
            }
        },
        confirmButton = { CalmTextButton(stringResource(R.string.action_done), emphasized = true, onClick = onDismiss) },
        containerColor = CalmTheme.colors.surface,
    )
}

@Composable
fun AppSinglePickerDialog(
    title: String,
    apps: List<LauncherApp>,
    onPick: (LauncherApp) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { filterApps(apps, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = CalmTheme.type.title) },
        text = {
            Column {
                FilterField(query) { query = it }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered, key = { it.key.id }) { app ->
                        SettingRow(title = app.label, onClick = { onPick(app) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { CalmTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
        containerColor = CalmTheme.colors.surface,
    )
}
