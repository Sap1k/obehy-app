package cz.obehy.feature.map

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import cz.obehy.core.model.LiveMapSource
import cz.obehy.core.model.MapMode
import cz.obehy.core.model.Punctuality
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.rowSpans
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.DelayChip
import cz.obehy.core.ui.components.LineBadge
import cz.obehy.core.ui.components.LiveMark
import cz.obehy.core.ui.components.NoticeCard
import cz.obehy.core.ui.components.StateMessage
import cz.obehy.core.ui.components.trip.ConnectionsSection
import cz.obehy.core.ui.components.trip.NotesSection
import cz.obehy.core.ui.components.trip.Schedule
import cz.obehy.core.ui.components.trip.TrainSection
import cz.obehy.core.ui.components.trip.hasTrainSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The top of a sheet: its grip, and under it one row as a top app bar: back at the start when
 * there's somewhere to go back to (the board a trip was picked from), the [title], then Follow
 * when there's a vehicle to follow, and close.
 */
@Composable
internal fun SheetTop(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    following: Boolean = false,
    onFollow: (() -> Unit)? = null,
    title: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = ObehyTheme.spacing.s), Alignment.Center) {
            Surface(
                Modifier.size(ObehyTheme.sizes.mapGripWidth, ObehyTheme.sizes.mapGripHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {}
        }
        // As a top app bar: back at the start, the title, and the actions at the end, so the row is
        // never a band of nothing with a lone close button in it.
        Row(
            Modifier.fillMaxWidth().padding(
                start = if (onBack != null) ObehyTheme.spacing.xs else ObehyTheme.spacing.l,
                end = ObehyTheme.spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.map_back))
                }
            }
            if (title != null) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    content = title,
                )
            } else {
                Box(Modifier.weight(1f))
            }
            if (onFollow != null) FollowButton(following, onFollow)
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.map_close))
            }
        }
    }
}

/**
 * A sheet: its [top] stays put while the rest scrolls. The whole of it is at least a little
 * taller than its [peek] (a sheet no taller has no peek to rest at) and at most [maxHeight], so
 * pulled all the way up it stops short of the status bar. [sheetPeek] learns where in it the part
 * marked for the peek ends; back at its peek ([resting]) it scrolls back to how the peek shows it.
 */
@Composable
internal fun SheetFrame(
    maxHeight: Dp,
    peek: Dp,
    resting: Boolean,
    top: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    sheetPeek: SheetPeek? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = ObehyTheme.spacing
    val least = peek + spacing.xl
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // The peek ends a little below what it shows, above the navigation bar the sheet runs under.
    val below = spacing.m + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    SideEffect {
        sheetPeek?.scroll = scroll
        sheetPeek?.density = density
        sheetPeek?.below = below
    }
    LaunchedEffect(resting) {
        if (resting) {
            if (sheetPeek != null) sheetPeek.backToRest() else scroll.animateScrollTo(0)
        }
    }
    // At the peek with more opened out, a drag on what's in it scrolls it there; the sheet moves by its top.
    val inPlace = resting && sheetPeek?.opened == true
    val fling = ScrollableDefaults.flingBehavior()
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = least, max = maxOf(maxHeight, least))
            .onGloballyPositioned { sheetPeek?.frameAt(it) },
    ) {
        top()
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .then(if (inPlace) Modifier.scrollInPlace(scroll, fling, scope) else Modifier)
                .verticalScroll(scroll, enabled = !inPlace)
                .padding(horizontal = spacing.l)
                .padding(bottom = spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
            content = content,
        )
    }
}

/**
 * Keeps the map on the vehicle: an icon toggle among the bar's actions, tonal while off, Lime while
 * on, its shape changing with it.
 */
@Composable
private fun FollowButton(
    following: Boolean,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconToggleButton(
        checked = following,
        onCheckedChange = { onFollow() },
        modifier = modifier,
        shapes = IconButtonDefaults.toggleableShapes(),
        colors =
            IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Icon(
            Icons.Rounded.NearMe,
            contentDescription = stringResource(R.string.map_follow),
        )
    }
}

