package cz.obehy.feature.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cz.obehy.core.map.GLIDE_MS
import cz.obehy.core.map.LiveMap
import cz.obehy.core.map.LivePath
import cz.obehy.core.map.PathStop
import cz.obehy.core.model.BoardSide
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.LiveMapSource
import cz.obehy.core.model.MapBounds
import cz.obehy.core.model.MapMode
import cz.obehy.core.model.Mode
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripRef
import cz.obehy.core.model.VehicleHit
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.icon
import kotlinx.coroutines.flow.drop
import java.time.Instant

@Composable
internal fun MapDestination(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepLive() }
    }
    BackHandler(state.search != null) { viewModel.closeSearch() }
    BackHandler(state.search == null && (state.selection != null || state.stop != null)) {
        if (state.selection != null) viewModel.close() else viewModel.closeStop()
    }
    // The screen sees only what it can ask for, not the view model behind it.
    val actions: MapActions = remember(viewModel) { viewModel }
    MapScreen(state, actions, modifier)
}

/** What the map screen can be asked to do. */
internal interface MapActions {
    fun onViewport(
        bounds: MapBounds,
        zoom: Double,
    )

    fun onGesture(bounds: MapBounds)

    fun selectVehicle(id: String)

    fun selectStop(id: String)

    fun selectTrip(trip: TripRef)

    fun close()

    fun closeStop()

    fun closeAll()

    fun toggleFollow()

    fun toggleMode(mode: MapMode)

    fun locate()

    fun openSearch()

    fun setSheet(detent: SheetDetent)

    fun setExplaining(open: Boolean)

    fun retryTrip()

    fun loadBoard()

    fun showBoard(side: BoardSide)

    fun moreBoard()

    fun search(query: String)

    fun selectHit(hit: VehicleHit)

    fun closeSearch()
}

/**
 * The live map, edge to edge under the status bar, with nothing on it but a search pill and the
 * modes chip at the top and the locate button: tapping a vehicle opens its whole trip in a sheet,
 * on the map, so following it is a tap away. Its own scaffold, as the map is the screen.
 */
