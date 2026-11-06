package cz.obehy.feature.planner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.obehy.core.map.RouteMap
import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.Leg
import cz.obehy.core.model.Suggestion
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
    onOpenTrip: (Leg.Transit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The list comes back to its place only if it first appears with its results.
    if (state.restoring) return
    val wide = isWideWindow()
    val search = state.placeSearch
    BackHandler(enabled = search != null) { viewModel.act(PlannerAction.ClosePlaceSearch) }
    LifecycleResumeEffect(viewModel) {
        viewModel.act(PlannerAction.CheckLocation)
        onPauseOrDispose {}
    }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(search == null) { if (search == null) keyboard?.hide() }
    // The picker comes in over the planner, which stays as it is underneath: rebuilding the results
    // for every pick shows as a stutter. It moves as a whole picture, so no frame lays anything out.
    val picker = remember { MutableTransitionState(false) }
    picker.targetState = search != null
    var shown by remember { mutableStateOf(search) }
    if (search != null) shown = search
    val covered = picker.isIdle && picker.currentState
    Box(Modifier.fillMaxSize()) {
        PlannerScreen(
            state,
            wide = wide,
            onAction = viewModel::act,
            onOpen = {
                viewModel.act(PlannerAction.Select(it))
                if (!wide) onOpen()
            },
            onOpenTrip = onOpenTrip,
            modifier =
                Modifier
                    .graphicsLayer { alpha = if (covered) 0f else 1f }
                    .then(if (covered) Modifier.clearAndSetSemantics {} else Modifier),
        )
        AnimatedVisibility(
            picker,
            enter = fadeIn(tween(PICKER_MS)) + scaleIn(tween(PICKER_MS), PICKER_SCALE, PICKER_ORIGIN),
            exit = fadeOut(tween(PICKER_MS)) + scaleOut(tween(PICKER_MS), PICKER_SCALE, PICKER_ORIGIN),
        ) {
            shown?.let {
                PlaceSearchScreen(
                    it,
                    onType = { text -> viewModel.act(PlannerAction.Type(text)) },
                    onChoose = { place -> viewModel.act(PlannerAction.Choose(place)) },
                    onForget = { place -> viewModel.act(PlannerAction.ForgetPlace(place)) },
                    onClose = { viewModel.act(PlannerAction.ClosePlaceSearch) },
                    onLocationAnswer = { viewModel.act(PlannerAction.CheckLocation) },
                )
            }
        }
    }
}

private const val PICKER_MS = 220
private const val PICKER_SCALE = 0.94f

/** The picker grows out of the top of the screen, where the search form is. */
private val PICKER_ORIGIN = TransformOrigin(0.5f, 0.2f)

private enum class Sheet { Time, Options }

/** Line names kept measured for the results list. */
private const val MEASURED_NAMES = 64

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
    onOpenTrip: (Leg.Transit) -> Unit,
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
                DetailPane(
                    state.open,
                    state.query,
                    onOpenTrip,
                    onReplan = { onAction(PlannerAction.Replan) },
                    Modifier.weight(1f),
                )
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
    val list = rememberLazyListState()
    // Lines repeat from one result to the next, so the rows share their measurements.
    val measurer = rememberTextMeasurer(cacheSize = MEASURED_NAMES)
    if (loaded != null && !wide) PreloadResultMaps(loaded.itineraries, list, paused = state.placeSearch != null)
    PullToRefreshBox(
        isRefreshing = loaded?.refreshing == true,
        onRefresh = { onAction(PlannerAction.RefreshResults) },
        modifier = modifier,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
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
                Results.Idle -> {
                    if (state.suggestions.isNotEmpty()) {
                        item(key = "suggested") { SectionTitle(R.string.suggested) }
                    }
                    items(state.suggestions, key = { "${it.from.id}>${it.to.id}" }) { suggestion ->
                        SuggestionRow(
                            suggestion,
                            onClick = { onAction(PlannerAction.UseSuggestion(suggestion)) },
                            onLongClick = { onAction(PlannerAction.ForgetSuggestion(suggestion)) },
                        )
                    }
                }

                Results.Loading -> {
                    Unit
                }

                Results.Failed -> {
                    item(key = "failed") { Note(R.string.results_error) { onAction(PlannerAction.Search) } }
                }

                Results.NoLocation -> {
                    item(key = "no-location") { Note(R.string.results_no_location) { onAction(PlannerAction.Search) } }
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
                                    measurer = measurer,
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

/** A journey to search for with one tap; a long press stops offering it. */
@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                stringResource(R.string.suggested_journey, suggestion.from.label(), suggestion.to.label()),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier =
            modifier.combinedClickable(
                onLongClickLabel = stringResource(R.string.forget),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    )
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
    onOpenTrip: (Leg.Transit) -> Unit,
    onReplan: () -> Unit,
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
        ItineraryTimeline(open.itinerary, query.from?.label(), query.to?.label(), onOpenTrip, onReplan)
    }
}
