package com.calmlauncher.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.RadioRow
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.settings.LockMethod
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.domain.model.AppKey
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.feature.settings.SettingsViewModel
import com.calmlauncher.service.AppLauncher
import com.calmlauncher.service.SystemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val apps: AppsRepository,
    private val settings: SettingsRepository,
    val systemActions: SystemActions,
    val launcher: AppLauncher,
) : ViewModel() {
    val appList: StateFlow<List<LauncherApp>> = apps.apps

    fun finish(favorites: List<AppKey>, lock: LockMethod) {
        viewModelScope.launch {
            if (favorites.isNotEmpty()) apps.setFavorites(favorites)
            settings.update { it.copy(onboardingDone = true, lockMethod = lock) }
        }
    }
}

private const val PAGES = 5

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val apps by vm.appList.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var lock by rememberSaveable { mutableStateOf(LockMethod.NONE) }
    var isDefault by remember { mutableStateOf(SettingsViewModel.isDefaultLauncher(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = SettingsViewModel.isDefaultLauncher(context) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = SettingsViewModel.isDefaultLauncher(context)
    }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.systemActions.isDeviceAdminActive()) lock = LockMethod.DEVICE_ADMIN
    }

    fun finish() {
        val keys = selectedIds.mapNotNull { AppKey.parse(it) }
        vm.finish(keys, lock)
        onFinished()
    }

    BackHandler(enabled = page > 0) { page-- }

    Box(
        Modifier
            .fillMaxSize()
            .background(CalmTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize().padding(Spacing.lg)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onboarding_step, page + 1, PAGES),
                    style = CalmTheme.type.caption,
                    color = CalmTheme.colors.textSecondary,
                )
                CalmTextButton(stringResource(R.string.onboarding_skip), onClick = { finish() })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    0 -> TextPage(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body))
                    1 -> FavoritesPage(apps, selectedIds) { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    }
                    2 -> LockPage(
                        lock = lock,
                        onSelect = { method ->
                            lock = method
                            when (method) {
                                LockMethod.ACCESSIBILITY -> vm.launcher.openAccessibilitySettings()
                                LockMethod.DEVICE_ADMIN -> adminLauncher.launch(vm.systemActions.deviceAdminIntent())
                                LockMethod.NONE -> Unit
                            }
                        },
                    )
                    3 -> TextPage(stringResource(R.string.onboarding_optional_title), stringResource(R.string.onboarding_optional_body))
                    else -> DefaultPage(isDefault) {
                        val intent = SettingsViewModel.requestDefaultIntent(context)
                        if (intent != null) roleLauncher.launch(intent) else vm.launcher.openHomeSettings()
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (page > 0) CalmTextButton(stringResource(R.string.onboarding_back), onClick = { page-- }) else Spacer(Modifier.width(1.dp))
                if (page < PAGES - 1) {
                    CalmTextButton(stringResource(R.string.onboarding_next), emphasized = true, onClick = { page++ })
                } else {
                    CalmTextButton(stringResource(R.string.onboarding_finish), emphasized = true, onClick = { finish() })
                }
            }
        }
    }
}

@Composable
private fun TextPage(title: String, body: String) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Spacing.xl)) {
        Text(title, style = CalmTheme.type.clock.copy(fontSize = CalmTheme.type.title.fontSize * 1.6f), color = CalmTheme.colors.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.heightIn(min = Spacing.lg))
        Text(body, style = CalmTheme.type.body, color = CalmTheme.colors.textSecondary)
    }
}

@Composable
private fun FavoritesPage(apps: List<LauncherApp>, selected: List<String>, onToggle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Text(
            stringResource(R.string.onboarding_favorites_title),
            style = CalmTheme.type.title,
            color = CalmTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.onboarding_favorites_body, selected.size),
            style = CalmTheme.type.caption,
            color = CalmTheme.colors.textSecondary,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(apps.filter { !it.hidden }, key = { it.key.id }) { app ->
                val checked = app.key.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.touchTarget)
                        .toggleable(value = checked, role = Role.Checkbox) { onToggle(app.key.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(app.label, style = CalmTheme.type.body, color = CalmTheme.colors.text)
                }
            }
        }
    }
}

@Composable
private fun LockPage(lock: LockMethod, onSelect: (LockMethod) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Spacing.md)) {
        Text(
            stringResource(R.string.onboarding_lock_title),
            style = CalmTheme.type.title,
            color = CalmTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.onboarding_lock_body),
            style = CalmTheme.type.body,
            color = CalmTheme.colors.textSecondary,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        RadioRow(
            stringResource(R.string.lock_accessibility),
            lock == LockMethod.ACCESSIBILITY,
            onSelect = { onSelect(LockMethod.ACCESSIBILITY) },
            description = stringResource(R.string.onboarding_lock_a11y_desc),
        )
        RadioRow(
            stringResource(R.string.lock_admin),
            lock == LockMethod.DEVICE_ADMIN,
            onSelect = { onSelect(LockMethod.DEVICE_ADMIN) },
            description = stringResource(R.string.onboarding_lock_admin_desc),
        )
        RadioRow(
            stringResource(R.string.onboarding_lock_skip),
            lock == LockMethod.NONE,
            onSelect = { onSelect(LockMethod.NONE) },
            description = stringResource(R.string.onboarding_lock_skip_desc),
        )
    }
}

@Composable
private fun DefaultPage(isDefault: Boolean, onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Spacing.md)) {
        Text(
            stringResource(R.string.onboarding_default_title),
            style = CalmTheme.type.title,
            color = CalmTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.onboarding_default_body),
            style = CalmTheme.type.body,
            color = CalmTheme.colors.textSecondary,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        SettingRow(
            title = stringResource(if (isDefault) R.string.set_default_launcher_done else R.string.set_default_launcher),
            onClick = onRequest,
        )
    }
}
