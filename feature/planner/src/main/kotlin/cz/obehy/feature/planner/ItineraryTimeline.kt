package cz.obehy.feature.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.Leg
import cz.obehy.core.model.StopCall
import cz.obehy.core.model.TimePoint
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.LineBadge
import cz.obehy.core.ui.components.LowFloorIcon
import cz.obehy.core.ui.components.PlatformLabel
import cz.obehy.core.ui.components.RouteMark
import cz.obehy.core.ui.components.RouteRow
import cz.obehy.core.ui.components.RouteStroke
import cz.obehy.core.ui.components.TimeBasis
import cz.obehy.core.ui.components.TimeColumn
import cz.obehy.core.ui.components.rememberTimeColumnWidth
import cz.obehy.core.ui.components.routeLineColor
import cz.obehy.core.ui.components.walkStroke
import java.time.Duration

/**
 * A journey as one continuous line: each ride in its route's colour between the stops where you
 * board and alight, walks dotted with the time to spare, the stops in between folded away.
 * [fromName] and [toName] name the ends when they are an address rather than a stop.
 */
@Composable
internal fun ItineraryTimeline(
    itinerary: Itinerary,
    fromName: String?,
    toName: String?,
    modifier: Modifier = Modifier,
) {
    val walk = walkStroke()
    val legs = itinerary.legs
    val width = rememberTimeColumnWidth(legs.flatMap { it.times() }, legs.map { it.duration.spoken() })
    Column(modifier) {
        legs.forEachIndexed { index, leg ->
            val previous = legs.getOrNull(index - 1)
            val next = legs.getOrNull(index + 1)
            when (leg) {
                is Leg.Walk -> {
                    if (previous == null) {
                        StopRow(fromName ?: leg.from.name, leg.start, null, RouteMark.Stop, null, walk, width)
                    }
                    WalkRow(
                        leg,
                        spare = (next as? Leg.Transit)?.let { Duration.between(leg.end.expected, it.start.expected) },
                        walk,
                        width,
                    )
                    if (next == null) {
                        StopRow(toName ?: leg.to.name, leg.end, null, RouteMark.End, walk, null, width)
                    }
                }

                is Leg.Transit -> {
                    val ride = RouteStroke(routeLineColor(leg.color))
                    // When the vehicle just goes on as this ride, the previous ride's last stop already says so.
                    if (!leg.staySeated) {
                        StopRow(
                            leg.from.name,
                            leg.start,
                            leg.from,
                            RouteMark.Stop,
                            above = walk.takeIf { previous is Leg.Walk },
                            below = ride,
                            width = width,
                        )
                    }
                    RideRow(leg, ride, width)
                    val continues = (next as? Leg.Transit)?.takeIf { it.staySeated }
                    StopRow(
                        leg.to.name,
                        leg.end,
                        leg.to,
                        if (next == null) RouteMark.End else RouteMark.Stop,
                        above = ride,
                        below =
                            continues?.let {
                                RouteStroke(
                                    routeLineColor(it.color),
                                )
                            } ?: walk.takeIf { next is Leg.Walk },
                        width = width,
                        note = continues?.let { stringResource(R.string.stay_seated, it.name) },
                    )
                }
            }
        }
    }
}

private fun Leg.times(): List<TimePoint> =
    buildList {
        add(start)
        add(end)
        if (this@times is Leg.Transit) {
            intermediateStops.forEach { stop ->
                (stop.departure ?: stop.arrival)?.let(::add)
            }
        }
    }

/**
 * What fills the time column on a row without a stop: how long this part takes. It is read out
 * as [spoken].
 */
