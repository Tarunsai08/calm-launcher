package com.calmlauncher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.LocalGrayscale
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.apps.IconCache
import com.calmlauncher.domain.model.LauncherApp

/** Lazily loaded app icon at the drawn size (only when icons are switched on). */
@Composable
fun AppIcon(app: LauncherApp, icons: IconCache, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(icons.peek(app.key, px), app.key, px) {
        value = icons.load(app.key, px)
    }
    val grayscale = LocalGrayscale.current
    val b = bitmap
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size),
            colorFilter = if (grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
        )
    } else {
        Spacer(modifier.size(size))
    }
}

/**
 * A single app as text. Long-press (or the TalkBack "Options" action) opens the context sheet.
 * Work-profile and clone apps get a small text badge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLabel(
    app: LauncherApp,
    style: TextStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    icons: IconCache? = null,
    showIcon: Boolean = false,
    dimmed: Boolean = false,
    hint: String? = null,
) {
    val optionsLabel = stringResource(R.string.a11y_app_options)
    val badge = when {
        app.isWorkProfile -> stringResource(R.string.badge_work)
        app.isClone -> stringResource(R.string.badge_clone)
        else -> null
    }
    Row(
        modifier
            .heightIn(min = Spacing.touchTarget)
            .alpha(if (dimmed) 0.38f else 1f)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = optionsLabel,
            )
            .semantics {
                customActions = listOf(CustomAccessibilityAction(optionsLabel) { onLongClick(); true })
            }
            .padding(vertical = Spacing.xxs, horizontal = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon && icons != null) {
            AppIcon(app, icons, size = 28.dp)
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = app.label,
            style = style,
            color = CalmTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (badge != null) {
            Spacer(Modifier.width(Spacing.xs))
            Text(badge, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
        }
        if (hint != null) {
            Spacer(Modifier.width(Spacing.xs))
            Text(hint, style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary, maxLines = 1)
        }
    }
}
