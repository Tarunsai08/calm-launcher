package com.calmlauncher.core.designsystem

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.calmlauncher.data.settings.AccentColor
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.ThemeMode

/** Colours beyond Material's scheme that the text-first UI needs. */
@Immutable
data class CalmColors(
    val background: Color,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val divider: Color,
    val surface: Color,
    val accent: Color,
    val onAccent: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LocalCalmColors = staticCompositionLocalOf {
    CalmColors(
        background = Color.White, text = Color.Black, textSecondary = Color.DarkGray,
        textDisabled = Color.Gray, divider = Color.LightGray, surface = Color.White,
        accent = Color.Black, onAccent = Color.White, scrim = Color.Black, isDark = false,
    )
}

/** True when the launcher's own icons/images should be drawn without colour. */
val LocalGrayscale = staticCompositionLocalOf { false }

object CalmTheme {
    val colors: CalmColors @Composable get() = LocalCalmColors.current
    val type: CalmTypography @Composable get() = LocalCalmTypography.current
    val motion: Motion @Composable get() = LocalMotion.current
}

private val LightBase = CalmColors(
    background = Color(0xFFF7F7F5),
    text = Color(0xFF111111),
    textSecondary = Color(0xFF575757),
    textDisabled = Color(0xFF8A8A8A),
    divider = Color(0xFFDADAD6),
    surface = Color(0xFFFFFFFF),
    accent = Color(0xFF111111),
    onAccent = Color(0xFFFFFFFF),
    scrim = Color(0xFFFFFFFF),
    isDark = false,
)

private val DarkBase = CalmColors(
    background = Color(0xFF121212),
    text = Color(0xFFEDEDED),
    textSecondary = Color(0xFFABABAB),
    textDisabled = Color(0xFF7A7A7A),
    divider = Color(0xFF2E2E2E),
    surface = Color(0xFF1C1C1C),
    accent = Color(0xFFEDEDED),
    onAccent = Color(0xFF111111),
    scrim = Color(0xFF000000),
    isDark = true,
)

private val AmoledBase = DarkBase.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0D0D0D),
    divider = Color(0xFF262626),
    textSecondary = Color(0xFFA3A3A3),
)

fun calmColorsFor(mode: ThemeMode, systemDark: Boolean, accent: AccentColor, grayscale: Boolean): CalmColors {
    val base = when (mode) {
        ThemeMode.LIGHT -> LightBase
        ThemeMode.DARK -> DarkBase
        ThemeMode.AMOLED -> AmoledBase
        ThemeMode.SYSTEM -> if (systemDark) DarkBase else LightBase
    }
    val argb = accent.argb
    if (argb == null || grayscale) return base
    val raw = Color(argb)
    // Keep text contrast >= 4.5:1: darken accents on light backgrounds, lighten on dark.
    val adjusted = if (base.isDark) lerp(raw, Color.White, 0.25f) else lerp(raw, Color.Black, 0.45f)
    return base.copy(accent = adjusted, onAccent = if (base.isDark) Color(0xFF111111) else Color.White)
}

private fun schemeFrom(c: CalmColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.surface,
        onPrimaryContainer = c.text,
        secondary = c.textSecondary,
        onSecondary = c.background,
        secondaryContainer = c.surface,
        onSecondaryContainer = c.text,
        tertiary = c.textSecondary,
        background = c.background,
        onBackground = c.text,
        surface = c.background,
        onSurface = c.text,
        surfaceVariant = c.surface,
        onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.surface,
        surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.background,
        outline = c.divider,
        outlineVariant = c.divider,
        inverseSurface = c.text,
        inverseOnSurface = c.background,
    )
}

/** Reads the system "remove animations" setting (animator duration scale == 0). */
@Composable
fun rememberSystemMotion(): Motion {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        if (scale == 0f) Motion.Disabled else Motion()
    }
}

@Composable
fun CalmLauncherTheme(
    settings: LauncherSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = calmColorsFor(settings.themeMode, systemDark, settings.accent, settings.grayscaleUi)
    val scheme = if (settings.dynamicColor && !settings.grayscaleUi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (colors.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // Use dynamic colour for accents only; keep the calm neutral backgrounds.
        schemeFrom(colors).copy(primary = dynamic.primary, onPrimary = dynamic.onPrimary)
    } else {
        schemeFrom(colors)
    }
    val finalColors = if (scheme.primary != colors.accent) colors.copy(accent = scheme.primary, onAccent = scheme.onPrimary) else colors
    val typography = rememberCalmTypography(settings)
    val motion = rememberSystemMotion()
    CompositionLocalProvider(
        LocalCalmColors provides finalColors,
        LocalCalmTypography provides typography,
        LocalMotion provides motion,
        LocalGrayscale provides settings.grayscaleUi,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography.material, content = content)
    }
}
