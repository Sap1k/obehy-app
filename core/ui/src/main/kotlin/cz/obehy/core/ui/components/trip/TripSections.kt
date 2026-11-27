package cz.obehy.core.ui.components.trip

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cz.obehy.core.model.CallFlag
import cz.obehy.core.model.Composition
import cz.obehy.core.model.CompositionCall
import cz.obehy.core.model.CompositionStatus
import cz.obehy.core.model.CompositionUnit
import cz.obehy.core.model.TimePoint
import cz.obehy.core.model.TripConnection
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripNotes
import cz.obehy.core.model.TripStop
import cz.obehy.core.model.TripVehicleInfo
import cz.obehy.core.model.rowSpans
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R
import cz.obehy.core.ui.components.CompositionStrip
import cz.obehy.core.ui.components.InfoChip
import cz.obehy.core.ui.components.InfoRow
import cz.obehy.core.ui.components.PlatformLabel
import cz.obehy.core.ui.components.RouteMark
import cz.obehy.core.ui.components.RouteProgress
import cz.obehy.core.ui.components.RouteRow
import cz.obehy.core.ui.components.RouteStroke
import cz.obehy.core.ui.components.ScheduleTime
import cz.obehy.core.ui.components.ScrollingChipRow
import cz.obehy.core.ui.components.SegmentedRows
import cz.obehy.core.ui.components.VehicleId
import cz.obehy.core.ui.components.featureIcon
import cz.obehy.core.ui.components.passedLabel
import cz.obehy.core.ui.components.quietStroke
import cz.obehy.core.ui.components.rememberPassed
import cz.obehy.core.ui.components.rememberPlatformWidth
import cz.obehy.core.ui.components.rememberScheduleTimeWidth
import cz.obehy.core.ui.components.rememberSmoothNow
import cz.obehy.core.ui.components.rideHere
import cz.obehy.core.ui.components.routeLineColor
import cz.obehy.core.ui.components.unitName
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
private fun Section(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMediumEmphasized) }
        content()
    }
}

/** The time that counts at the [index]th stop: arriving where the ride ends, else leaving. */
private fun TripDetail.timeAt(index: Int): TimePoint? {
    val call = stops[index].call
    val ends = index == stops.lastIndex || index == boarded?.last
    return if (ends) call.arrival ?: call.departure else call.departure ?: call.arrival
}

/** The columns every row of a schedule lines up on. */
private class ScheduleColumns(
    val time: Dp,
    val platform: Dp,
)

/**
 * Every stop of the trip on one grid, the stretch the journey rides on in its colour and the rest
 * quiet. [titled] heads it as a section; only the stops in [shown] are drawn, the line running on
 * past the first and last of them to say there are more. The stops in [cycling], after [shown],
 * take turns in one more row, reached by a dotted stretch of line for the stops between. Each
 * stop's row also takes [rowModifier] of its index.
 */
