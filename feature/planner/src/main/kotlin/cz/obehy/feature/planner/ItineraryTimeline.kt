package cz.obehy.feature.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ExpandLess
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.Leg
import cz.obehy.core.model.StopCall
import cz.obehy.core.model.TimePoint
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.DelayLabel
import cz.obehy.core.ui.components.LineBadge
import cz.obehy.core.ui.components.LowFloorIcon
import cz.obehy.core.ui.components.PlatformLabel
import cz.obehy.core.ui.components.RouteMark
import cz.obehy.core.ui.components.RouteRow
import cz.obehy.core.ui.components.RouteStroke
import cz.obehy.core.ui.components.TimeLabel
import cz.obehy.core.ui.components.TimeLabelSize
import cz.obehy.core.ui.components.clock
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
    Column(modifier) {
        legs.forEachIndexed { index, leg ->
            val previous = legs.getOrNull(index - 1)
            val next = legs.getOrNull(index + 1)
            when (leg) {
                is Leg.Walk -> {
                    if (previous == null) {
                        StopRow(fromName ?: leg.from.name, leg.start, null, RouteMark.Stop, above = null, below = walk)
                    }
                    WalkRow(
                        leg,
                        spare = (next as? Leg.Transit)?.let { Duration.between(leg.end.expected, it.start.expected) },
                        walk,
                    )
                    if (next == null) {
                        StopRow(toName ?: leg.to.name, leg.end, null, RouteMark.End, above = walk, below = null)
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
                        )
                    }
                    RideRow(leg, ride)
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
                        note = continues?.let { stringResource(R.string.stay_seated, it.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(
    time: TimePoint?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Box(modifier.width(ObehyTheme.sizes.timeColumn)) {
        time?.let { TimeLabel(it, size = if (small) TimeLabelSize.Small else TimeLabelSize.Large) }
    }
}

@Composable
private fun StopRow(
    name: String,
    time: TimePoint,
    call: StopCall?,
    mark: RouteMark,
    above: RouteStroke?,
    below: RouteStroke?,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    RouteRow(mark, modifier, above = above, below = below, leading = { TimeColumn(time) }) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = ObehyTheme.spacing.m, top = ObehyTheme.spacing.xs, bottom = ObehyTheme.spacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (mark == RouteMark.End) FontWeight.Bold else null,
                )
                call?.track?.let { PlatformLabel(it, call.trackChanged) }
            }
            DelayLabel(time.delayMinutes)
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
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable(ride.tripId) { mutableStateOf(false) }
    RouteRow(RouteMark.None, modifier, above = stroke, below = stroke, leading = { TimeColumn(null) }) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = ObehyTheme.spacing.m, top = ObehyTheme.spacing.s, bottom = ObehyTheme.spacing.s),
        ) {
            // The line and where it goes share a line when they fit.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                LineBadge(ride)
                ride.headsign?.let { Headsign(it) }
                if (ride.accessible == true) LowFloorIcon()
            }
            val stops = ride.intermediateStops
            if (stops.isNotEmpty()) {
                Row(
                    Modifier.clickable { open = !open }.padding(top = ObehyTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        pluralStringResource(R.plurals.stops_between, stops.size, stops.size, ride.duration.spoken()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    ride.duration.spoken(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (open) {
        ride.intermediateStops.forEach { stop ->
            RouteRow(RouteMark.Pass, above = stroke, below = stroke, leading = {
                TimeColumn(stop.departure ?: stop.arrival, small = true)
            }) {
                Text(
                    stop.name,
                    Modifier.padding(
                        start = ObehyTheme.spacing.m,
                        top = ObehyTheme.spacing.xs,
                        bottom = ObehyTheme.spacing.xs,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(destination, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WalkRow(
    walk: Leg.Walk,
    spare: Duration?,
    stroke: RouteStroke,
    modifier: Modifier = Modifier,
) {
    RouteRow(RouteMark.None, modifier, above = stroke, below = stroke, leading = { TimeColumn(null) }) {
        Row(
            Modifier.padding(start = ObehyTheme.spacing.m, top = ObehyTheme.spacing.xs, bottom = ObehyTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val text =
                buildList {
                    add(stringResource(R.string.walk, walk.duration.spoken()))
                    spare?.takeIf { it.toMinutes() > 0 }?.let { add(stringResource(R.string.spare, it.spoken())) }
                }.joinToString(" · ")
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
