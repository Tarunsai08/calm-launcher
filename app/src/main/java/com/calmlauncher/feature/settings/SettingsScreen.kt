package com.calmlauncher.feature.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmPage
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.RadioRow
import com.calmlauncher.core.designsystem.SectionHeader
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.SliderRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.core.designsystem.ToggleRow
import com.calmlauncher.data.settings.LockMethod
import com.calmlauncher.domain.model.LauncherAction
import com.calmlauncher.ui.ActionPickerDialog
import com.calmlauncher.ui.ConfirmDialog
import java.time.LocalDate

/** Which action a gesture/slot picker is editing. */
enum class ActionSlot { SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, DOUBLE_TAP, LONG_PRESS, LEFT_SLOT, RIGHT_SLOT, CLOCK, DATE }

@Composable
fun SettingsScreen(
    initialSection: String?,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var pickingAction by rememberSaveable { mutableStateOf<ActionSlot?>(null) }
    var choice by remember { mutableStateOf<SettingItem.Choice?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf(vm.permissionStatus()) }
    val listState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = vm.permissionStatus() }
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri, includeTools = true)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        status = vm.permissionStatus()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        status = vm.permissionStatus()
        if (vm.systemActions.isDeviceAdminActive()) vm.setLockMethod(LockMethod.DEVICE_ADMIN)
    }

    val actions = SettingsActions(
        update = vm::update,
        navigate = navigate,
        pickAction = { pickingAction = it },
        requestPermission = { permissionLauncher.launch(it) },
        requestDefaultLauncher = {
            val intent = SettingsViewModel.requestDefaultIntent(context)
            if (intent != null) roleLauncher.launch(intent) else vm.launcher.openHomeSettings()
        },
        requestDeviceAdmin = { adminLauncher.launch(vm.systemActions.deviceAdminIntent()) },
        exportBackup = { exportLauncher.launch("calm-launcher-backup-${LocalDate.now()}.json") },
        importBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        confirmReset = { confirmReset = true },
        confirmDelete = { confirmDelete = true },
        launcher = vm.launcher,
        setLockMethod = vm::setLockMethod,
    )
    val sections = buildSettingsSections(settings, apps, status, actions)

    LaunchedEffect(initialSection, sections.size) {
        val target = initialSection ?: return@LaunchedEffect
        var index = 1 // search field
        for (section in sections) {
            if (section.id == target) {
                listState.scrollToItem(index)
                break
            }
            index += 1 + section.items.size
        }
    }

    CalmPage(title = stringResource(R.string.settings_title), onBack = onBack) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.settings_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
            if (query.isNotBlank()) {
                val results = sections.search(query)
                if (results.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(R.string.settings_no_results),
                            style = CalmTheme.type.body,
                            color = CalmTheme.colors.textSecondary,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
                items(results, key = { "r:" + it.second.id }) { (section, item) ->
                    SettingItemRow(item, sectionLabel = section.title, onChoice = { choice = it })
                }
            } else {
                for (section in sections) {
                    item(key = "h:" + section.id) { SectionHeader(section.title) }
                    items(section.items, key = { it.id }) { item -> SettingItemRow(item, null) { choice = it } }
                }
            }
        }
    }

    pickingAction?.let { slot ->
        val current = when (slot) {
            ActionSlot.SWIPE_UP -> settings.swipeUp
            ActionSlot.SWIPE_DOWN -> settings.swipeDown
            ActionSlot.SWIPE_LEFT -> settings.swipeLeft
            ActionSlot.SWIPE_RIGHT -> settings.swipeRight
            ActionSlot.DOUBLE_TAP -> settings.doubleTap
            ActionSlot.LONG_PRESS -> settings.longPress
            ActionSlot.LEFT_SLOT -> settings.leftSlot
            ActionSlot.RIGHT_SLOT -> settings.rightSlot
            ActionSlot.CLOCK -> settings.clockAction
            ActionSlot.DATE -> settings.dateAction
        }
        ActionPickerDialog(
            title = stringResource(R.string.settings_choose_action),
            current = current,
            apps = apps,
            onDismiss = { pickingAction = null },
            onPick = { action: LauncherAction ->
                vm.update {
                    when (slot) {
                        ActionSlot.SWIPE_UP -> it.copy(swipeUp = action)
                        ActionSlot.SWIPE_DOWN -> it.copy(swipeDown = action)
                        ActionSlot.SWIPE_LEFT -> it.copy(swipeLeft = action)
                        ActionSlot.SWIPE_RIGHT -> it.copy(swipeRight = action)
                        ActionSlot.DOUBLE_TAP -> it.copy(doubleTap = action)
                        ActionSlot.LONG_PRESS -> it.copy(longPress = action)
                        ActionSlot.LEFT_SLOT -> it.copy(leftSlot = action)
                        ActionSlot.RIGHT_SLOT -> it.copy(rightSlot = action)
                        ActionSlot.CLOCK -> it.copy(clockAction = action)
                        ActionSlot.DATE -> it.copy(dateAction = action)
                    }
                }
                pickingAction = null
            },
        )
    }

    choice?.let { c ->
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(c.title, style = CalmTheme.type.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    c.options.forEachIndexed { i, label ->
                        RadioRow(label, selected = i == c.selectedIndex, onSelect = {
                            c.onSelect(i)
                            choice = null
                        })
                    }
                }
            },
            confirmButton = {},
            dismissButton = { CalmTextButton(stringResource(R.string.action_cancel), onClick = { choice = null }) },
            containerColor = CalmTheme.colors.surface,
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_title),
            message = stringResource(R.string.reset_message),
            confirmLabel = stringResource(R.string.reset_confirm),
            onDismiss = { confirmReset = false },
            onConfirm = {
                vm.resetDefaults()
                confirmReset = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_all_title),
            message = stringResource(R.string.delete_all_message),
            confirmLabel = stringResource(R.string.delete_all_confirm),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                vm.deleteAll()
                confirmDelete = false
            },
        )
    }
}

@Composable
private fun SettingItemRow(item: SettingItem, sectionLabel: String?, onChoice: (SettingItem.Choice) -> Unit) {
    val description = listOfNotNull(sectionLabel, item.description).joinToString(" · ").ifBlank { null }
    when (item) {
        is SettingItem.Toggle -> ToggleRow(
            title = item.title,
            description = description,
            checked = item.checked,
            onCheckedChange = item.onChange,
            enabled = item.enabled,
        )
        is SettingItem.Action -> SettingRow(title = item.title, description = description, value = item.value, onClick = item.onClick)
        is SettingItem.Choice -> SettingRow(
            title = item.title,
            description = description,
            value = item.options.getOrNull(item.selectedIndex),
            onClick = { onChoice(item) },
        )
        is SettingItem.Slider -> {
            var value by remember(item.value) { mutableFloatStateOf(item.value) }
            SliderRow(
                title = item.title,
                description = description,
                value = value,
                valueRange = item.range,
                steps = item.steps,
                valueLabel = item.format(value),
                onValueChange = { value = it },
                onValueChangeFinished = { item.onChange(value) },
            )
        }
    }
}

/** POST_NOTIFICATIONS only exists (and is only needed) on Android 13+. */
internal val notificationPermission: String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
