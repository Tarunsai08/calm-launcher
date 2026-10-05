package com.calmlauncher.feature.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.ui.AppLabel
import com.calmlauncher.ui.HomeOverlayState
import com.calmlauncher.ui.LauncherViewModel
import com.calmlauncher.ui.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DrawerScreen(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    overlay: HomeOverlayState,
    onOpenSheet: (LauncherApp) -> Unit,
    navigate: (String) -> Unit,
    drawerVm: DrawerViewModel = hiltViewModel(),
) {
    val state by drawerVm.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
    var autoLaunchCancelledFor by remember { mutableStateOf<String?>(null) }

    fun closeDrawer() {
        keyboard?.hide()
        drawerVm.clear()
        field = TextFieldValue("")
        overlay.close()
    }

    fun launchApp(app: LauncherApp) {
        keyboard?.hide()
        vm.launchApp(app.key)
        closeDrawer()
    }

    fun activate(item: DrawerItem) {
        when (item) {
            is DrawerItem.App -> launchApp(item.app)
            is DrawerItem.Shortcut -> {
                when (val t = item.shortcut.target) {
                    is ShortcutTarget.SystemSettings -> vm.launcher.openSettingsAction(t.action)
                    is ShortcutTarget.InApp -> navigate(t.route)
                    ShortcutTarget.Timer -> vm.launcher.setTimer()
                }
                closeDrawer()
            }
            is DrawerItem.Contact -> {
                vm.launcher.openContact(item.uri)
                closeDrawer()
            }
            is DrawerItem.Calculation -> Unit
            is DrawerItem.WebSearch -> {
                vm.launcher.webSearch(item.query, settings.webSearchPackage)
                closeDrawer()
            }
            is DrawerItem.Header -> Unit
        }
    }

    // Typed-on-home text arrives here.
    LaunchedEffect(overlay.pendingQuery) {
        overlay.pendingQuery?.let { q ->
            field = TextFieldValue(q, TextRange(q.length))
            drawerVm.setQuery(q)
            overlay.pendingQuery = null
        }
    }

    LaunchedEffect(overlay.focusSearchToken, settings.autoFocusSearch) {
        if (settings.autoFocusSearch || overlay.focusSearchToken > 0) {
            delay(50)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    // Auto-launch the only match after a short, cancellable delay.
    val single = state.singleMatch
    val singleId = single?.key?.id
    LaunchedEffect(singleId, state.query, settings.autoLaunchSingleMatch) {
        if (!settings.autoLaunchSingleMatch || single == null || autoLaunchCancelledFor == state.query) return@LaunchedEffect
        delay(settings.autoLaunchDelayMs.toLong().coerceIn(0L, 2_000L))
        launchApp(single)
    }

    BackHandler(enabled = true) {
        if (field.text.isNotEmpty()) {
            field = TextFieldValue("")
            drawerVm.clear()
        } else {
            closeDrawer()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(CalmTheme.colors.background)
            // Make the whole drawer a hit target so taps never fall through to home gestures.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize()) {
            // Search field
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val placeholder = stringResource(R.string.search_placeholder)
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        drawerVm.setQuery(it.text)
                        autoLaunchCancelledFor = null
                    },
                    singleLine = true,
                    textStyle = CalmTheme.type.title.copy(color = CalmTheme.colors.text),
                    cursorBrush = SolidColor(CalmTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { state.items.firstOrNull { it.isActionable() }?.let { activate(it) } }),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Spacing.touchTarget)
                        .focusRequester(focusRequester)
                        .testTag(DrawerTestTags.SEARCH)
                        .semantics { contentDescription = placeholder }
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                                closeDrawer()
                                true
                            } else {
                                false
                            }
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) {
                                Text(placeholder, style = CalmTheme.type.title, color = CalmTheme.colors.textSecondary)
                            }
                            inner()
                        }
                    },
                )
                if (field.text.isNotEmpty()) {
                    Text(
                        stringResource(R.string.search_clear),
                        style = CalmTheme.type.label,
                        color = CalmTheme.colors.textSecondary,
                        modifier = Modifier
                            .heightIn(min = Spacing.touchTarget)
                            .clickable(role = Role.Button) {
                                field = TextFieldValue("")
                                drawerVm.clear()
                            }
                            .padding(Spacing.sm),
                    )
                }
                // Always visible, whatever the list length or keyboard state: settings must never be
                // buried at the end of a long app list.
                val settingsLabel = stringResource(R.string.drawer_launcher_settings)
                Text(
                    stringResource(R.string.settings_title).lowercase(),
                    style = CalmTheme.type.label,
                    color = CalmTheme.colors.textSecondary,
                    modifier = Modifier
                        .heightIn(min = Spacing.touchTarget)
                        .testTag(DrawerTestTags.SETTINGS)
                        .semantics { contentDescription = settingsLabel }
                        .clickable(role = Role.Button) {
                            closeDrawer()
                            navigate(Routes.SETTINGS)
                        }
                        .padding(Spacing.sm),
                )
            }

            if (single != null && settings.autoLaunchSingleMatch && autoLaunchCancelledFor != state.query) {
                Text(
                    stringResource(R.string.search_auto_launching, single.label),
                    style = CalmTheme.type.caption,
                    color = CalmTheme.colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { autoLaunchCancelledFor = state.query }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            if (state.query.isEmpty() && state.categories.isNotEmpty()) {
                CategoryChips(
                    categories = state.categories.map { it.id to it.name },
                    selected = state.selectedCategory,
                    onSelect = { drawerVm.selectCategory(it) },
                )
            }

            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                ) {
                    items(state.items, key = { it.key }, contentType = { it::class }) { item ->
                        DrawerRow(
                            item = item,
                            vm = vm,
                            settings = settings,
                            onActivate = { activate(item) },
                            onLongPress = { if (item is DrawerItem.App) onOpenSheet(item.app) },
                        )
                    }
                    if (state.query.isEmpty()) {
                        item(key = "footer") {
                            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg)) {
                                FooterLink(stringResource(R.string.drawer_launcher_settings)) {
                                    closeDrawer()
                                    navigate(Routes.SETTINGS)
                                }
                                if (settings.notesEnabled) FooterLink(stringResource(R.string.sc_notes)) { closeDrawer(); navigate(Routes.NOTES) }
                                if (settings.todosEnabled) FooterLink(stringResource(R.string.sc_todos)) { closeDrawer(); navigate(Routes.TODOS) }
                            }
                        }
                    }
                    if (state.query.isNotEmpty() && state.items.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.search_no_results),
                                style = CalmTheme.type.body,
                                color = CalmTheme.colors.textSecondary,
                                modifier = Modifier.padding(Spacing.lg),
                            )
                        }
                    }
                }
                if (state.query.isEmpty()) {
                    AlphabetIndex(
                        letters = state.letters,
                        onJump = { index, _ -> scope.launch { listState.scrollToItem(index) } },
                        modifier = Modifier.padding(end = Spacing.xxs),
                    )
                }
            }
        }
    }
}

