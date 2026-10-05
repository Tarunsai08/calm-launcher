package com.calmlauncher.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.calmlauncher.R
import com.calmlauncher.data.settings.FontChoice
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.TextWeight

/** Bundled OFL fonts (variable fonts: one file covers every weight). */
object CalmFonts {
    private fun variable(res: Int) = FontFamily(
        Font(res, FontWeight.Light),
        Font(res, FontWeight.Normal),
        Font(res, FontWeight.Medium),
        Font(res, FontWeight.SemiBold),
        Font(res, FontWeight.Bold),
    )

    val inter by lazy { variable(R.font.inter) }
    val ibmPlex by lazy { variable(R.font.ibm_plex_sans) }
    val atkinson by lazy { variable(R.font.atkinson) }
    val spaceGrotesk by lazy { variable(R.font.space_grotesk) }
    val sourceSerif by lazy { variable(R.font.source_serif) }
    val jetbrainsMono by lazy { variable(R.font.jetbrains_mono) }

    fun family(choice: FontChoice): FontFamily = when (choice) {
        FontChoice.SYSTEM -> FontFamily.Default
        FontChoice.INTER -> inter
        FontChoice.IBM_PLEX_SANS -> ibmPlex
        FontChoice.ATKINSON -> atkinson
        FontChoice.SPACE_GROTESK -> spaceGrotesk
        FontChoice.SOURCE_SERIF -> sourceSerif
        FontChoice.JETBRAINS_MONO -> jetbrainsMono
    }
}

fun TextWeight.toFontWeight(): FontWeight = when (this) {
    TextWeight.LIGHT -> FontWeight.Light
    TextWeight.REGULAR -> FontWeight.Normal
    TextWeight.MEDIUM -> FontWeight.Medium
    TextWeight.BOLD -> FontWeight.Bold
}

@Immutable
data class CalmTypography(
    val clock: TextStyle,
    val date: TextStyle,
    val homeLabel: TextStyle,
    val drawerLabel: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val material: Typography,
)

val LocalCalmTypography = staticCompositionLocalOf { buildTypography(LauncherSettings.DEFAULT) }

@Composable
fun rememberCalmTypography(s: LauncherSettings): CalmTypography = remember(
    s.font, s.textScale, s.textWeight, s.letterSpacingEm, s.lineHeightMultiplier,
    s.clockSizeSp, s.clockWeight, s.favoriteTextSizeSp, s.drawerTextSizeSp,
) { buildTypography(s) }

/**
 * Sizes are in sp so system font scaling (up to 200%) applies on top of the in-app scale.
 * Layouts never fix heights for text, so nothing clips at large scales.
 */
fun buildTypography(s: LauncherSettings): CalmTypography {
    val family = CalmFonts.family(s.font)
    val scale = s.textScale.factor
    val weight = s.textWeight.toFontWeight()
    val spacing = s.letterSpacingEm.em
    val lh = s.lineHeightMultiplier.coerceIn(1.0f, 2.0f)

    fun style(size: Float, w: FontWeight = weight, lineHeight: Float = lh, letter: Float? = null) = TextStyle(
        fontFamily = family,
        fontWeight = w,
        fontSize = (size * scale).sp,
        lineHeight = (size * scale * lineHeight).sp,
        letterSpacing = letter?.em ?: spacing,
    )

    val body = style(16f)
    val label = style(14f, FontWeight.Medium)
    val title = style(22f, FontWeight.Medium)
    val caption = style(13f)
    val material = Typography(
        displayLarge = style(48f), displayMedium = style(40f), displaySmall = style(34f),
        headlineLarge = style(30f), headlineMedium = style(26f), headlineSmall = style(24f),
        titleLarge = title, titleMedium = style(18f, FontWeight.Medium), titleSmall = style(16f, FontWeight.Medium),
        bodyLarge = body, bodyMedium = style(15f), bodySmall = caption,
        labelLarge = label, labelMedium = style(13f, FontWeight.Medium), labelSmall = style(12f, FontWeight.Medium),
    )
    return CalmTypography(
        clock = style(s.clockSizeSp.toFloat(), s.clockWeight.toFontWeight(), lineHeight = 1.1f, letter = -0.02f),
        date = style(18f, lineHeight = 1.3f),
        homeLabel = style(s.favoriteTextSizeSp.toFloat(), lineHeight = 1.25f),
        drawerLabel = style(s.drawerTextSizeSp.toFloat(), lineHeight = 1.3f),
        title = title,
        body = body,
        label = label,
        caption = caption,
        material = material,
    )
}
