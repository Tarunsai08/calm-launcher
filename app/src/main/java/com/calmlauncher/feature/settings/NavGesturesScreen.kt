package com.calmlauncher.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmPage
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.SectionHeader
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.SliderRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.core.designsystem.ToggleRow
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.service.SystemActions
import com.calmlauncher.service.navigation.HideTrial
import com.calmlauncher.service.navigation.NavBarController
import com.calmlauncher.service.navigation.SystemNavMode
import com.calmlauncher.ui.ExplainKind
import com.calmlauncher.ui.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** Snapshot of the system state the navigation screen explains. Re-read on resume. */
data class NavStatus(
    val helperOn: Boolean,
    val isXiaomi: Boolean,
    val mode: SystemNavMode,
    val canHide: Boolean,
    val buttonsHidden: Boolean,
)

@HiltViewModel
class NavGesturesViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val systemActions: SystemActions,
    val navBar: NavBarController,
) : ViewModel() {
    val settings: StateFlow<LauncherSettings> = repo.settings
    val trial: StateFlow<HideTrial> = navBar.trial

    fun status() = NavStatus(
        helperOn = systemActions.isAccessibilityEnabled(),
        isXiaomi = navBar.isXiaomi(),
        mode = navBar.systemNavMode(),
        canHide = navBar.canHideButtons(),
        buttonsHidden = navBar.buttonsHidden(),
    )

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun startTrial(): Boolean = navBar.startTrial()

    fun stopHiding() {
        update { it.copy(navHideButtons = false) }
        navBar.restoreButtons()
    }

    override fun onCleared() {
        navBar.resetTrialState()
        super.onCleared()
    }
}

/**
 * Built-in navigation gestures for phones that switch to buttons when a third-party launcher
 * is the home app (Xiaomi HyperOS/MIUI does this). Swipe in from the left or right edge for
 * back, up from the bottom for home, up and hold for recent apps.
 */
