package com.calmlauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.settings.BackgroundMode
import com.calmlauncher.data.settings.ClockFormat
import com.calmlauncher.data.settings.FocusHideMode
import com.calmlauncher.data.settings.HorizontalAlign
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SlotStyle
import com.calmlauncher.domain.model.LauncherAction
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.feature.appsheet.AppContextSheet
import com.calmlauncher.feature.drawer.DrawerScreen
import com.calmlauncher.ui.AppLabel
import com.calmlauncher.ui.HomeOverlayState
import com.calmlauncher.ui.LauncherViewModel
import com.calmlauncher.ui.Routes
import com.calmlauncher.ui.TextInputDialog
import com.calmlauncher.ui.actionLabel

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    overlay: HomeOverlayState,
    navigate: (String) -> Unit,
) {
    val apps by vm.appList.collectAsStateWithLifecycle()
    val rulesSnapshot by vm.focusRulesVersion().collectAsStateWithLifecycle()
    var sheetApp by remember { mutableStateOf<LauncherApp?>(null) }
    var editingIntention by rememberSaveable { mutableStateOf(false) }
    val motion = CalmTheme.motion
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentSettings by rememberUpdatedState(settings)
    val currentPerform by rememberUpdatedState<(LauncherAction) -> Unit>({ vm.perform(it) })

    // Each time home becomes visible: reset enforcement session tracking.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onHomeVisible()
            if (event == Lifecycle.Event.ON_STOP) overlay.close()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back at home does nothing (it is home); in the drawer it closes the drawer.
    BackHandler(enabled = true) {
        if (overlay.drawerOpen) overlay.close()
    }

    // Focus state depends on time and rules; recompute on each recomposition tick of the clock.
    val now = rememberNow(withSeconds = false)
    val focusActive = remember(now, settings, rulesSnapshot) { vm.isFocusActive() }

    val favorites = remember(apps) { apps.filter { it.isFavorite && !it.hidden }.sortedBy { it.favoriteOrder } }
    val visibleFavorites = remember(favorites, focusActive, settings.focusHideMode, now) {
        if (focusActive && settings.focusHideMode == FocusHideMode.HIDE) {
            favorites.filterNot { vm.blockedByFocus(it.key.packageName) }
        } else {
            favorites
        }
    }

    val scrollState = rememberScrollState()
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val focusRequester = remember { FocusRequester() }

    val a11yOpenApps = stringResource(R.string.a11y_open_app_list)
    val a11ySearch = stringResource(R.string.a11y_search)
    val a11ySettings = stringResource(R.string.a11y_open_settings)
    val a11yLock = stringResource(R.string.a11y_lock_screen)
    val a11yNotifications = stringResource(R.string.a11y_notifications)
    val a11yFocus = stringResource(R.string.a11y_toggle_focus)

    Box(
        Modifier
            .fillMaxSize()
            .homeBackground(settings)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(a11yOpenApps) { overlay.openDrawer(false); true },
                    CustomAccessibilityAction(a11ySearch) { overlay.openDrawer(true); true },
                    CustomAccessibilityAction(a11ySettings) { navigate(Routes.SETTINGS); true },
                    CustomAccessibilityAction(a11yLock) { vm.perform(LauncherAction.LockScreen); true },
                    CustomAccessibilityAction(a11yNotifications) { vm.perform(LauncherAction.ExpandNotifications); true },
                    CustomAccessibilityAction(a11yFocus) { vm.perform(LauncherAction.ToggleFocus); true },
                )
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                // Hardware keyboard: typing a letter on home starts a search.
                if (!settings.typeToSearch || overlay.drawerOpen || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val cp = event.utf16CodePoint
                if (cp > 0 && Character.isLetterOrDigit(cp)) {
                    overlay.pendingQuery = String(Character.toChars(cp))
                    overlay.openDrawer(focusSearch = true)
                    true
                } else {
                    false
                }
            }
            .homeSwipes(
                enabled = !overlay.drawerOpen,
                childCanScroll = { dir, start ->
                    listBounds.contains(start) &&
                        ((dir == SwipeDirection.UP && scrollState.canScrollForward) ||
                            (dir == SwipeDirection.DOWN && scrollState.canScrollBackward))
                },
                onSwipe = { dir ->
                    when (dir) {
                        SwipeDirection.UP -> vm.perform(settings.swipeUp)
                        SwipeDirection.DOWN -> vm.perform(settings.swipeDown)
                        SwipeDirection.LEFT -> vm.perform(settings.swipeLeft)
                        SwipeDirection.RIGHT -> vm.perform(settings.swipeRight)
                    }
                },
            ),
    ) {
        // Empty-space layer: double tap and long press (labels on top take their own taps).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { currentPerform(currentSettings.doubleTap) },
                        onLongPress = { currentPerform(currentSettings.longPress) },
                    )
                },
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = settings.homeAlignment.toAlignment(),
        ) {
            Box(Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = settings.homeAlignment.toAlignment()) {
                    Spacer(Modifier.height(Spacing.xl))
                    ClockAndDate(
                        settings = settings,
                        onClockClick = { vm.perform(settings.clockAction) },
                        onDateClick = { vm.perform(settings.dateAction) },
                    )
                    val use24 = settings.clockFormat == ClockFormat.H24 ||
                        (settings.clockFormat == ClockFormat.SYSTEM && DateFormat.is24HourFormat(context))
                    if (settings.showBattery) BatteryLine(settings.homeAlignment, Modifier.padding(top = Spacing.xxs))
                    if (settings.showNextEvent) NextEventLine(settings.homeAlignment, use24, Modifier.padding(top = Spacing.xxs))
                    if (settings.showIntention) {
                        IntentionLine(
                            text = settings.intentionText,
                            align = settings.homeAlignment,
                            onClick = { editingIntention = true },
                        )
                    }
                    Spacer(Modifier.height(Spacing.xxl))

                    // The list wraps its content so taps on the empty space around it reach the
                    // background layer (double tap / long press).
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = settings.homeAlignment.toBoxAlignment()) {
                        val listModifier = Modifier
                            .onGloballyPositioned { listBounds = it.boundsInRoot() }
                            .verticalScroll(scrollState)
                        Column(listModifier, horizontalAlignment = settings.homeAlignment.toAlignment()) {
                            if (visibleFavorites.isEmpty()) {
                                Text(
                                    stringResource(R.string.home_no_favorites),
                                    style = CalmTheme.type.body,
                                    color = CalmTheme.colors.textSecondary,
                                    modifier = Modifier
                                        .heightIn(min = Spacing.touchTarget)
                                        .clickable(role = Role.Button) { navigate(Routes.FAVORITES) },
                                )
                            }
                            for (app in visibleFavorites) {
                                val dimmed = focusActive && vm.blockedByFocus(app.key.packageName)
                                AppLabel(
                                    app = app,
                                    style = CalmTheme.type.homeLabel,
                                    onClick = { vm.launchApp(app.key) },
                                    onLongClick = { sheetApp = app },
                                    icons = vm.icons,
                                    showIcon = settings.showIconsOnHome,
                                    dimmed = dimmed,
                                )
                                Spacer(Modifier.height(settings.favoriteSpacingDp.dp))
                            }
                        }
                    }
                }
            }
            BottomRow(
                settings = settings,
                apps = apps,
                focusActive = focusActive,
                onLeft = { vm.perform(settings.leftSlot) },
                onRight = { vm.perform(settings.rightSlot) },
                onFocus = { vm.perform(LauncherAction.ToggleFocus) },
                onAllApps = { overlay.openDrawer(false) },
            )
        }

        AnimatedVisibility(
            visible = overlay.drawerOpen,
            enter = fadeIn(tween(motion.scaled(motion.medium))) +
                slideInVertically(tween(motion.scaled(motion.medium))) { it / 12 },
            exit = fadeOut(tween(motion.scaled(motion.fast))) +
                slideOutVertically(tween(motion.scaled(motion.fast))) { it / 12 },
        ) {
            DrawerScreen(
                vm = vm,
                settings = settings,
                overlay = overlay,
                onOpenSheet = { sheetApp = it },
                navigate = navigate,
            )
        }
    }

    LaunchedEffect(overlay.drawerOpen) {
        if (!overlay.drawerOpen) runCatching { focusRequester.requestFocus() }
    }

    sheetApp?.let { app ->
        AppContextSheet(
            app = app,
            vm = vm,
            onDismiss = { sheetApp = null },
            navigate = { route ->
                sheetApp = null
                overlay.close()
                navigate(route)
            },
        )
    }

    if (editingIntention) {
        IntentionDialog(
            initial = settings.intentionText,
            onDismiss = { editingIntention = false },
            onSave = { text ->
                vm.updateSettings { it.copy(intentionText = text) }
                editingIntention = false
            },
        )
    }
}