/** Stable handles for UI tests. */
object DrawerTestTags {
    const val SEARCH = "drawer_search"
    const val SETTINGS = "drawer_settings"
}

private fun DrawerItem.isActionable(): Boolean = this !is DrawerItem.Header && this !is DrawerItem.Calculation

@Composable
private fun DrawerRow(
    item: DrawerItem,
    vm: LauncherViewModel,
    settings: LauncherSettings,
    onActivate: () -> Unit,
    onLongPress: () -> Unit,
) {
    val rowModifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
    when (item) {
        is DrawerItem.App -> AppLabel(
            app = item.app,
            style = CalmTheme.type.drawerLabel,
            onClick = onActivate,
            onLongClick = onLongPress,
            icons = vm.icons,
            showIcon = settings.showIconsInDrawer,
            dimmed = vm.blockedByFocus(item.app.key.packageName),
            hint = item.hint ?: if (item.app.hidden) stringResource(R.string.badge_hidden) else null,
            modifier = rowModifier,
        )
        is DrawerItem.Shortcut -> SecondaryRow(item.label, stringResource(R.string.result_kind_setting), onActivate, rowModifier)
        is DrawerItem.Contact -> SecondaryRow(item.name, stringResource(R.string.result_kind_contact), onActivate, rowModifier)
        is DrawerItem.Calculation -> Row(
            rowModifier.heightIn(min = Spacing.touchTarget).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("= ${item.result}", style = CalmTheme.type.drawerLabel, color = CalmTheme.colors.accent)
        }
        is DrawerItem.WebSearch -> SecondaryRow(
            stringResource(R.string.result_web_search, item.query),
            null,
            onActivate,
            rowModifier,
        )
        is DrawerItem.Header -> Text(
            item.title,
            style = CalmTheme.type.caption,
            color = CalmTheme.colors.textSecondary,
            modifier = rowModifier.padding(top = Spacing.md),
        )
    }
}

@Composable
private fun SecondaryRow(title: String, kind: String?, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = CalmTheme.type.drawerLabel,
            color = CalmTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (kind != null) {
            Spacer(Modifier.width(Spacing.xs))
            Text(kind, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = CalmTheme.type.body,
        color = CalmTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
    )
}

@Composable
private fun CategoryChips(categories: List<Pair<Long, String>>, selected: Long?, onSelect: (Long?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md),
    ) {
        Chip(stringResource(R.string.drawer_all), selected == null) { onSelect(null) }
        for ((id, name) in categories) {
            Chip(name, selected == id) { onSelect(if (selected == id) null else id) }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = CalmTheme.type.label,
        color = if (selected) CalmTheme.colors.accent else CalmTheme.colors.textSecondary,
        modifier = Modifier
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    )
}