@Composable
fun NavGesturesScreen(onBack: () -> Unit, navigate: (String) -> Unit, vm: NavGesturesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val trial by vm.trial.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val status = remember(refresh, trial, s) { vm.status() }

    CalmPage(title = stringResource(R.string.nav_title), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Body(stringResource(R.string.nav_intro))

            SectionHeader(stringResource(R.string.nav_status))
            SettingRow(
                title = stringResource(R.string.nav_system_mode),
                value = stringResource(
                    when (status.mode) {
                        SystemNavMode.BUTTONS -> R.string.nav_mode_buttons
                        SystemNavMode.TWO_BUTTONS -> R.string.nav_mode_two_buttons
                        SystemNavMode.GESTURES -> R.string.nav_mode_gestures
                        SystemNavMode.UNKNOWN -> R.string.nav_mode_unknown
                    },
                ),
            )
            SettingRow(
                title = stringResource(R.string.nav_helper),
                description = stringResource(if (status.helperOn) R.string.nav_helper_on_desc else R.string.nav_helper_off_desc),
                value = stringResource(if (status.helperOn) R.string.status_on else R.string.status_off),
                onClick = { navigate(Routes.explain(ExplainKind.ACCESSIBILITY)) },
            )

            SectionHeader(stringResource(R.string.nav_gestures))
            ToggleRow(
                title = stringResource(R.string.nav_enable),
                description = stringResource(R.string.nav_enable_desc),
                checked = s.navGesturesEnabled,
                onCheckedChange = { v -> vm.update { it.copy(navGesturesEnabled = v) } },
            )
            val on = s.navGesturesEnabled
            ToggleRow(
                title = stringResource(R.string.nav_back_sides),
                description = stringResource(R.string.nav_back_sides_desc),
                checked = s.navBackFromSides,
                enabled = on,
                onCheckedChange = { v -> vm.update { it.copy(navBackFromSides = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.nav_bottom),
                description = stringResource(if (status.isXiaomi) R.string.nav_bottom_desc_xiaomi else R.string.nav_bottom_desc_other),
                checked = s.navBottomGestures,
                enabled = on,
                onCheckedChange = { v -> vm.update { it.copy(navBottomGestures = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.nav_previous_app),
                description = stringResource(R.string.nav_previous_app_desc),
                checked = s.navQuickSwitch,
                enabled = on && s.navBottomGestures,
                onCheckedChange = { v -> vm.update { it.copy(navQuickSwitch = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.nav_haptics),
                description = null,
                checked = s.navHaptics,
                enabled = on,
                onCheckedChange = { v -> vm.update { it.copy(navHaptics = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.nav_handle),
                description = stringResource(R.string.nav_handle_desc),
                checked = s.navShowHandle,
                enabled = on && s.navBottomGestures,
                onCheckedChange = { v -> vm.update { it.copy(navShowHandle = v) } },
            )
            DpSlider(
                title = stringResource(R.string.nav_edge_width),
                description = stringResource(R.string.nav_edge_width_desc),
                value = s.navEdgeWidthDp,
            ) { v -> vm.update { it.copy(navEdgeWidthDp = v) } }
            DpSlider(
                title = stringResource(R.string.nav_bottom_height),
                description = null,
                value = s.navBottomHeightDp,
            ) { v -> vm.update { it.copy(navBottomHeightDp = v) } }

            if (status.isXiaomi) {
                HideButtonsSection(context, vm, s, status, trial)
            }

            SectionHeader(stringResource(R.string.nav_how_title))
            Body(stringResource(R.string.nav_how_body))
        }
    }
}

@Composable
private fun HideButtonsSection(
    context: Context,
    vm: NavGesturesViewModel,
    s: LauncherSettings,
    status: NavStatus,
    trial: HideTrial,
) {
    SectionHeader(stringResource(R.string.nav_hide_title))
    Body(stringResource(R.string.nav_hide_intro))
    if (!status.canHide) {
        Body(stringResource(R.string.nav_hide_permission_steps))
        SelectionContainer {
            Text(
                vm.navBar.grantCommand(),
                style = CalmTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
                color = CalmTheme.colors.text,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
        CalmTextButton(stringResource(R.string.nav_copy_command), modifier = Modifier.padding(horizontal = Spacing.sm)) {
            copyToClipboard(context, vm.navBar.grantCommand())
        }
        return
    }
    val ready = s.navGesturesEnabled && s.navBottomGestures && status.helperOn
    when {
        trial is HideTrial.Waiting -> {
            Text(
                stringResource(R.string.nav_trial_waiting, trial.secondsLeft),
                style = CalmTheme.type.label,
                color = CalmTheme.colors.accent,
                modifier = Modifier.padding(Spacing.md),
            )
        }
        s.navHideButtons -> {
            Body(stringResource(if (trial is HideTrial.Confirmed) R.string.nav_trial_confirmed else R.string.nav_hidden_now))
            CalmTextButton(stringResource(R.string.nav_show_buttons), modifier = Modifier.padding(horizontal = Spacing.sm)) {
                vm.stopHiding()
            }
        }
        else -> {
            if (trial is HideTrial.RolledBack) Body(stringResource(R.string.nav_trial_rolled_back))
            if (!ready) Body(stringResource(R.string.nav_hide_needs))
            CalmTextButton(
                stringResource(R.string.nav_try_hide),
                emphasized = true,
                enabled = ready,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            ) {
                if (!vm.startTrial()) Toast.makeText(context, R.string.nav_hide_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun DpSlider(title: String, description: String?, value: Int, onChange: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    SliderRow(
        title = title,
        description = description,
        value = current,
        valueRange = 8f..40f,
        steps = 15,
        valueLabel = "${current.roundToInt()} dp",
        onValueChange = { current = it },
        onValueChangeFinished = { onChange(current.roundToInt()) },
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = CalmTheme.type.body,
        color = CalmTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("adb", text))
    Toast.makeText(context, R.string.nav_copied, Toast.LENGTH_SHORT).show()
}
