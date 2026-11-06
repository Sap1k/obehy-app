package cz.obehy.feature.planner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import cz.obehy.core.ui.components.ObehyPullToRefresh
import cz.obehy.core.ui.components.StaggeredEntry
import cz.obehy.core.ui.components.StateMessage
import cz.obehy.core.ui.isWideWindow
import kotlinx.coroutines.launch
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
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            picker,
            enter =
                fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.defaultSpatialSpec(), PICKER_SCALE, PICKER_ORIGIN),
            exit =
                fadeOut(motion.fastEffectsSpec()) +
                    scaleOut(motion.fastSpatialSpec(), PICKER_SCALE, PICKER_ORIGIN),
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
        titleBar = TitleBar.Logo,
        width = if (wide) ContentWidth.Full else ContentWidth.Readable,
        scrollable = false,
        verticalArrangement = Arrangement.Top,
        actions = {
            if (state.canReset) {
                IconButton(onClick = { onAction(PlannerAction.Reset) }, shapes = IconButtonDefaults.shapes()) {
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
    SearchOutcomeHaptics(state.results)
    val scope = rememberCoroutineScope()
    val pastForm by remember(list) { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    ObehyPullToRefresh(loaded?.refreshing == true, { onAction(PlannerAction.RefreshResults) }, modifier) {
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
                    itemsIndexed(state.suggestions, key = {
                        _,
                        it,
                        ->
                        "${it.from.id}>${it.to.id}"
                    }) { index, suggestion ->
                        SuggestionRow(
                            suggestion,
                            index,
                            state.suggestions.size,
                            onClick = { onAction(PlannerAction.UseSuggestion(suggestion)) },
                            onLongClick = { onAction(PlannerAction.ForgetSuggestion(suggestion)) },
                        )
                    }
                }

                Results.Loading -> {
                    Unit
                }

                Results.Failed -> {
                    item(key = "failed") {
                        Note(R.string.results_error, Icons.Rounded.CloudOff) { onAction(PlannerAction.Search) }
                    }
                }

                Results.NoLocation -> {
                    item(key = "no-location") {
                        Note(R.string.results_no_location, Icons.Rounded.LocationOff) {
                            onAction(PlannerAction.Search)
                        }
                    }
                }

                is Results.Loaded -> {
                    if (results.itineraries.isEmpty()) {
                        item(key = "none") { Note(R.string.results_none, Icons.Rounded.SearchOff) }
                    } else {
                        item(key = "fresh") {
                            Row(
                                Modifier.padding(bottom = ObehyTheme.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FlowRow(
                                    Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                                ) {
                                    if (results.failed) {
                                        Text(
                                            stringResource(R.string.refresh_failed),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    Text(
                                        updatedAgo(results.fetchedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (results.earlierCursor != null) {
                                    TextButton(
                                        onClick = { onAction(PlannerAction.Earlier) },
                                        shapes = ButtonDefaults.shapes(),
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
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text(stringResource(R.string.later)) }
                            }
                        }
                    }
                }
            }
        }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            pastForm && state.query.isComplete,
            Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { -it },
            exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it },
        ) {
            QuerySummary(state.query, onClick = { scope.launch { list.animateScrollToItem(0) } })
        }
    }
}

/** A short buzz when a search the user is waiting on lands, or fails. */
@Composable
private fun SearchOutcomeHaptics(results: Results) {
    val haptics = LocalHapticFeedback.current
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(results) {
        when {
            results is Results.Loading -> {
                waiting = true
            }

            !waiting -> {
                Unit
            }

            results is Results.Loaded -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                waiting = false
            }

            else -> {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                waiting = false
            }
        }
    }
}

/** The search form folded down to what's being searched for, over the results; a tap goes back up to it. */
@Composable
private fun QuerySummary(
    query: JourneyQuery,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val from = query.from?.label().orEmpty()
    val to = query.to?.label().orEmpty()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = ObehyTheme.spacing.xs,
    ) {
        Row(
            Modifier.padding(horizontal = ObehyTheme.spacing.l, vertical = ObehyTheme.spacing.s),
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.suggested_journey, from, to),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    query.timeLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
        }
    }
}

/** A journey to search for with one tap; a long press stops offering it. */
@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        },
        onLongClickLabel = stringResource(R.string.forget),
        colors = ListItemDefaults.segmentedColors(containerColor = ObehyTheme.cardColor),
    ) {
        Text(
            stringResource(R.string.suggested_journey, suggestion.from.label(), suggestion.to.label()),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    StateMessage(
        icon,
        stringResource(text),
        modifier,
        action = onRetry?.let { stringResource(R.string.retry) },
        onAction = { onRetry?.invoke() },
    )
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
            StateMessage(Icons.Rounded.Directions, stringResource(R.string.pick_journey))
        }
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m),
    ) {
        RouteMap(open.itinerary.legs, Modifier.fillMaxWidth().height(ObehyTheme.sizes.heroMap), interactive = true)
        JourneySummary(open)
        JourneyBanner(open.itinerary, query.to?.label(), onReplan)
        ItineraryTimeline(open.itinerary, query.from?.label(), query.to?.label(), onOpenTrip)
    }
}
