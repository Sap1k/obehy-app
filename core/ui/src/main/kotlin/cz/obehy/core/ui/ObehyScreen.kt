package cz.obehy.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/** How a screen's title bar looks. */
enum class TitleBar {
    /** A large title that collapses as the content scrolls: top-level screens (Settings). */
    Large,

    /** A single-line title: detail and placeholder screens. */
    Small,
}

/**
 * The frame of every screen: a top bar (which keeps content clear of the status bar), and the
 * content in a column of readable width with the standard side padding. With [scrollable] the
 * column scrolls and the [TitleBar.Large] title collapses as it does; without it the column
 * fills the space below the bar (for centred or self-scrolling content).
 */
@Composable
fun ObehyScreen(
    title: String,
    modifier: Modifier = Modifier,
    titleBar: TitleBar = TitleBar.Large,
    scrollable: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(ObehyTheme.spacing.xl),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val collapse = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(collapse.nestedScrollConnection),
        topBar = {
            when (titleBar) {
                TitleBar.Large -> LargeFlexibleTopAppBar(title = { Text(title) }, scrollBehavior = collapse)
                TitleBar.Small -> TopAppBar(title = { Text(title) })
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
            Column(
                Modifier
                    .widthIn(max = ObehyTheme.sizes.readableWidth)
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
