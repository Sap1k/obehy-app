package cz.obehy.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow

/**
 * A screen led by a picture (a journey's map): the [hero] sits under the top bar at full height
 * and shrinks away as the content scrolls up, then comes back when you scroll to the top. The bar
 * keeps the [title] and [subtitle], the back arrow and [actions]. Tapping the hero calls
 * [onHeroClick]; pulling the content down calls [onRefresh].
 */
@Composable
fun ObehyHeroScreen(
    title: String,
    onBack: () -> Unit,
    hero: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onHeroClick: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val heroHeight = with(LocalDensity.current) { ObehyTheme.sizes.heroMap.toPx() }
    var shown by remember { mutableFloatStateOf(heroHeight) }
    val collapse =
        remember(heroHeight) {
            object : NestedScrollConnection {
                // Scrolling up shrinks the hero before the content moves; scrolling down at the top
                // grows it back before a pull can refresh.
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val delta = available.y
                    if (delta < 0 || scroll.value == 0) {
                        val next = (shown + delta).coerceIn(0f, heroHeight)
                        val used = next - shown
                        shown = next
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }
            }
        }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).nestedScroll(collapse)) {
            val heightDp = with(LocalDensity.current) { shown.toDp() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    .clipToBounds()
                    .graphicsLayer { alpha = shown / heroHeight }
                    .then(if (onHeroClick != null) Modifier.clickable(onClick = onHeroClick) else Modifier),
            ) {
                // The hero keeps its full size and is clipped, so a map doesn't reframe while it shrinks.
                Box(Modifier.fillMaxWidth().height(ObehyTheme.sizes.heroMap)) { hero() }
            }
            val body: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = ObehyTheme.sizes.readableWidth)
                            .fillMaxWidth()
                            .padding(horizontal = ObehyTheme.spacing.l, vertical = ObehyTheme.spacing.m),
                        content = content,
                    )
                }
            }
            if (onRefresh != null) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) { body() }
            } else {
                body()
            }
        }
    }
}
