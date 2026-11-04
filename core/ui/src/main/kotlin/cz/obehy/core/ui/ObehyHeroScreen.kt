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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow

/**
 * A screen led by a picture (a journey's map): the [hero] heads the content and scrolls away with
 * it under the top bar, which keeps the [title] and [subtitle], the back arrow and [actions].
 * Tapping the hero calls [onHeroClick]; pulling the content down calls [onRefresh].
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
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
        val body: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ObehyTheme.sizes.heroMap)
                        .then(if (onHeroClick != null) Modifier.clickable(onClick = onHeroClick) else Modifier),
                ) { hero() }
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
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { body() }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) { body() }
        }
    }
}
