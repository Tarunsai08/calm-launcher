package com.calmlauncher.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
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
import com.calmlauncher.core.util.DeviceAuth
import com.calmlauncher.core.util.findActivity
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.db.CategoryEntity
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.security.SecretStore
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.domain.model.AppKey
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.feature.focus.AppMultiPickerDialog
import com.calmlauncher.ui.ConfirmDialog
import com.calmlauncher.ui.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageViewModel @Inject constructor(
    private val appsRepo: AppsRepository,
    private val rules: RulesRepository,
    private val settingsRepo: SettingsRepository,
    private val secrets: SecretStore,
) : ViewModel() {
    val apps: StateFlow<List<LauncherApp>> = appsRepo.apps
    val categories: StateFlow<List<CategoryEntity>> = rules.categories
    val settings: StateFlow<LauncherSettings> = settingsRepo.settings

    fun setHidden(key: AppKey, hidden: Boolean) = viewModelScope.launch { appsRepo.setHidden(key, hidden) }
    fun setFavorite(key: AppKey, favorite: Boolean) = viewModelScope.launch { appsRepo.setFavorite(key, favorite) }
    fun move(key: AppKey, delta: Int) = viewModelScope.launch { appsRepo.moveFavorite(key, delta) }
    fun setCategory(key: AppKey, id: Long?) = viewModelScope.launch { appsRepo.setCategory(key, id) }
    fun addCategory(name: String) = viewModelScope.launch { if (name.isNotBlank()) rules.addCategory(name) }
    fun renameCategory(id: Long, name: String) = viewModelScope.launch { if (name.isNotBlank()) rules.renameCategory(id, name) }
    fun deleteCategory(id: Long) = viewModelScope.launch { rules.deleteCategory(id) }

    fun setLock(enabled: Boolean) = viewModelScope.launch { settingsRepo.update { it.copy(hiddenLockEnabled = enabled) } }
    fun hasPin(): Boolean = secrets.hasPin()
    fun setPin(pin: String) = secrets.setPin(pin)
    fun verifyPin(pin: String): Boolean = secrets.verify(pin)
    fun clearPin() = secrets.clear()
}

