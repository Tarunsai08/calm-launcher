package com.calmlauncher.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calmlauncher.R

/** Centres content in a readable column on wide screens (tablets, foldables, landscape). */
@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxWidth()) { content() }
    }
}

/** Opaque full-screen page with a text title and a back button. */
@Composable
fun CalmPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(CalmTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ContentColumn {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = CalmTheme.colors.text,
                        )
                    }
                    Text(
                        title,
                        style = CalmTheme.type.title,
                        color = CalmTheme.colors.text,
                        modifier = Modifier.weight(1f).semantics { heading() },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    actions()
                }
                content()
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = CalmTheme.type.caption,
        color = CalmTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.xs)
            .semantics { heading() },
    )
}

@Composable
fun CalmDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(horizontal = Spacing.md), color = CalmTheme.colors.divider)
}

/** Two-line text row: title + optional description. Tappable when [onClick] is set. */
@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget + Spacing.xs)
            .then(clickModifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CalmTheme.type.body, color = if (enabled) CalmTheme.colors.text else CalmTheme.colors.textDisabled)
            if (!description.isNullOrBlank()) {
                Text(description, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
            }
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.size(2.dp))
                Text(value, style = CalmTheme.type.label, color = CalmTheme.colors.accent)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(Spacing.sm))
            trailing()
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget + Spacing.xs)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CalmTheme.type.body, color = if (enabled) CalmTheme.colors.text else CalmTheme.colors.textDisabled)
            if (!description.isNullOrBlank()) {
                Text(description, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CalmTheme.colors.onAccent,
                checkedTrackColor = CalmTheme.colors.accent,
                uncheckedThumbColor = CalmTheme.colors.textSecondary,
                uncheckedTrackColor = CalmTheme.colors.surface,
                uncheckedBorderColor = CalmTheme.colors.textSecondary,
            ),
        )
    }
}

@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = CalmTheme.type.body, color = CalmTheme.colors.text)
            if (!description.isNullOrBlank()) {
                Text(description, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
fun SliderRow(
    title: String,
    description: String?,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = CalmTheme.type.body, color = CalmTheme.colors.text, modifier = Modifier.weight(1f))
            Text(valueLabel, style = CalmTheme.type.label, color = CalmTheme.colors.accent)
        }
        if (!description.isNullOrBlank()) {
            Text(description, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

/** Flat text button used across the app instead of filled buttons. */
@Composable
fun CalmTextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Spacing.touchTarget),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Text(
            text,
            style = if (emphasized) CalmTheme.type.label else CalmTheme.type.body,
            color = if (!enabled) CalmTheme.colors.textDisabled else if (emphasized) CalmTheme.colors.accent else CalmTheme.colors.text,
        )
    }
}

@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CalmTheme.type.body,
        color = CalmTheme.colors.textSecondary,
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
    )
}