@Composable
internal fun MapScreen(
    state: MapUiState,
    actions: MapActions,
    modifier: Modifier = Modifier,
) {
    val opened = state.selection != null || state.stop != null
    // A fling goes on to the next height and no further: from all the way up, it stops at the
    // peek; from the peek, it closes. Nothing is ever refused, so a drag never springs back.
    val sheet =
        rememberBottomSheetScaffoldState(
            rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false),
        )
    LaunchedEffect(opened, state.selection?.trip, state.stop?.stop?.id) {
        if (opened) sheet.bottomSheetState.partialExpand() else sheet.bottomSheetState.hide()
    }
    // Asked to drop to its peek (following); never while closing, which goes all the way down.
    LaunchedEffect(state.sheet) {
        val now = sheet.bottomSheetState.currentValue
        if (opened && state.sheet == SheetDetent.Peek && now == SheetValue.Expanded) {
            sheet.bottomSheetState.partialExpand()
        }
    }
    // Where the sheet comes to rest decides, never where a drag passes on its way: swiped away, it
    // closes everything; come to rest open with nothing left to show, it goes.
    val openNow by rememberUpdatedState(opened)
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.bottomSheetState.currentValue }.drop(1).collect { value ->
            when (value) {
                SheetValue.Expanded -> actions.setSheet(SheetDetent.Open)
                SheetValue.PartiallyExpanded -> actions.setSheet(SheetDetent.Peek)
                SheetValue.Hidden -> Unit
            }
            if (value == SheetValue.Hidden && openNow) actions.closeAll()
            if (value != SheetValue.Hidden && !openNow) sheet.bottomSheetState.hide()
        }
    }
    // What the sheet shows: kept after it's closed until it has slid all the way down, so closing animates.
    val content = state.selection?.let { SheetContent.Vehicle(it) } ?: state.stop?.let { SheetContent.Stop(it) }
    var kept by remember { mutableStateOf(content) }
    LaunchedEffect(content) { if (content != null) kept = content }
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.bottomSheetState.currentValue }.collect { if (it == SheetValue.Hidden) kept = null }
    }
    val showing = content ?: kept
    BoxWithConstraints(modifier) {
        // The sheet stops short of the status bar when pulled all the way up.
        val sheetMax =
            maxHeight - WindowInsets.statusBars.asPaddingValues().calculateTopPadding() - ObehyTheme.spacing.l
        // The peek fits what the sheet marks for it (a vehicle's next stops, a stop's first
        // departures), measured on this screen, so it never cuts a row off (see SheetPeek); never
        // more than a share of the screen, so the map stays.
        val sheetPeek = remember(showing?.key) { SheetPeek() }
        val peek = minOf(sheetPeek.height ?: ObehyTheme.sizes.mapSheetPeek, sheetMax * PEEK_SHARE)
        val resting = state.sheet == SheetDetent.Peek
        BottomSheetScaffold(
            sheetContent = {
                val motion = MaterialTheme.motionScheme
                AnimatedContent(
                    showing,
                    contentKey = { it?.key },
                    transitionSpec = {
                        (
                            fadeIn(motion.defaultEffectsSpec()) +
                                scaleIn(motion.defaultSpatialSpec(), initialScale = SHEET_SCALE)
                        ).togetherWith(fadeOut(motion.fastEffectsSpec()))
                            .using(SizeTransform(clip = false))
                    },
                    label = "sheet",
                ) { showing ->
                    // Each keeps the peek it opened with: one on its way out mustn't measure for the next.
                    val ownPeek = remember(showing?.key) { sheetPeek }
                    when (showing) {
                        is SheetContent.Vehicle -> {
                            VehicleSheet(showing.selection, state, actions, sheetMax, peek, ownPeek)
                        }

                        is SheetContent.Stop -> {
                            StopSheet(showing.stop, actions, sheetMax, peek, ownPeek, resting)
                        }

                        null -> {
                            Unit
                        }
                    }
                }
            },
            scaffoldState = sheet,
            sheetPeekHeight = if (showing != null) peek else 0.dp,
            // The sheet draws its own top, inside its height.
            sheetDragHandle = null,
        ) {
            Box(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                var topHeight by remember { mutableStateOf(0.dp) }
                val selected = state.selection?.vehicle
                val detail = state.selection?.detail
                LiveMap(
                    vehicles = state.shown,
                    start = remember { state.me ?: LatLon(CZECHIA_LAT, CZECHIA_LON) },
                    startZoom = remember { if (state.me != null) START_ZOOM_HERE else START_ZOOM_COUNTRY },
                    onViewport = actions::onViewport,
                    onVehicleClick = actions::selectVehicle,
                    onStopClick = actions::selectStop,
                    modifier = Modifier.fillMaxSize(),
                    stops = state.stops,
                    selected = selected,
                    path =
                        detail?.let { trip ->
                            LivePath(
                                trip.ride.path.ifEmpty { trip.stops.mapNotNull { it.call.position } },
                                trip.ride.color,
                                estimated = state.source == LiveMapSource.Railviz,
                                stops = trip.pathStops(),
                            )
                        },
                    me = state.me,
                    from = state.selection?.from ?: state.stop?.stop?.position,
                    camera = state.camera,
                    // Railviz places its vehicles every second: each glide takes just that long.
                    glideMillis = if (state.source == LiveMapSource.Railviz) RAILVIZ_GLIDE_MS else GLIDE_MS,
                    // A vehicle brought into view lands in the map above the sheet, not under it.
                    bottomInset = if (opened) peek else 0.dp,
                    // Nor under the controls at the top.
                    topInset = topHeight,
                    onGesture = actions::onGesture,
                )
                TopControls(
                    state,
                    actions,
                    Modifier
                        .align(
                            Alignment.TopCenter,
                        ).onSizeChanged { topHeight = with(density) { it.height.toDp() } },
                )
                AnimatedVisibility(
                    !opened,
                    Modifier.align(Alignment.BottomEnd).padding(ObehyTheme.spacing.l),
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    FloatingActionButton(onClick = actions::locate) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.map_locate))
                    }
                }
            }
        }
        AnimatedVisibility(
            state.search != null,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            state.search?.let { search ->
                VehicleSearch(search, actions::search, actions::selectHit, actions::closeSearch)
            }
        }
        if (state.explaining) Explainer(onDismiss = { actions.setExplaining(false) })
    }
}

