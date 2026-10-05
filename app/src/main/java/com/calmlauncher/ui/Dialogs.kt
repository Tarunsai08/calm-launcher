package com.calmlauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.RadioRow
import com.calmlauncher.core.designsystem.SectionHeader
import com.calmlauncher.domain.model.LauncherAction
import com.calmlauncher.domain.model.LauncherApp

@Composable
fun actionLabel(action: LauncherAction, apps: List<LauncherApp>): String = when (action) {
    LauncherAction.None -> stringResource(R.string.action_none)
    LauncherAction.OpenDrawer -> stringResource(R.string.action_open_drawer)
    LauncherAction.OpenSearch -> stringResource(R.string.action_open_search)
    LauncherAction.ExpandNotifications -> stringResource(R.string.action_notifications)
    LauncherAction.LockScreen -> stringResource(R.string.action_lock)
    LauncherAction.OpenLauncherSettings -> stringResource(R.string.action_settings)
    LauncherAction.ToggleFocus -> stringResource(R.string.action_focus)
    LauncherAction.OpenNotes -> stringResource(R.string.action_notes)
    LauncherAction.OpenTodos -> stringResource(R.string.action_todos)
    LauncherAction.OpenInsights -> stringResource(R.string.action_insights)
    LauncherAction.OpenDigest -> stringResource(R.string.action_digest)
    LauncherAction.DefaultClock -> stringResource(R.string.action_clock)
    LauncherAction.DefaultCalendar -> stringResource(R.string.action_calendar)
    LauncherAction.DefaultDialer -> stringResource(R.string.action_phone)
    LauncherAction.DefaultCamera -> stringResource(R.string.action_camera)
    is LauncherAction.LaunchApp -> apps.firstOrNull { it.key == action.key }?.label
        ?: stringResource(R.string.action_missing_app)
}

/** Picker for any built-in action or any installed app. */
@Composable
fun ActionPickerDialog(
    title: String,
    current: LauncherAction,
    apps: List<LauncherApp>,
    onDismiss: () -> Unit,
    onPick: (LauncherAction) -> Unit,
    allowed: List<LauncherAction> = LauncherAction.builtIns,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = CalmTheme.type.title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                item { SectionHeader(stringResource(R.string.picker_actions)) }
                items(allowed, key = { it.storageValue }) { action ->
                    RadioRow(
                        title = actionLabel(action, apps),
                        selected = current == action,
                        onSelect = { onPick(action) },
                    )
                }
                item { SectionHeader(stringResource(R.string.picker_apps)) }
                items(apps.filter { !it.hidden }, key = { it.key.id }) { app ->
                    val action = LauncherAction.LaunchApp(app.key)
                    RadioRow(title = app.label, selected = current == action, onSelect = { onPick(action) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { CalmTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
        containerColor = CalmTheme.colors.surface,
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    label: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    confirmLabel: String = stringResource(R.string.action_save),
    singleLine: Boolean = true,
    validator: (String) -> Boolean = { true },
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = CalmTheme.type.title) },
        text = {
            Column {
                if (supporting != null) Text(supporting, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = if (label != null) { { Text(label) } } else null,
                    singleLine = singleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            CalmTextButton(confirmLabel, onClick = { onConfirm(text) }, enabled = validator(text), emphasized = true)
        },
        dismissButton = { CalmTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
        containerColor = CalmTheme.colors.surface,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = CalmTheme.type.title) },
        text = { Text(message, style = CalmTheme.type.body) },
        confirmButton = { CalmTextButton(confirmLabel, onClick = onConfirm, emphasized = true) },
        dismissButton = { CalmTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
        containerColor = CalmTheme.colors.surface,
    )
}