@Composable
fun Schedule(
    trip: TripDetail,
    modifier: Modifier = Modifier,
    titled: Boolean = true,
    shown: IntRange = trip.stops.indices,
    cycling: IntRange = IntRange.EMPTY,
    rowModifier: (Int) -> Modifier = { Modifier },
) {
    val ride = RouteStroke(routeLineColor(trip.ride.color))
    val quiet = quietStroke()
    val times = trip.stops.indices.mapNotNull(trip::timeAt)
    val columns =
        ScheduleColumns(
            time = rememberScheduleTimeWidth(times),
            // Eased open when platforms arrive later than the rest, rather than every name jumping aside.
            platform =
                animateDpAsState(
                    rememberPlatformWidth(trip.stops.mapNotNull { it.call.track }),
                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                ).value,
        )
    val span = trip.boarded ?: trip.stops.indices

    fun stroke(index: Int) = if (index in span && index + 1 in span) ride else quiet

    val clock = rememberSmoothNow()
    val now = remember(clock) { { clock.value } }
    val here = rideHere(trip.ride)
    // Nothing to follow on a trip that isn't running.
    val spans =
        if (trip.cancelled) {
            emptyList()
        } else {
            trip.stops.indices
                .map { trip.timeAt(it)?.expected }
                .rowSpans()
        }

    fun mark(index: Int) =
        when {
            index == span.first -> RouteMark.Stop
            index == span.last -> if (index == trip.stops.lastIndex) RouteMark.End else RouteMark.Stop
            else -> RouteMark.Pass
        }

    Section(if (titled) stringResource(R.string.trip_schedule) else null, modifier) {
        Column {
            val motion = MaterialTheme.motionScheme
            val unfold = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
            val fold = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
            trip.stops.forEachIndexed { index, stop ->
                // The row the vehicle is in draws above the next, whose line would cross its badge.
                val holding by remember(spans, index) {
                    derivedStateOf { spans.getOrNull(index)?.holds(now()) == true }
                }
                // Stops coming into the window unfold, those leaving it fold away.
                AnimatedVisibility(
                    index in shown,
                    Modifier.zIndex(if (holding) 1f else 0f).then(rowModifier(index)),
                    enter = unfold,
                    exit = fold,
                ) {
                    ScheduleRow(
                        stop,
                        trip.timeAt(index),
                        mark = mark(index),
                        ridden = index in span,
                        above = if (index > 0) stroke(index - 1) else null,
                        below = if (index < trip.stops.lastIndex) stroke(index) else null,
                        markStroke = if (index in span) ride else quiet,
                        columns = columns,
                        progress = spans.getOrNull(index)?.let { RouteProgress(it, now, here) },
                    )
                }
            }
            // Kept while it folds away, so it has something to fold.
            var lastCycling by remember { mutableStateOf(cycling) }
            if (!cycling.isEmpty()) lastCycling = cycling
            AnimatedVisibility(!cycling.isEmpty() && !lastCycling.isEmpty(), enter = unfold, exit = fold) {
                // One under the other: an AnimatedVisibility stacks what it holds.
                Column {
                    val turning = lastCycling
                    val into = stroke(turning.first - 1)
                    // The one dotted stretch, for the stops it goes on through: no dots meet dots, so
                    // the line stays even.
                    RouteRow(
                        RouteMark.None,
                        below = into.copy(dotted = true),
                        // Drawn as one stretch from top to bottom, so its dots run evenly.
                        markAt = 0.dp,
                        leading = { Spacer(Modifier.width(columns.time)) },
                    ) {
                        Row(
                            Modifier.padding(
                                start = ObehyTheme.spacing.s,
                                top = ObehyTheme.spacing.s,
                                bottom = ObehyTheme.spacing.s,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.trip_further_stops),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.Rounded.SubdirectoryArrowRight,
                                contentDescription = null,
                                Modifier.size(ObehyTheme.sizes.iconSmall),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val picks = remember(turning) { turning.picks() }
                    val turn by rememberCycle(picks.size)
                    val style =
                        if (turning.first in span) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        }
                    val markAt = ObehyTheme.spacing.xs + with(LocalDensity.current) { style.lineHeight.toDp() } / 2
                    Box(Modifier.clipToBounds()) {
                        // The line stays put; only the stop on it changes, coming down from above as the
                        // last goes on down: later and later stops.
                        RouteRow(
                            RouteMark.None,
                            Modifier.matchParentSize(),
                            above = into,
                            markAt = markAt,
                            leading = { Spacer(Modifier.width(columns.time)) },
                        ) {}
                        AnimatedContent(
                            picks[turn.coerceIn(picks.indices)],
                            transitionSpec = {
                                (slideInVertically(motion.slowSpatialSpec()) { -it } + fadeIn(motion.slowEffectsSpec()))
                                    .togetherWith(
                                        slideOutVertically(motion.slowSpatialSpec()) { it } +
                                            fadeOut(motion.fastEffectsSpec()),
                                    )
                            },
                            label = "cycling stop",
                        ) { index ->
                            ScheduleRow(
                                trip.stops[index],
                                trip.timeAt(index),
                                mark = mark(index),
                                ridden = index in span,
                                above = null,
                                below = null,
                                markStroke = if (index in span) ride else quiet,
                                columns = columns,
                            )
                        }
                    }
                }
            }
        }
        FlagLegend(trip.stops.slice(shown).flatMapTo(mutableSetOf()) { it.flags })
    }
}

/** Steps round [count] turns, lingering on each. */
@Composable
private fun rememberCycle(count: Int): State<Int> {
    val turn = remember(count) { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        while (true) {
            delay(CYCLE_MS)
            turn.intValue = (turn.intValue + 1) % count
        }
    }
    return turn
}

/** Every few of these stops, evenly spread, the last always among them: a cycle that moves along. */
private fun IntRange.picks(): List<Int> {
    val size = last - first + 1
    val stride = (size + CYCLE_PICKS - 1) / CYCLE_PICKS
    return ((first..last step stride).toList() + last).distinct()
}

/** About how many stops a cycling row takes in turn. */
private const val CYCLE_PICKS = 4

/** How long each stop in a cycling row stays. */
private const val CYCLE_MS = 3_000L

private val CallFlag.icon: ImageVector
    get() =
        when (this) {
            CallFlag.RequestStop -> Icons.Rounded.Notifications
            CallFlag.ExitOnly -> Icons.AutoMirrored.Rounded.Logout
            CallFlag.BoardingOnly -> Icons.AutoMirrored.Rounded.Login
        }

@Composable
private fun CallFlag.text(): String =
    stringResource(
        when (this) {
            CallFlag.RequestStop -> R.string.stop_on_request
            CallFlag.ExitOnly -> R.string.stop_exit_only
            CallFlag.BoardingOnly -> R.string.stop_boarding_only
        },
    )

/** What a stop's small signs mean, once, under the schedule. */
@Composable
private fun FlagLegend(
    flags: Set<CallFlag>,
    modifier: Modifier = Modifier,
) {
    if (flags.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m)) {
        CallFlag.entries.filter { it in flags }.forEach { flag ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlagIcon(flag, spoken = false)
                Caption(flag.text())
            }
        }
    }
}

