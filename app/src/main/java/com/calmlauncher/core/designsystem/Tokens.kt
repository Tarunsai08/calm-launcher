package com.calmlauncher.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale. Every margin and gap in the app comes from here. */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp

    /** Minimum interactive size (WCAG / Material guidance). */
    val touchTarget: Dp = 48.dp

    /** Content column width on tablets, foldables and landscape. */
    val maxContentWidth: Dp = 560.dp
}

object Radii {
    val none: Dp = 0.dp
    val sm: Dp = 4.dp
    val md: Dp = 8.dp
    val lg: Dp = 16.dp
    val pill: Dp = 999.dp
}

/** Motion durations (ms). Kept short and subtle; all collapse to 0 with system animations off. */
@Immutable
data class Motion(
    val fast: Int = 120,
    val medium: Int = 180,
    val slow: Int = 240,
    val enabled: Boolean = true,
) {
    fun scaled(ms: Int): Int = if (enabled) ms else 0

    companion object {
        val Disabled = Motion(0, 0, 0, enabled = false)
    }
}

val LocalMotion = staticCompositionLocalOf { Motion() }
