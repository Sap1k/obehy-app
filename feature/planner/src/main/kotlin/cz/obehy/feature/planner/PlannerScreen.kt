package cz.obehy.feature.planner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.obehy.core.map.PreloadRouteMaps
import cz.obehy.core.map.RouteMap
import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.ui.ContentWidth
import cz.obehy.core.ui.ObehyScreen
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.SharedBounds
import cz.obehy.core.ui.TitleBar
import cz.obehy.core.ui.components.StaggeredEntry
import cz.obehy.core.ui.isWideWindow
import java.time.Duration
import java.time.Instant

@Composable
internal fun PlannerDestination(
    viewModel: PlannerViewModel,
    onOpen: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wide = isWideWindow()
    val search = state.placeSearch
    BackHandler(enabled = search != null) { viewModel.act(PlannerAction.ClosePlaceSearch) }
    if (search != null) {
        PlaceSearchScreen(
            search,
            onType = { viewModel.act(PlannerAction.Type(it)) },
            onChoose = { viewModel.act(PlannerAction.Choose(it)) },
            onClose = { viewModel.act(PlannerAction.ClosePlaceSearch) },
        )
    } else {
        PlannerScreen(
            state,
            wide = wide,
            onAction = viewModel::act,
            onOpen = {
                viewModel.act(PlannerAction.Select(it))
                if (!wide) onOpen()
            },
        )
    }
}

private enum class Sheet { Time, Options }

/** How soon after a search its results may still animate in. */
private val ARRIVAL: Duration = Duration.ofSeconds(1)

/**
 * The search form with the results under it. On a wide window the journey picked from the
 * results opens beside them.
 */
@Composable
internal fun PlannerScreen(
    state: PlannerState,
    wide: Boolean,
    onAction: (PlannerAction) -> Unit,
    onOpen: (Itinerary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    ObehyScreen(
        stringResource(R.string.planner_title),
        modifier = modifier,
        titleBar = TitleBar.Small,
        width = if (wide) ContentWidth.Full else ContentWidth.Readable,
        scrollable = false,
        verticalArrangement = Arrangement.Top,
        actions = {
            if (state.canReset) {
                IconButton(onClick = { onAction(PlannerAction.Reset) }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.new_search))
                }
            }
        },
    ) {
        val results: @Composable (Modifier) -> Unit = { paneModifier ->
            ResultsList(state, wide, onAction, onOpen, onTime = {
                sheet = Sheet.Time
            }, onOptions = { sheet = Sheet.Options }, paneModifier)
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xl)) {
                results(Modifier.width(ObehyTheme.sizes.listPane))
                DetailPane(state.open, state.query, Modifier.weight(1f))
            }
        } else {
            results(Modifier.fillMaxSize())
        }
    }
    when (sheet) {
        Sheet.Time -> {
            TimeSheet(
                state.query,
                onDone = { time, arriveBy ->
                    onAction(PlannerAction.SetTime(time, arriveBy))
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }

        Sheet.Options -> {
            OptionsSheet(state.options, onChange = { onAction(PlannerAction.SetOptions(it)) }, onDismiss = {
                sheet =
                    null
            })
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun ResultsList(
    state: PlannerState,
    wide: Boolean,
    onAction: (PlannerAction) -> Unit,
    onOpen: (Itinerary) -> Unit,
    onTime: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = state.results as? Results.Loaded
    // The detail pane of a wide screen has its own size, and is open anyway.
    if (loaded != null && !wide) PreloadRouteMaps(remember(loaded.itineraries) { loaded.itineraries.map { it.legs } })
    PullToRefreshBox(
        isRefreshing = loaded?.refreshing == true,
        onRefresh = { onAction(PlannerAction.RefreshResults) },
        modifier = modifier,
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            item(key = "form") {
                SearchForm(
                    state.query,
                    state.options,
                    searching = state.results is Results.Loading,
                    onAction,
                    onTime,
                    onOptions,
                    Modifier.padding(bottom = ObehyTheme.spacing.m),
                )
            }
            when (val results = state.results) {
                Results.Idle, Results.Loading -> {
                    Unit
                }

                Results.Failed -> {
                    item(key = "failed") { Note(R.string.results_error) { onAction(PlannerAction.Search) } }
                }

                is Results.Loaded -> {
                    if (results.itineraries.isEmpty()) {
                        item(key = "none") { Note(R.string.results_none) }
                    } else {
                        item(key = "fresh") {
                            Row(
                                Modifier.padding(bottom = ObehyTheme.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (results.failed) {
                                        stringResource(R.string.refresh_failed) + " · " +
                                            updatedAgo(results.fetchedAt)
                                    } else {
                                        updatedAgo(results.fetchedAt)
                                    },
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (results.earlierCursor != null) {
                                    TextButton(
                                        onClick = { onAction(PlannerAction.Earlier) },
                                    ) { Text(stringResource(R.string.earlier)) }
                                }
                            }
                        }
                    }
                    itemsIndexed(results.itineraries, key = { _, it -> it.key }) { index, itinerary ->
                        // Only a search that just landed arrives; coming back to the list doesn't replay it.
                        val arriving = remember { Duration.between(results.fetchedAt, Instant.now()) < ARRIVAL }
                        StaggeredEntry(
                            index,
                            enabled = arriving,
                            Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        ) {
                            SharedBounds(itinerary.key) {
                                ResultCard(
                                    itinerary,
                                    index,
                                    results.itineraries.size,
                                    // Only beside the list is the open journey still in view to point at.
                                    selected = wide && itinerary.key == state.open?.itinerary?.key,
                                    onClick = { onOpen(itinerary) },
                                )
                            }
                        }
                    }
                    if (results.loadingMore) {
                        item(key = "more") { Centered { LoadingIndicator() } }
                    } else if (results.laterCursor != null && results.itineraries.isNotEmpty()) {
                        item(key = "later") {
                            Centered {
                                TextButton(
                                    onClick = { onAction(PlannerAction.Later) },
                                ) { Text(stringResource(R.string.later)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Note(
    text: Int,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = ObehyTheme.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** The journey open beside the results on a wide window: its map, then its timeline. */
@Composable
private fun DetailPane(
    open: OpenJourney?,
    query: JourneyQuery,
    modifier: Modifier = Modifier,
) {
    if (open == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.pick_journey), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m),
    ) {
        RouteMap(open.itinerary.legs, Modifier.fillMaxWidth().height(ObehyTheme.sizes.heroMap), interactive = true)
        Text(
            journeySummary(open),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ItineraryTimeline(open.itinerary, query.from?.name, query.to?.name)
    }
}