/** Search and the modes at the top; with railviz, where its positions come from instead of search. */
@Composable
private fun TopControls(
    state: MapUiState,
    actions: MapActions,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    Column(
        modifier.statusBarsPadding().padding(horizontal = spacing.m, vertical = spacing.s).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            when (state.source) {
                LiveMapSource.Railviz -> {
                    EstimatedChip(onClick = { actions.setExplaining(true) })
                    Box(Modifier.weight(1f))
                }

                else -> {
                    SearchPill(onClick = actions::openSearch, Modifier.weight(1f))
                }
            }
            ModesChip(state.modes, actions::toggleMode)
        }
        AnimatedVisibility(
            state.failed,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                Row(
                    Modifier.padding(horizontal = spacing.m, vertical = spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.SyncProblem,
                        contentDescription = null,
                        Modifier.size(ObehyTheme.sizes.iconSmall),
                    )
                    Text(stringResource(R.string.map_failed), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** "Find a vehicle": opens the search over the map. */
@Composable
private fun SearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = ObehyTheme.sizes.iconBadge).semantics { role = Role.Button },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = ObehyTheme.spacing.xs,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = ObehyTheme.spacing.l),
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Text(
                stringResource(R.string.map_search),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The modes that are on, as their icons; opens a small menu to turn each on or off. */
@Composable
private fun ModesChip(
    modes: Set<MapMode>,
    onToggle: (MapMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val all = modes.size == MapMode.entries.size
    val label = stringResource(if (all) R.string.map_modes_all else R.string.map_modes)
    val scheme = MaterialTheme.colorScheme
    Box(modifier) {
        Surface(
            modifier = Modifier.heightIn(min = ObehyTheme.sizes.iconBadge),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (all) scheme.surfaceContainerHighest else scheme.primaryContainer,
            shadowElevation = ObehyTheme.spacing.xs,
            contentColor = if (all) scheme.onSurface else scheme.onPrimaryContainer,
            onClick = { open = true },
        ) {
            Row(
                Modifier.padding(horizontal = ObehyTheme.spacing.m).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MapMode.entries.filter { it in modes && it != MapMode.Other }.forEach { mode ->
                    Icon(mode.symbol, contentDescription = null, Modifier.size(ObehyTheme.sizes.icon))
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = label)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            MapMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.title)) },
                    onClick = { onToggle(mode) },
                    leadingIcon = { Icon(mode.symbol, contentDescription = null) },
                    trailingIcon = {
                        if (mode in modes) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

/** With railviz: says the positions come from the timetable, and opens why. */
@Composable
private fun EstimatedChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = ObehyTheme.sizes.iconBadge),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = ObehyTheme.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, Modifier.size(ObehyTheme.sizes.iconSmall))
            Text(stringResource(R.string.map_estimated_chip), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.Info, contentDescription = null, Modifier.size(ObehyTheme.sizes.iconSmall))
        }
    }
}

/** Why railviz's vehicles are only where they should be. */
@Composable
private fun Explainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            Modifier
                .padding(horizontal = ObehyTheme.spacing.l)
                .padding(bottom = ObehyTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m),
        ) {
            Text(stringResource(R.string.map_estimated_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.map_estimated_text), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.map_estimated_ok)) }
        }
    }
}

internal val MapMode.symbol: ImageVector get() = mode.icon

internal val MapMode.mode: Mode
    get() =
        when (this) {
            MapMode.Rail -> Mode.RegionalRail
            MapMode.Metro -> Mode.Subway
            MapMode.Tram -> Mode.Tram
            MapMode.Bus -> Mode.Bus
            MapMode.Other -> Mode.Ferry
        }

internal val MapMode.title: Int
    get() =
        when (this) {
            MapMode.Rail -> R.string.map_mode_rail
            MapMode.Metro -> R.string.map_mode_metro
            MapMode.Tram -> R.string.map_mode_tram
            MapMode.Bus -> R.string.map_mode_bus
            MapMode.Other -> R.string.map_mode_other
        }

/** The trip's stops for the map: passed as Oběhy says, or else by the times. */
private fun TripDetail.pathStops(): List<PathStop> {
    val now = Instant.now()
    return stops.mapNotNull { stop ->
        val at = stop.call.position ?: return@mapNotNull null
        val passed = stop.passed ?: ((stop.call.departure ?: stop.call.arrival)?.expected?.isBefore(now) == true)
        PathStop(at, stop.call.name, passed)
    }
}

/** What the sheet holds; [key] tells one from another, so a new one animates in. */
private sealed interface SheetContent {
    val key: Any

    data class Vehicle(
        val selection: Selection,
    ) : SheetContent {
        override val key: Any get() = selection.trip ?: selection.vehicleId.orEmpty()
    }

    data class Stop(
        val stop: StopState,
    ) : SheetContent {
        override val key: Any get() = stop.stop.id
    }
}

/** The most of the screen a sheet's peek takes. */
private const val PEEK_SHARE = 0.6f

/** How small a sheet's new content starts as it fades in. */
private const val SHEET_SCALE = 0.96f

private const val RAILVIZ_GLIDE_MS = 1_000
private const val CZECHIA_LAT = 49.8
private const val CZECHIA_LON = 15.5
private const val START_ZOOM_COUNTRY = 7.0
private const val START_ZOOM_HERE = 13.0