@Composable
private fun DurationNote(
    text: String,
    spoken: String,
    width: Dp,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .width(width)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(end = ObehyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How far down a row the middle of its first line of [style] text sits. */
@Composable
private fun firstLine(style: TextStyle): Dp =
    ObehyTheme.spacing.xs + with(LocalDensity.current) { style.lineHeight.toDp() } / 2

@Composable
private fun StopRow(
    name: String,
    time: TimePoint,
    call: StopCall?,
    mark: RouteMark,
    above: RouteStroke?,
    below: RouteStroke?,
    width: Dp,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val style = MaterialTheme.typography.titleMediumEmphasized
    val gap = ObehyTheme.spacing.xs
    RouteRow(mark, modifier, above = above, below = below, markAt = firstLine(style), leading = {
        TimeColumn(time, width, Modifier.padding(top = gap))
    }) {
        Column(Modifier.weight(1f).padding(start = ObehyTheme.spacing.m, top = gap, bottom = gap)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.weight(1f, fill = false), style = style)
                    call?.track?.let { PlatformLabel(it, call.trackChanged) }
                }
                TimeBasis(time)
            }
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RideRow(
    ride: Leg.Transit,
    stroke: RouteStroke,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable(ride.tripId) { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val chevron by animateFloatAsState(if (open) 180f else 0f, motion.defaultSpatialSpec())
    val stops = ride.intermediateStops
    val toggle = stringResource(if (open) R.string.hide_stops else R.string.show_stops)
    RouteRow(RouteMark.None, modifier, above = stroke, below = stroke, leading = {
        val duration = ride.duration.spoken()
        DurationNote(duration, duration, width)
    }) {
        // The whole ride is the toggle, which keeps it an easy target without adding height.
        Column(
            Modifier
                .weight(1f)
                .padding(
                    start = ObehyTheme.spacing.m,
                    top = ObehyTheme.spacing.xs,
                    bottom = ObehyTheme.spacing.xs,
                ).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .then(if (stops.isNotEmpty()) Modifier.clickable(onClickLabel = toggle) { open = !open } else Modifier)
                .padding(horizontal = ObehyTheme.spacing.m, vertical = ObehyTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineBadge(ride)
                if (ride.accessible == true) LowFloorIcon()
            }
            // On its own line, so a long train name can never squeeze it.
            ride.headsign?.let { Headsign(it) }
            if (stops.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.stops_between, stops.size, stops.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(ObehyTheme.sizes.iconSmall).rotate(chevron),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
    ) {
        Column { ride.intermediateStops.forEach { PassedStop(it, stroke, width) } }
    }
}

@Composable
private fun PassedStop(
    stop: StopCall,
    stroke: RouteStroke,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodySmall
    val gap = ObehyTheme.spacing.xs
    val time = stop.departure ?: stop.arrival
    RouteRow(RouteMark.Pass, modifier, above = stroke, below = stroke, markAt = firstLine(style), leading = {
        if (time !=
            null
        ) {
            TimeColumn(time, width, Modifier.padding(top = gap), small = true)
        } else {
            Spacer(Modifier.width(width))
        }
    }) {
        Row(
            Modifier.weight(1f).padding(start = ObehyTheme.spacing.m, top = gap, bottom = gap),
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
        ) {
            Text(
                stop.name,
                Modifier.weight(1f),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            time?.let { TimeBasis(it, small = true) }
        }
    }
}

/** Where a ride goes: an arrow and its destination, spoken as "towards …". */
@Composable
private fun Headsign(
    destination: String,
    modifier: Modifier = Modifier,
) {
    val spoken = stringResource(R.string.towards, destination)
    Row(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            // Level with the first line when a long destination wraps.
            modifier = Modifier.padding(top = ObehyTheme.spacing.xs / 2).size(ObehyTheme.sizes.iconSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(destination, style = MaterialTheme.typography.titleSmallEmphasized)
    }
}

@Composable
private fun WalkRow(
    walk: Leg.Walk,
    spare: Duration?,
    stroke: RouteStroke,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    RouteRow(RouteMark.None, modifier, above = stroke, below = stroke, leading = {
        val duration = walk.duration.spoken()
        DurationNote(
            duration,
            stringResource(R.string.walk, duration),
            width,
            Modifier.padding(vertical = ObehyTheme.spacing.xs),
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
        )
    }) {
        spare?.takeIf { it.toMinutes() > 0 }?.let {
            Text(
                stringResource(R.string.spare, it.spoken()),
                Modifier.padding(start = ObehyTheme.spacing.m),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