/** Hidden apps list. Optionally protected by the device lock (biometric/credential) or an app PIN. */
@Composable
fun HiddenAppsScreen(onBack: () -> Unit, vm: ManageViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var askPin by rememberSaveable { mutableStateOf(false) }
    var settingPin by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.hidden_title)
    val unlockSubtitle = stringResource(R.string.hidden_unlock_subtitle)

    fun requestUnlock() {
        val activity = context.findActivity()
        if (DeviceAuth.isAvailable(context) && activity != null) {
            DeviceAuth.authenticate(activity, title, unlockSubtitle, onSuccess = { unlocked = true }, onFailure = { msg ->
                if (vm.hasPin()) askPin = true else error = msg
            })
        } else if (vm.hasPin()) {
            askPin = true
        } else {
            unlocked = true // No lock available; nothing to protect with.
        }
    }

    LaunchedEffect(settings.hiddenLockEnabled) {
        if (!settings.hiddenLockEnabled) unlocked = true else if (!unlocked) requestUnlock()
    }

    CalmPage(title = title, onBack = onBack) {
        if (!unlocked) {
            EmptyState(error ?: stringResource(R.string.hidden_locked))
            CalmTextButton(stringResource(R.string.hidden_unlock), emphasized = true, onClick = { requestUnlock() })
        } else {
            val hidden = apps.filter { it.hidden }
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    ToggleRow(
                        title = stringResource(R.string.hidden_lock),
                        description = stringResource(R.string.hidden_lock_desc),
                        checked = settings.hiddenLockEnabled,
                        onCheckedChange = { enable ->
                            if (enable && !DeviceAuth.isAvailable(context) && !vm.hasPin()) settingPin = true else vm.setLock(enable)
                        },
                    )
                    SettingRow(
                        title = stringResource(if (vm.hasPin()) R.string.hidden_change_pin else R.string.hidden_set_pin),
                        description = stringResource(R.string.hidden_pin_desc),
                        onClick = { settingPin = true },
                    )
                    SettingRow(title = stringResource(R.string.hidden_add), onClick = { picking = true })
                    SectionHeader(stringResource(R.string.hidden_list))
                }
                if (hidden.isEmpty()) item { EmptyState(stringResource(R.string.hidden_empty)) }
                items(hidden, key = { it.key.id }) { app ->
                    SettingRow(
                        title = app.label,
                        description = stringResource(R.string.hidden_tap_to_unhide),
                        onClick = { vm.setHidden(app.key, false) },
                    )
                }
            }
        }
    }

    if (picking) {
        AppMultiPickerDialog(
            title = stringResource(R.string.hidden_add),
            apps = apps,
            selected = apps.filter { it.hidden }.map { it.key.packageName }.toSet(),
            onToggle = { pkg -> apps.filter { it.key.packageName == pkg }.forEach { vm.setHidden(it.key, !it.hidden) } },
            onDismiss = { picking = false },
        )
    }

    if (askPin) {
        TextInputDialog(
            title = stringResource(R.string.hidden_enter_pin),
            initial = "",
            keyboardType = KeyboardType.NumberPassword,
            onDismiss = { askPin = false },
            onConfirm = { pin ->
                if (vm.verifyPin(pin)) {
                    unlocked = true
                    error = null
                } else {
                    error = context.getString(R.string.hidden_wrong_pin)
                }
                askPin = false
            },
        )
    }

    if (settingPin) {
        PinSetupDialog(
            onDismiss = { settingPin = false },
            onSet = { pin ->
                vm.setPin(pin)
                vm.setLock(true)
                settingPin = false
            },
        )
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hidden_set_pin), style = CalmTheme.type.title) },
        text = {
            Column {
                Text(stringResource(R.string.hidden_pin_rules), style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text(stringResource(R.string.hidden_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(12) },
                    label = { Text(stringResource(R.string.hidden_pin_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            CalmTextButton(
                stringResource(R.string.action_save),
                enabled = pin.length >= SecretStore.MIN_PIN && pin == confirm,
                emphasized = true,
                onClick = { onSet(pin) },
            )
        },
        dismissButton = { CalmTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
        containerColor = CalmTheme.colors.surface,
    )
}

/** Choose and order home-screen favourites (unlimited). */
@Composable
fun FavoritesScreen(onBack: () -> Unit, vm: ManageViewModel = hiltViewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    val favorites = apps.filter { it.isFavorite }.sortedBy { it.favoriteOrder }
    CalmPage(
        title = stringResource(R.string.set_favorites),
        onBack = onBack,
        actions = { CalmTextButton(stringResource(R.string.favorites_add), onClick = { picking = true }) },
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            if (favorites.isEmpty()) item { EmptyState(stringResource(R.string.favorites_empty)) }
            items(favorites, key = { it.key.id }) { app ->
                Row(Modifier.fillMaxWidth().padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, style = CalmTheme.type.body, color = CalmTheme.colors.text, modifier = Modifier.weight(1f))
                    CalmTextButton("↑", onClick = { vm.move(app.key, -1) })
                    CalmTextButton("↓", onClick = { vm.move(app.key, 1) })
                    CalmTextButton(stringResource(R.string.favorites_remove), onClick = { vm.setFavorite(app.key, false) })
                }
            }
        }
    }
    if (picking) {
        val visible = apps.filter { !it.hidden }
        AppMultiPickerDialog(
            title = stringResource(R.string.favorites_add),
            apps = visible.distinctBy { it.key.id },
            selected = favorites.map { it.key.packageName }.toSet(),
            onToggle = { pkg ->
                val app = visible.firstOrNull { it.key.packageName == pkg } ?: return@AppMultiPickerDialog
                vm.setFavorite(app.key, !app.isFavorite)
            },
            onDismiss = { picking = false },
        )
    }
}

/** User-defined groups (Work, Social, Tools…) used for drawer filtering and group limits. */
@Composable
fun CategoriesScreen(onBack: () -> Unit, openRules: (String) -> Unit, vm: ManageViewModel = hiltViewModel()) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var assigning by rememberSaveable { mutableStateOf<Long?>(null) }

    CalmPage(
        title = stringResource(R.string.categories_title),
        onBack = onBack,
        actions = { CalmTextButton(stringResource(R.string.categories_add), onClick = { adding = true }) },
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(
                    stringResource(R.string.categories_desc),
                    style = CalmTheme.type.caption,
                    color = CalmTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
            if (categories.isEmpty()) item { EmptyState(stringResource(R.string.categories_empty)) }
            items(categories, key = { it.id }) { cat ->
                val members = apps.filter { it.categoryId == cat.id }
                SectionHeader(cat.name)
                SettingRow(
                    title = stringResource(R.string.categories_apps),
                    description = members.joinToString(", ") { it.label }.ifBlank { stringResource(R.string.categories_no_apps) },
                    onClick = { assigning = cat.id },
                )
                SettingRow(title = stringResource(R.string.categories_rules), onClick = { openRules("group:${cat.id}") })
                Row(Modifier.padding(horizontal = Spacing.xs)) {
                    CalmTextButton(stringResource(R.string.categories_rename), onClick = { renaming = cat.id })
                    CalmTextButton(stringResource(R.string.action_delete), onClick = { deleting = cat.id })
                }
                CalmDivider()
            }
        }
    }

    if (adding) {
        TextInputDialog(
            title = stringResource(R.string.categories_add),
            initial = "",
            validator = { it.isNotBlank() },
            onDismiss = { adding = false },
            onConfirm = {
                vm.addCategory(it)
                adding = false
            },
        )
    }
    renaming?.let { id ->
        TextInputDialog(
            title = stringResource(R.string.categories_rename),
            initial = categories.firstOrNull { it.id == id }?.name.orEmpty(),
            validator = { it.isNotBlank() },
            onDismiss = { renaming = null },
            onConfirm = {
                vm.renameCategory(id, it)
                renaming = null
            },
        )
    }
    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.categories_delete_title),
            message = stringResource(R.string.categories_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteCategory(id)
                deleting = null
            },
        )
    }
    assigning?.let { id ->
        val distinct = apps.filter { !it.hidden }.distinctBy { it.key.packageName }
        AppMultiPickerDialog(
            title = stringResource(R.string.categories_apps),
            apps = distinct,
            selected = apps.filter { it.categoryId == id }.map { it.key.packageName }.toSet(),
            onToggle = { pkg ->
                apps.filter { it.key.packageName == pkg }.forEach { app ->
                    vm.setCategory(app.key, if (app.categoryId == id) null else id)
                }
            },
            onDismiss = { assigning = null },
        )
    }
}
