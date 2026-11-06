package cz.obehy.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** How a screen's title bar looks. */
enum class TitleBar {
    /** A large title that collapses as the content scrolls: top-level screens (Settings). */
    Large,

    /** A single-line title: detail and placeholder screens. */
    Small,

    /** The wordmark, read out as the title: the home screen, which the tab already names. */
    Logo,
}

/** How wide a screen's content gets. */
enum class ContentWidth {
    /** A column of readable width, centred on wide screens: most screens. */
    Readable,

    /** The whole window: for panes side by side (a list and its detail). */
    Full,
}

/**
 * The frame of every screen: a top bar (which keeps content clear of the status bar), and the
 * content in a column of [width] with the standard side padding. With [scrollable] the column
 * scrolls and the [TitleBar.Large] title collapses as it does; without it the column fills the
 * space below the bar (for centred or self-scrolling content). [onBack] adds a back arrow and
 * [actions] icon buttons at the end of the bar.
 */
@Composable
fun ObehyScreen(
    title: String,
    modifier: Modifier = Modifier,
    titleBar: TitleBar = TitleBar.Large,
    onBack: (() -> Unit)? = null,
    width: ContentWidth = ContentWidth.Readable,
    scrollable: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(ObehyTheme.spacing.xl),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // Each bar's own: without a bar to set its limit, the collapse would swallow every scroll.
    val scrolling =
        when (titleBar) {
            TitleBar.Large -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            TitleBar.Small, TitleBar.Logo -> TopAppBarDefaults.pinnedScrollBehavior()
        }
    Scaffold(
        modifier = modifier.nestedScroll(scrolling.nestedScrollConnection),
        topBar = {
            val back: @Composable () -> Unit = {
                if (onBack != null) {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            }
            when (titleBar) {
                TitleBar.Large -> {
                    LargeFlexibleTopAppBar(
                        title = { Text(title) },
                        navigationIcon = back,
                        actions = actions,
                        scrollBehavior = scrolling,
                    )
                }

                TitleBar.Small, TitleBar.Logo -> {
                    TopAppBar(
                        title = {
                            if (titleBar == TitleBar.Logo) {
                                ObehyLogo(Modifier.clearAndSetSemantics { contentDescription = title })
                            } else {
                                Text(title)
                            }
                        },
                        navigationIcon = back,
                        actions = actions,
                        scrollBehavior = scrolling,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            val readable =
                when (width) {
                    ContentWidth.Readable -> Modifier.widthIn(max = ObehyTheme.sizes.readableWidth)
                    ContentWidth.Full -> Modifier
                }
            Column(
                Modifier
                    .then(readable)
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier else Modifier.fillMaxSize())
                    .padding(horizontal = ObehyTheme.spacing.l, vertical = ObehyTheme.spacing.s),
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                content = content,
            )
        }
    }
}

/** Whether the window is wide enough for a list and its detail side by side (tablets, unfolded phones). */
@Composable
fun isWideWindow(): Boolean {
    val width =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    return width >= 840.dp
}