@Composable
private fun FlagIcon(
    flag: CallFlag,
    spoken: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        flag.icon,
        contentDescription = if (spoken) flag.text() else null,
        modifier = modifier.size(ObehyTheme.sizes.iconSmall),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ScheduleRow(
    stop: TripStop,
    time: TimePoint?,
    mark: RouteMark,
    ridden: Boolean,
    above: RouteStroke?,
    below: RouteStroke?,
    markStroke: RouteStroke,
    columns: ScheduleColumns,
    modifier: Modifier = Modifier,
    progress: RouteProgress? = null,
) {
    val passed = rememberPassed(progress)
    val spoken = passedLabel()
    // One size down the whole schedule: what's ridden stands out by weight and colour alone.
    val style = if (ridden) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    val color =
        if (ridden && !passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val gap = ObehyTheme.spacing.xs
    val call = stop.call
    val firstLine = gap + with(LocalDensity.current) { style.lineHeight.toDp() } / 2
    RouteRow(
        mark,
        modifier,
        above = above,
        below = below,
        markColor = markStroke.color,
        markAt = firstLine,
        progress = progress,
        leading = {
            Box(Modifier.width(columns.time).padding(top = gap), Alignment.CenterEnd) {
                if (time != null) ScheduleTime(time, passed = passed)
            }
        },
    ) {
        Row(
            Modifier.weight(1f).padding(start = ObehyTheme.spacing.s, top = gap, bottom = gap),
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    call.name,
                    Modifier.weight(1f, fill = false).semantics { if (passed) stateDescription = spoken },
                    style = style,
                    color = if (call.cancelled) MaterialTheme.colorScheme.error else color,
                    textDecoration = if (call.cancelled) TextDecoration.LineThrough else null,
                )
                stop.flags.forEach { FlagIcon(it, spoken = true, Modifier.padding(top = gap / 2)) }
            }
            if (columns.platform > 0.dp) {
                Box(Modifier.width(columns.platform), Alignment.TopEnd) {
                    call.track?.let { PlatformLabel(it, call.trackChanged) }
                }
            }
        }
    }
}

/** Whether [TrainSection] has anything to show. */
fun hasTrainSection(
    trip: TripDetail,
    composition: Composition?,
    positionFrom: String? = null,
): Boolean = trip.info?.vehicles?.isNotEmpty() == true || composition.shown != null || positionFrom != null

private val Composition?.shown: Composition?
    get() =
        this?.takeUnless { it.status == CompositionStatus.NotAvailable }?.takeIf {
            it.segments.isNotEmpty() || it.status == CompositionStatus.Pending
        }

