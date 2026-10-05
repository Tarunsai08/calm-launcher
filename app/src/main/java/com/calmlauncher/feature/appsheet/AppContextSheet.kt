package com.calmlauncher.feature.appsheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmDivider
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.RadioRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.service.AppShortcut
import com.calmlauncher.ui.LauncherViewModel
import com.calmlauncher.ui.Routes
import com.calmlauncher.ui.TextInputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Long-press menu for an app, wherever it appears (home, drawer, search). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContextSheet(
    app: LauncherApp,
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
    navigate: (String) -> Unit,
    sheetVm: AppSheetViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renaming by rememberSaveable { mutableStateOf(false) }
    var choosingCategory by rememberSaveable { mutableStateOf(false) }
    val apps by vm.appList.collectAsStateWithLifecycle()
    val categories by sheetVm.categories.collectAsStateWithLifecycle()
    val notificationModes by sheetVm.notificationModes.collectAsStateWithLifecycle()
    val current = apps.firstOrNull { it.key == app.key } ?: app
    val shortcuts by produceState<List<AppShortcut>>(emptyList(), app.key) {
        value = withContext(Dispatchers.IO) { vm.launcher.shortcutsFor(app.key) }
    }
    val otherProfileCopies = remember(apps, app.key) {
        apps.filter { it.key.packageName == app.key.packageName && it.key.userSerial != app.key.userSerial }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CalmTheme.colors.surface,
        contentColor = CalmTheme.colors.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Spacing.md),
        ) {
            Text(
                current.label,
                style = CalmTheme.type.title,
                color = CalmTheme.colors.text,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm).semantics { heading() },
            )
            if (current.alias != null) {
                Text(
                    stringResource(R.string.sheet_original_name, current.systemLabel.ifBlank { current.key.packageName }),
                    style = CalmTheme.type.caption,
                    color = CalmTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }

            if (shortcuts.isNotEmpty()) {
                for (sc in shortcuts) {
                    SheetAction(sc.label) {
                        vm.launcher.startShortcut(sc)
                        onDismiss()
                    }
                }
                CalmDivider(Modifier.padding(vertical = Spacing.xs))
            }

            if (current.isFavorite) {
                SheetAction(stringResource(R.string.sheet_remove_favorite)) { vm.setFavorite(current.key, false); onDismiss() }
                SheetAction(stringResource(R.string.sheet_move_up)) { vm.moveFavorite(current.key, -1) }
                SheetAction(stringResource(R.string.sheet_move_down)) { vm.moveFavorite(current.key, 1) }
            } else {
                SheetAction(stringResource(R.string.sheet_add_favorite)) { vm.setFavorite(current.key, true); onDismiss() }
            }
            SheetAction(stringResource(R.string.sheet_rename)) { renaming = true }
            SheetAction(stringResource(if (current.hidden) R.string.sheet_unhide else R.string.sheet_hide)) {
                vm.setHidden(current.key, !current.hidden)
                onDismiss()
            }
            SheetAction(
                stringResource(R.string.sheet_category),
                value = categories.firstOrNull { it.id == current.categoryId }?.name ?: stringResource(R.string.category_none),
            ) { choosingCategory = true }
            SheetAction(stringResource(R.string.sheet_focus_rules)) {
                navigate(Routes.rules("app:" + current.key.packageName))
            }
            val mode = notificationModes[current.key.packageName]
            SheetAction(
                stringResource(R.string.sheet_notifications),
                value = stringResource(mode?.labelRes() ?: R.string.notif_mode_allow),
            ) { sheetVm.cycleNotificationMode(current.key.packageName) }

            for (copy in otherProfileCopies) {
                val label = if (copy.isWorkProfile) R.string.sheet_open_work else R.string.sheet_open_other_profile
                SheetAction(stringResource(label)) {
                    vm.launchApp(copy.key)
                    onDismiss()
                }
            }
            CalmDivider(Modifier.padding(vertical = Spacing.xs))
            SheetAction(stringResource(R.string.sheet_app_info)) {
                vm.launcher.openAppInfo(current.key)
                onDismiss()
            }
            SheetAction(stringResource(R.string.sheet_uninstall)) {
                vm.launcher.uninstall(current.key)
                onDismiss()
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.sheet_rename),
            initial = current.alias ?: current.label,
            supporting = stringResource(R.string.rename_supporting),
            onDismiss = { renaming = false },
            onConfirm = { text ->
                val trimmed = text.trim()
                vm.setAlias(current.key, if (trimmed.isEmpty() || trimmed == current.systemLabel) null else trimmed)
                renaming = false
            },
        )
    }

    if (choosingCategory) {
        AlertDialog(
            onDismissRequest = { choosingCategory = false },
            title = { Text(stringResource(R.string.sheet_category), style = CalmTheme.type.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RadioRow(stringResource(R.string.category_none), current.categoryId == null, onSelect = {
                        vm.setCategory(current.key, null)
                        choosingCategory = false
                    })
                    for (cat in categories) {
                        RadioRow(cat.name, current.categoryId == cat.id, onSelect = {
                            vm.setCategory(current.key, cat.id)
                            choosingCategory = false
                        })
                    }
                    if (categories.isEmpty()) {
                        Text(
                            stringResource(R.string.category_empty_hint),
                            style = CalmTheme.type.caption,
                            color = CalmTheme.colors.textSecondary,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
            },
            confirmButton = {
                CalmTextButton(stringResource(R.string.category_manage), onClick = {
                    choosingCategory = false
                    navigate(Routes.CATEGORIES)
                })
            },
            containerColor = CalmTheme.colors.surface,
        )
    }
}

@Composable
private fun SheetAction(text: String, value: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Text(text, style = CalmTheme.type.body, color = CalmTheme.colors.text)
        if (value != null) Text(value, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
    }
}
