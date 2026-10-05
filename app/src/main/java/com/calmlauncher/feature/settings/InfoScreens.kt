package com.calmlauncher.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.calmlauncher.BuildConfig
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmPage
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.SectionHeader
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.settings.LockMethod
import com.calmlauncher.ui.ExplainKind
import com.calmlauncher.ui.Routes

private data class OemTip(val brands: List<String>, val title: Int, val body: Int)

private val OEM_TIPS = listOf(
    OemTip(listOf("xiaomi", "redmi", "poco"), R.string.oem_xiaomi_title, R.string.oem_xiaomi_body),
    OemTip(listOf("samsung"), R.string.oem_samsung_title, R.string.oem_samsung_body),
    OemTip(listOf("oppo", "realme"), R.string.oem_oppo_title, R.string.oem_oppo_body),
    OemTip(listOf("vivo", "iqoo"), R.string.oem_vivo_title, R.string.oem_vivo_body),
    OemTip(listOf("oneplus"), R.string.oem_oneplus_title, R.string.oem_oneplus_body),
    OemTip(listOf("huawei", "honor"), R.string.oem_huawei_title, R.string.oem_huawei_body),
    OemTip(listOf("motorola", "nothing", "google", "pixel"), R.string.oem_stock_title, R.string.oem_stock_body),
)

/** Per-OEM guidance for keeping optional background features alive on aggressive battery managers. */
@Composable
fun OemHelpScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val mine = OEM_TIPS.firstOrNull { tip -> tip.brands.any { manufacturer.contains(it) } }
    CalmPage(title = stringResource(R.string.oem_title), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.oem_intro),
                style = CalmTheme.type.body,
                color = CalmTheme.colors.text,
                modifier = Modifier.padding(Spacing.md),
            )
            SettingRow(
                title = stringResource(R.string.oem_open_battery),
                description = stringResource(R.string.oem_open_battery_desc),
                onClick = { vm.launcher.openBatteryOptimizationSettings() },
            )
            SettingRow(title = stringResource(R.string.oem_open_app_info), onClick = { vm.launcher.openOwnAppDetails() })
            if (mine != null) {
                SectionHeader(stringResource(R.string.oem_your_device, Build.MANUFACTURER))
                TipBlock(mine)
            }
            SectionHeader(stringResource(R.string.oem_all_devices))
            OEM_TIPS.filter { it != mine }.forEach { TipBlock(it) }
        }
    }
}

@Composable
private fun TipBlock(tip: OemTip) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text(stringResource(tip.title), style = CalmTheme.type.label, color = CalmTheme.colors.text)
        Text(stringResource(tip.body), style = CalmTheme.type.body, color = CalmTheme.colors.textSecondary)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val fonts = remember {
        runCatching { context.assets.list("licenses")?.toList().orEmpty() }.getOrDefault(emptyList()).sorted()
    }
    var openLicense by remember { mutableStateOf<String?>(null) }
    CalmPage(title = stringResource(R.string.about_title), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.app_name), style = CalmTheme.type.title, color = CalmTheme.colors.text, modifier = Modifier.padding(Spacing.md))
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = CalmTheme.type.caption,
                color = CalmTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            Text(stringResource(R.string.about_body), style = CalmTheme.type.body, color = CalmTheme.colors.text, modifier = Modifier.padding(Spacing.md))
            SectionHeader(stringResource(R.string.section_privacy))
            Text(stringResource(R.string.privacy_full), style = CalmTheme.type.body, color = CalmTheme.colors.text, modifier = Modifier.padding(Spacing.md))
            SettingRow(title = stringResource(R.string.focus_battery_help), onClick = { navigate(Routes.OEM_HELP) })
            SectionHeader(stringResource(R.string.about_licenses))
            Text(stringResource(R.string.about_fonts), style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.md))
            for (file in fonts) {
                SettingRow(title = file.removeSuffix("-OFL.txt"), description = "SIL Open Font License 1.1", onClick = {
                    openLicense = if (openLicense == file) null else file
                })
                if (openLicense == file) {
                    val text = remember(file) {
                        runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }.getOrDefault("")
                    }
                    Text(text, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary, modifier = Modifier.padding(Spacing.md))
                }
            }
        }
    }
}

/**
 * Plain-language explanation shown BEFORE sending the user to a system permission screen.
 * Says what the permission is for, what is never done with it, and how to revoke it.
 */
@Composable
fun PermissionExplainerScreen(kind: String, onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    var status by remember { mutableStateOf(vm.permissionStatus()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = vm.permissionStatus() }
    val (title, body, granted) = when (kind) {
        ExplainKind.ACCESSIBILITY -> Triple(R.string.explain_a11y_title, R.string.explain_a11y_body, status.accessibility)
        ExplainKind.NOTIFICATIONS -> Triple(R.string.explain_notif_title, R.string.explain_notif_body, status.notificationAccess)
        ExplainKind.OVERLAY -> Triple(R.string.explain_overlay_title, R.string.explain_overlay_body, status.overlay)
        else -> Triple(R.string.explain_usage_title, R.string.explain_usage_body, status.usageAccess)
    }
    CalmPage(title = stringResource(title), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md)) {
            Text(stringResource(body), style = CalmTheme.type.body, color = CalmTheme.colors.text)
            Text(
                stringResource(if (granted) R.string.explain_status_granted else R.string.explain_status_not_granted),
                style = CalmTheme.type.label,
                color = if (granted) CalmTheme.colors.accent else CalmTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = Spacing.md),
            )
            CalmTextButton(
                stringResource(if (granted) R.string.explain_manage else R.string.explain_continue),
                emphasized = true,
                onClick = {
                    when (kind) {
                        ExplainKind.ACCESSIBILITY -> {
                            if (vm.settings.value.lockMethod == LockMethod.NONE) vm.setLockMethod(LockMethod.ACCESSIBILITY)
                            vm.launcher.openAccessibilitySettings()
                        }
                        ExplainKind.NOTIFICATIONS -> vm.launcher.openNotificationListenerSettings()
                        ExplainKind.OVERLAY -> vm.launcher.openOverlaySettings()
                        else -> vm.launcher.openUsageAccessSettings()
                    }
                },
            )
            CalmTextButton(stringResource(R.string.explain_not_now), onClick = onBack)
        }
    }
}
