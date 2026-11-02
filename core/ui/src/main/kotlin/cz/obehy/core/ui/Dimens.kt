package cz.obehy.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Every gap and padding in the app is one of these; feature modules never
 * write a `.dp` number themselves (lint check ObehyRawDimension).
 */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

/** Fixed sizes of recurring things. Add one here rather than sizing something in a feature. */
@Immutable
data class Sizes(
    /** A standard icon. */
    val icon: Dp = 24.dp,
    /** The round or cookie badge an icon sits in at the start of a list item. */
    val iconBadge: Dp = 48.dp,
    /** A lone illustration icon, e.g. on an empty or placeholder screen. */
    val illustration: Dp = 56.dp,
    /** The brand moment on the welcome screen and the symbol inside it. */
    val hero: Dp = 220.dp,
    val heroSymbol: Dp = 112.dp,
    /** The wordmark when it stands on its own; 40 dp is its default height in a header. */
    val logoLarge: Dp = 52.dp,
    val logo: Dp = 40.dp,
    /** The widest a column of content gets (tablets keep a readable line length). */
    val readableWidth: Dp = 560.dp,
    /** The widest a centred paragraph gets. */
    val paragraphWidth: Dp = 420.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }
internal val LocalSizes = staticCompositionLocalOf { Sizes() }

/** The Oběhy tokens on top of [androidx.compose.material3.MaterialTheme]: read them like `ObehyTheme.spacing.l`. */
object ObehyTheme {
    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    val sizes: Sizes
        @Composable @ReadOnlyComposable
        get() = LocalSizes.current
}