private fun HorizontalAlign.toBoxAlignment(): Alignment = when (this) {
    HorizontalAlign.START -> Alignment.TopStart
    HorizontalAlign.CENTER -> Alignment.TopCenter
    HorizontalAlign.END -> Alignment.TopEnd
}

@Composable
private fun Modifier.homeBackground(settings: LauncherSettings): Modifier {
    val colors = CalmTheme.colors
    return when (settings.backgroundMode) {
        BackgroundMode.SOLID -> background(colors.background)
        BackgroundMode.WALLPAPER -> background(Color.Black.copy(alpha = settings.wallpaperScrim.coerceIn(0f, 0.9f)))
    }
}

@Composable
private fun BottomRow(
    settings: LauncherSettings,
    apps: List<LauncherApp>,
    focusActive: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onFocus: () -> Unit,
    onAllApps: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().widthIn(max = Spacing.maxContentWidth).padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.showBottomSlots) {
            SlotText(actionLabel(settings.leftSlot, apps), settings.bottomSlotStyle, glyph = "◐", onClick = onLeft)
        } else {
            Spacer(Modifier.widthIn(min = 1.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (settings.showAllAppsButton) {
                SlotText(stringResource(R.string.home_all_apps), SlotStyle.TEXT, glyph = "", onClick = onAllApps)
            }
            if (settings.showFocusToggle) {
                val label = stringResource(if (focusActive) R.string.home_focus_on else R.string.home_focus_off)
                Text(
                    text = label,
                    style = CalmTheme.type.caption,
                    color = if (focusActive) CalmTheme.colors.accent else CalmTheme.colors.textSecondary,
                    modifier = Modifier
                        .heightIn(min = Spacing.touchTarget)
                        .clickable(role = Role.Switch, onClick = onFocus)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                )
            }
        }
        if (settings.showBottomSlots) {
            SlotText(actionLabel(settings.rightSlot, apps), settings.bottomSlotStyle, glyph = "◑", onClick = onRight)
        } else {
            Spacer(Modifier.widthIn(min = 1.dp))
        }
    }
}

@Composable
private fun SlotText(label: String, style: SlotStyle, glyph: String, onClick: () -> Unit) {
    val text = if (style == SlotStyle.GLYPH && glyph.isNotEmpty()) glyph else label.lowercase()
    Text(
        text = text,
        style = CalmTheme.type.body,
        color = CalmTheme.colors.textSecondary,
        modifier = Modifier
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
    )
}

@Composable
private fun IntentionLine(text: String, align: HorizontalAlign, onClick: () -> Unit) {
    Text(
        text = text.ifBlank { stringResource(R.string.intention_placeholder) },
        style = CalmTheme.type.body,
        color = CalmTheme.colors.textSecondary,
        textAlign = align.toTextAlign(),
        modifier = Modifier
            .padding(top = Spacing.sm)
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.intention_edit), onClick = onClick),
    )
}

@Composable
private fun IntentionDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    TextInputDialog(
        title = stringResource(R.string.intention_title),
        initial = initial,
        supporting = stringResource(R.string.intention_supporting),
        onDismiss = onDismiss,
        onConfirm = { onSave(it.trim().take(140)) },
        singleLine = false,
    )
}
