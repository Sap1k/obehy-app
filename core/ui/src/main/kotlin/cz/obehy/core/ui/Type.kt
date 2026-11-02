package cz.obehy.core.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Manrope, the brand typeface, from one variable font (res/font/manrope.ttf, OFL). */
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map {
        Font(R.font.manrope, weight = it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    },
)

private fun TextStyle.manrope() = copy(fontFamily = Manrope)

/** The Material type scale, emphasized styles included, set in Manrope. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val ObehyTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.manrope(),
        displayMedium = displayMedium.manrope(),
        displaySmall = displaySmall.manrope(),
        headlineLarge = headlineLarge.manrope(),
        headlineMedium = headlineMedium.manrope(),
        headlineSmall = headlineSmall.manrope(),
        titleLarge = titleLarge.manrope(),
        titleMedium = titleMedium.manrope(),
        titleSmall = titleSmall.manrope(),
        bodyLarge = bodyLarge.manrope(),
        bodyMedium = bodyMedium.manrope(),
        bodySmall = bodySmall.manrope(),
        labelLarge = labelLarge.manrope(),
        labelMedium = labelMedium.manrope(),
        labelSmall = labelSmall.manrope(),
        displayLargeEmphasized = displayLargeEmphasized.manrope(),
        displayMediumEmphasized = displayMediumEmphasized.manrope(),
        displaySmallEmphasized = displaySmallEmphasized.manrope(),
        headlineLargeEmphasized = headlineLargeEmphasized.manrope(),
        headlineMediumEmphasized = headlineMediumEmphasized.manrope(),
        headlineSmallEmphasized = headlineSmallEmphasized.manrope(),
        titleLargeEmphasized = titleLargeEmphasized.manrope(),
        titleMediumEmphasized = titleMediumEmphasized.manrope(),
        titleSmallEmphasized = titleSmallEmphasized.manrope(),
        bodyLargeEmphasized = bodyLargeEmphasized.manrope(),
        bodyMediumEmphasized = bodyMediumEmphasized.manrope(),
        bodySmallEmphasized = bodySmallEmphasized.manrope(),
        labelLargeEmphasized = labelLargeEmphasized.manrope(),
        labelMediumEmphasized = labelMediumEmphasized.manrope(),
        labelSmallEmphasized = labelSmallEmphasized.manrope(),
    )
}