/** The vehicle on the trip and the train's order, when anyone knows. */
@Composable
fun TrainSection(
    trip: TripDetail,
    composition: Composition?,
    modifier: Modifier = Modifier,
    positionFrom: String? = null,
) {
    val vehicles = trip.info?.vehicles.orEmpty()
    val rows =
        vehicles.map<TripVehicleInfo, @Composable () -> Unit> { vehicle -> { VehicleRow(vehicle) } } +
            listOfNotNull<@Composable () -> Unit>(
                composition.shown?.let { shown -> { CompositionRow(trip, shown) } },
                positionFrom?.let { from ->
                    { InfoRow(Icons.Rounded.NearMe, stringResource(R.string.trip_position_from, from)) }
                },
            )
    Section(stringResource(R.string.trip_vehicle), modifier) {
        SegmentedRows(rows) { it() }
    }
}

/** A vehicle by what's known of it: its model first, or its plate or number when that's all there is. */
@Composable
private fun VehicleRow(
    vehicle: TripVehicleInfo,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    val named = vehicle.model != null
    Column(modifier.fillMaxWidth().padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        vehicle.model?.let { Text(it, style = MaterialTheme.typography.titleMediumEmphasized) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            vehicle.licencePlate?.let { VehicleId(it, large = !named) }
            vehicle.fleetNumber?.let { VehicleId(it, large = !named) }
            vehicle.operator?.let { InfoChip(Icons.Rounded.Business, it) }
            if (vehicle.lowFloor == true) {
                InfoChip(Icons.Rounded.Accessible, stringResource(R.string.vehicle_low_floor))
            }
            if (vehicle.airConditioning == true) {
                InfoChip(Icons.Rounded.AcUnit, stringResource(R.string.vehicle_air_conditioning))
            }
        }
    }
}

@Composable
private fun CompositionRow(
    trip: TripDetail,
    composition: Composition,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(ObehyTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
    ) {
        Text(stringResource(R.string.trip_train_order), style = MaterialTheme.typography.titleSmall)
        if (composition.segments.isEmpty()) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            Caption(stringResource(R.string.trip_finding_order))
            return@Column
        }
        // The API's stretches, and where the rider is (where the journey boards, else the trip's next stop).
        val stretches = composition.stretches
        var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
        val index = (chosen ?: composition.hereStretch ?: 0).coerceIn(0, stretches.lastIndex.coerceAtLeast(0))
        val stretch = stretches[index]
        val here = composition.hereSegment
        val segment =
            composition.segments[
                if (here != null &&
                    here in stretch.segments
                ) {
                    here
                } else {
                    stretch.segments.first()
                },
            ]
        val units = composition.unitsOf(segment)
        var opened by remember(composition, index) { mutableStateOf<Int?>(null) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Caption(stringResource(R.string.trip_direction))
        }
        if (stretches.size > 1) {
            ScrollingChipRow {
                stretches.forEachIndexed { n, it ->
                    FilterChip(
                        selected = n == index,
                        onClick = { chosen = n },
                        label = {
                            Text(stringResource(R.string.trip_span, it.fromCall.name, it.toCall.name))
                        },
                    )
                }
            }
        } else {
            Caption(stringResource(R.string.trip_span, stretch.fromCall.name, stretch.toCall.name))
        }
        CompositionStrip(units, segment.vehicles.map { it.cab }, onUnit = { opened = it })
        val sources = composition.sources.joinToString(", ")
        Caption(if (composition.checking) stringResource(R.string.trip_checking, sources) else sources)
        opened?.let { n ->
            units.getOrNull(n)?.let { unit ->
                UnitSheet(trip, unit, partial = segment.vehicles[n].partial, onDismiss = { opened = null })
            }
        }
    }
}

