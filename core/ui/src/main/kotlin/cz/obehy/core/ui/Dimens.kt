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
    /** An icon inside a line badge, chip or label. */
    val iconSmall: Dp = 16.dp,
    /** The proportional journey bar in a result card. */
    val journeyBar: Dp = 24.dp,
    /** The column the route line is drawn in, beside places and stops. */
    val routeGutter: Dp = 20.dp,
    /** A stop on the route line, and the line's thickness. */
    val routeMarker: Dp = 12.dp,
    val routeStroke: Dp = 4.dp,
    /** A stop passed on the way, on the route line. */
    val routePassMarker: Dp = 10.dp,
    /** A journey's map at the head of its screen. */
    val heroMap: Dp = 320.dp,
    /** What's left of it when the content has scrolled up: still a map worth looking at. */
    val heroMapStrip: Dp = 240.dp,
    /** The widest the results stay when a journey's detail is open beside them. */
    val listPane: Dp = 400.dp,
    /** One vehicle in a train's composition, and how far a cab end slants in. */
    val coachWidth: Dp = 80.dp,
    val coachHeight: Dp = 48.dp,
    val coachCab: Dp = 14.dp,
    /** The cookie a small icon sits on, at the start of a line of information. */
    val iconCookie: Dp = 32.dp,
    /** A vehicle on a map. */
    val mapVehicle: Dp = 28.dp,
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