/**
 * A vehicle opened on the map, all of it in one sheet, as the web's panel: its line and where it
 * goes, a line saying how late it is, and its next stops, so the vehicle stays in view above.
 * Pulled up: the vehicle and its train, notes and connections. With railviz the same sheet,
 * saying where it should be rather than where it is.
 */
@Composable
internal fun VehicleSheet(
    selection: Selection,
    state: MapUiState,
    actions: MapActions,
    maxHeight: Dp,
    peek: Dp,
    sheetPeek: SheetPeek,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    val estimated = state.source == LiveMapSource.Railviz
    val detail = selection.detail
    val resting = state.sheet == SheetDetent.Peek
    SheetFrame(
        maxHeight,
        peek,
        resting,
        sheetPeek = sheetPeek,
        top = {
            SheetTop(
                onClose = actions::closeAll,
                onBack = if (state.returnTo != null) actions::close else null,
                following = state.following,
                onFollow = if (selection.vehicle != null) actions::toggleFollow else null,
                title = { Heading(selection, detail, Modifier.weight(1f)) },
            )
        },
        modifier = modifier,
    ) {
        Facts(selection, detail, estimated)
        when {
            detail != null -> {
                Trip(detail, selection, sheetPeek)
            }

            selection.failed -> {
                StateMessage(
                    Icons.Rounded.CloudOff,
                    stringResource(R.string.map_trip_failed),
                    action = stringResource(R.string.map_retry),
                    onAction = actions::retryTrip,
                )
            }

            else -> {
                Box(Modifier.fillMaxWidth().padding(spacing.l), Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}

/** The line, and where it goes. */
@Composable
private fun Heading(
    selection: Selection,
    detail: TripDetail?,
    modifier: Modifier = Modifier,
) {
    val vehicle = selection.vehicle
    val ride = detail?.ride
    val name = vehicle?.line ?: ride?.line ?: ride?.name ?: return
    val mode = vehicle?.mode ?: ride?.mode ?: return
    val headsign = vehicle?.headsign ?: ride?.headsign ?: ride?.to?.name
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(
            mode,
            name,
            vehicle?.color ?: ride?.color,
            vehicle?.textColor ?: ride?.textColor,
            cancelled = detail?.cancelled == true,
        )
        headsign?.let {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                Modifier.size(ObehyTheme.sizes.iconSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                it,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How late it runs, as a small chip as on the web, and beside it where it stops next. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(
    selection: Selection,
    detail: TripDetail?,
    estimated: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    val vehicle = selection.vehicle
    val minutes = vehicle?.delayMinutes ?: detail?.delayMinutes
    val cancelled = detail?.cancelled == true
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        DelayChip(minutes, cancelled = cancelled)
        val now = Instant.now()
        val seen = vehicle?.at?.seenAt
        when {
            estimated -> Unit

            vehicle != null && !vehicle.at.isStaleAt(now) -> LiveMark()

            // An old position says how old, as on the web.
            seen != null -> Note(stringResource(R.string.map_position_age, Duration.between(seen, now).toMinutes()))
        }
        // The train's own number, when its line doesn't say it (rj 52 on an Ex3).
        val number = vehicle?.tripShortName ?: detail?.ride?.tripShortName
        if (number != null && MapMode.of(vehicle?.mode ?: detail!!.ride.mode) == MapMode.Rail &&
            number != vehicle?.line
        ) {
            Note(number)
        }
        if (estimated) selection.near?.let { near -> Note(stringResource(R.string.map_should_be_near, near)) }
    }
}

@Composable
private fun Note(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A window of the stops around where the vehicle is; then everything else about the trip. */
@Composable
private fun Trip(
    trip: TripDetail,
    selection: Selection,
    sheetPeek: SheetPeek,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    val info = trip.info
    val positionFrom =
        selection.facts
            ?.positionFrom
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        Stops(trip, key = selection.trip, sheetPeek)
        trip.alerts.forEach { NoticeCard(it.title, text = it.text, severe = it.severe) }
        if (hasTrainSection(trip, selection.composition, positionFrom)) {
            TrainSection(trip, selection.composition, positionFrom = positionFrom)
        }
        if (info?.notes?.isEmpty == false) NotesSection(info.notes)
        if (info?.connections?.isNotEmpty() == true) ConnectionsSection(info.connections)
    }
}

/**
 * The next stops: three, then a fourth row past a dotted stretch that takes the later stops in
 * turn, all moving on by themselves as the vehicle goes. The stops before and after open with a
 * button each, all of that side at once.
 */
@Composable
private fun Stops(
    trip: TripDetail,
    key: Any?,
    sheetPeek: SheetPeek,
    modifier: Modifier = Modifier,
) {
    var earlier by remember(key) { mutableStateOf(false) }
    var later by remember(key) { mutableStateOf(false) }
    SideEffect { sheetPeek.opened = earlier || later }
    val last = trip.stops.lastIndex
    // Where the timetable's "here" mark is (or where a ride from a board starts, until it's
    // reached), with the stop before it above for context.
    val here = maxOf(trip.hereIndex(), trip.boarded?.first ?: 0)
    val first = (here - 1).coerceAtLeast(0)
    // Three in a row from there; past them, one row for the rest in turn. One or two left just
    // join: two rows take about the room of the dotted stretch and the row cycling them.
    val end = if (last - here <= AHEAD + 1) last else here + AHEAD - 1
    val cycling = if (later || end == last) IntRange.EMPTY else end + 1..last
    // The peek shows all of them as they first open; opened out, they scroll in place, the stop
    // before the vehicle's staying put.
    Column(modifier.fillMaxWidth().then(sheetPeek.mark(window = first to end))) {
        if (first > 0) {
            StopsToggle(
                if (earlier) {
                    stringResource(R.string.map_stops_hide_earlier)
                } else {
                    stringResource(R.string.map_stops_earlier, first)
                },
                up = !earlier,
                onClick = {
                    sheetPeek.pin()
                    earlier = !earlier
                },
            )
        }
        Schedule(
            trip,
            titled = false,
            shown = (if (earlier) 0 else first)..(if (later) last else end),
            cycling = cycling,
            rowModifier = { index -> if (index == first) sheetPeek.hold() else Modifier },
        )
        if (end < last) {
            StopsToggle(
                if (later) {
                    stringResource(R.string.map_stops_hide_later)
                } else {
                    stringResource(R.string.map_stops_later, last - end)
                },
                up = later,
                onClick = { later = !later },
            )
        }
    }
}

@Composable
private fun StopsToggle(
    text: String,
    up: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = ButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraSmallContainerHeight)
    // Its chevron on the sheet's edge, in line with everything else, not set in by the button's padding.
    val inset = padding.calculateStartPadding(LocalLayoutDirection.current)
    TextButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
        modifier = modifier.offset(x = -inset).heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
        contentPadding = padding,
    ) {
        Icon(
            if (up) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            Modifier.size(ButtonDefaults.IconSize),
        )
        Text(text, Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

/**
 * The stop the timetable's "here" mark is at: each stop holds from halfway after the one before to
 * halfway to the next ([rowSpans], as the trip's timetable draws its progress), so the window
 * moves on when the timetable does, not the moment a stop's time ticks past.
 */
private fun TripDetail.hereIndex(now: Instant = Instant.now()): Int {
    val times =
        stops.indices.map { index ->
            val call = stops[index].call
            val ends = index == stops.lastIndex || index == boarded?.last
            (if (ends) call.arrival ?: call.departure else call.departure ?: call.arrival)?.expected
        }
    val spans = times.rowSpans()
    if (spans.isEmpty()) return 0
    val holding = spans.indexOfFirst { it.holds(now) }
    return when {
        holding >= 0 -> holding
        now < spans.first().from -> 0
        else -> stops.lastIndex
    }
}

/** How many of the next stops show in a row; the fourth row takes the later ones in turn. */
private const val AHEAD = 3

/**
 * Scrolls [scroll] by a vertical drag itself, taking the drag so the sheet it's in doesn't: a sheet
 * would open up first and only then let what's in it scroll.
 */
private fun Modifier.scrollInPlace(
    scroll: ScrollState,
    fling: FlingBehavior,
    scope: CoroutineScope,
): Modifier =
    pointerInput(scroll, fling) {
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onVerticalDrag = { change, drag ->
                change.consume()
                tracker.addPointerInputChange(change)
                scroll.dispatchRawDelta(-drag)
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().y
                scope.launch { scroll.scroll { with(fling) { performFling(-velocity) } } }
            },
        )
    }