@Composable
private fun Caption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UnitSheet(
    trip: TripDetail,
    unit: CompositionUnit,
    partial: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            Modifier.padding(
                start = ObehyTheme.spacing.l,
                end = ObehyTheme.spacing.l,
                bottom = ObehyTheme.spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
        ) {
            Text(unitName(unit), style = MaterialTheme.typography.titleMediumEmphasized)
            unit.vehicleType?.let { Caption(it) }
            // ČD's own description repeats the features; alone, it says what the coach is.
            unit.description?.takeIf { unit.features.isEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            unit.destination?.let {
                Text(stringResource(R.string.unit_through_coach, it), style = MaterialTheme.typography.bodyMedium)
            }
            unit.leavesAt?.let { Caption(stringResource(R.string.unit_leaves_at, it)) }
            unit.continuesAs?.let { Caption(stringResource(R.string.unit_continues_as, it)) }
            unit.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (partial) {
                Caption(
                    stringResource(R.string.unit_only_between, unit.fromCall.name, unit.toCall.name),
                )
            }
            if (unit.features.isNotEmpty()) {
                SegmentedRows(unit.features) { InfoRow(featureIcon(it.icon) ?: Icons.Rounded.Info, it.text) }
            }
        }
    }
}

/** A note as a sentence: the timetable's texts start in lower case. */
private fun String.sentence(): String = replaceFirstChar { it.titlecase() }

/** When the trip runs and what the timetable marks, with the longer texts folded away. */
@Composable
fun NotesSection(
    notes: TripNotes,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.trip_notes), modifier) {
        SegmentedRows(notes.operates + notes.marked) { note ->
            val day = note.icon.removePrefix(CALENDAR_PREFIX).takeIf { note.icon.startsWith(CALENDAR_PREFIX) }
            if (day != null) {
                InfoRow(sign = { DaySign(day) }, text = note.text.sentence())
            } else {
                InfoRow(featureIcon(note.icon) ?: Icons.Rounded.Info, note.text.sentence())
            }
        }
        if (notes.long.isNotEmpty()) {
            val motion = MaterialTheme.motionScheme
            val chevron by animateFloatAsState(if (open) FLIPPED else 0f, motion.defaultSpatialSpec())
            AnimatedVisibility(
                open,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
            ) {
                SegmentedRows(notes.long) { InfoRow(Icons.Rounded.Info, it.sentence()) }
            }
            TextButton(onClick = { open = !open }, shapes = ButtonDefaults.shapes()) {
                Text(
                    if (open) {
                        stringResource(R.string.trip_fewer_notes)
                    } else {
                        stringResource(R.string.trip_more_notes, notes.long.size)
                    },
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(ObehyTheme.sizes.iconSmall).rotate(chevron),
                )
            }
        }
    }
}

private const val FLIPPED = 180f

@Composable
fun ConnectionsSection(
    connections: List<TripConnection>,
    modifier: Modifier = Modifier,
) {
    Section(stringResource(R.string.trip_connections), modifier) {
        SegmentedRows(connections) { connection ->
            val what =
                listOfNotNull(connection.line, connection.destination)
                    .joinToString(" → ")
                    .ifEmpty { stringResource(R.string.trip_connection_any) }
            val meta =
                listOfNotNull(
                    connection.stopName,
                    connection.waitMinutes?.let { stringResource(R.string.trip_wait_minutes, it) },
                    connection.note,
                ).joinToString(", ")
            InfoRow(
                if (connection.waitsForIt) Icons.Rounded.HourglassTop else Icons.AutoMirrored.Rounded.ArrowForward,
                stringResource(
                    if (connection.waitsForIt) R.string.trip_waits_for else R.string.trip_connects_to,
                    what,
                ),
                supporting = meta.ifEmpty { null },
            )
        }
    }
}

/**
 * A running day as the printed timetable marks it: crossed tools for working days, the day's
 * number for a day of the week, a cross for Sundays and holidays.
 */
@Composable
private fun DaySign(
    code: String,
    modifier: Modifier = Modifier,
) {
    when (code) {
        "X" -> Icon(Icons.Rounded.Construction, contentDescription = null, modifier.size(ObehyTheme.sizes.iconSmall))
        "+" -> Text("\u2020", modifier, style = MaterialTheme.typography.titleMedium)
        in WEEKDAYS -> Text(code, modifier, style = MaterialTheme.typography.labelLarge)
        else -> Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier.size(ObehyTheme.sizes.iconSmall))
    }
}

/** The icon name a running day comes with, before its code. */
private const val CALENDAR_PREFIX = "calendar_"

/** The days of the week, as calendar designations number them. */
private val WEEKDAYS = ('1'..'7').map(Char::toString)
