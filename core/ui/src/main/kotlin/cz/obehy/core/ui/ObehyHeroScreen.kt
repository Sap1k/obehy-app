package cz.obehy.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** How tall a hero stands when the content has scrolled up, status bar included. */
@Composable
fun heroStripHeight(): Dp =
    ObehyTheme.sizes.heroMapStrip + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** How tall a hero stands at rest, status bar included. */
@Composable
fun heroFullHeight(): Dp = ObehyTheme.sizes.heroMap + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * A screen led by a picture (a journey's map), edge to edge under the status bar with a floating
 * back button. A sheet lies over its lower edge, holding the [title] and [subtitle], or [header]
 * in their place. As the content scrolls, the [hero] shrinks to a strip and the sheet stays right
 * under it; [hero] is told how far along that is, from 0 to 1, to keep what it shows in view.
 * Tapping the hero calls [onHeroClick]; pulling the content down calls [onRefresh]. With
 * [contentMorphKey], only the content below the sheet takes part in a [SharedBounds] morph.
 */
@Composable
fun ObehyHeroScreen(
    title: String,
    onBack: () -> Unit,
    hero: @Composable (shrunk: () -> Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    onHeroClick: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    contentMorphKey: Any? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0)) { padding ->
        val body: @Composable () -> Unit = {
            val scroll = rememberScrollState()
            val full = heroFullHeight()
            val strip = heroStripHeight()
            val overlap = ObehyTheme.spacing.xl
            val density = LocalDensity.current
            var headerHeight by remember { mutableIntStateOf(0) }
            val range = with(density) { (full - strip).toPx() }
            val shrunk = remember(scroll, range) { { (scroll.value / range).coerceIn(0f, 1f) } }
            val sheetAt = with(density) { (full - overlap).toPx() }
            val readable = Modifier.widthIn(max = ObehyTheme.sizes.readableWidth).fillMaxWidth()
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(full - overlap + with(density) { headerHeight.toDp() }))
                    val inner: @Composable () -> Unit = {
                        Column(
                            readable.padding(horizontal = ObehyTheme.spacing.l, vertical = ObehyTheme.spacing.m),
                            content = content,
                        )
                    }
                    // In place: scrolled up, the content is behind the map and the sheet, and stays there.
                    if (contentMorphKey != null) SharedBounds(contentMorphKey, inPlace = true) { inner() } else inner()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(full)
                        .shrinkingWith(scroll, full - strip)
                        // On top of the content, so a drag starting on it has to scroll that itself.
                        .scrollable(scroll, Orientation.Vertical, reverseDirection = true)
                        .then(if (onHeroClick != null) Modifier.clickable(onClick = onHeroClick) else Modifier),
                ) { hero(shrunk) }
                val sheet =
                    MaterialTheme.shapes.extraLarge.copy(
                        bottomStart = ZeroCornerSize,
                        bottomEnd = ZeroCornerSize,
                    )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerHeight = it.height }
                        .graphicsLayer { translationY = sheetAt - minOf(scroll.value.toFloat(), range) }
                        .scrollable(scroll, Orientation.Vertical, reverseDirection = true)
                        .clip(sheet)
                        .background(MaterialTheme.colorScheme.surface),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        readable.padding(
                            start = ObehyTheme.spacing.l,
                            end = ObehyTheme.spacing.l,
                            top = ObehyTheme.spacing.l,
                            bottom = ObehyTheme.spacing.s,
                        ),
                    ) {
                        if (header != null) {
                            header()
                        } else {
                            HeroTitle(title, subtitle)
                        }
                    }
                }
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(ObehyTheme.spacing.s),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                // Left of the map's own credits button, which takes that corner.
                Row(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(
                        top = ObehyTheme.spacing.s,
                        end = ObehyTheme.spacing.s + ObehyTheme.sizes.iconBadge,
                    ),
                    content = actions,
                )
            }
        }
        if (onRefresh != null) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { body() }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) { body() }
        }
    }
}

@Composable
private fun HeroTitle(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Trims the top and bottom off this as [scroll] moves, by up to [by] in all, keeping the middle in
 * place of the top. In the layer alone, so scrolling never measures the picture again.
 */
private fun Modifier.shrinkingWith(
    scroll: ScrollState,
    by: Dp,
): Modifier =
    graphicsLayer {
        val trim = minOf(scroll.value.toFloat(), by.toPx()) / 2
        translationY = -trim
        clip = true
        shape = TrimmedEnds(trim)
    }

private class TrimmedEnds(
    private val trim: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(Rect(0f, trim, size.width, size.height - trim))
}
