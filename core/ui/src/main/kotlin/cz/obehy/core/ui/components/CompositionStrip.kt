package cz.obehy.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChildFriendly
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Woman
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import cz.obehy.core.model.CabEnds
import cz.obehy.core.model.CompositionUnit
import cz.obehy.core.model.UnitKind
import cz.obehy.core.model.cabEnds
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R

/** The ones a rider looks for on a coach, most wanted first; the rest are in its detail. */
private val ON_COACH =
    listOf(
        "sleeper",
        "couchette",
        "restaurant",
        "wheelchair_lift",
        "wheelchair",
        "bicycle_reservation",
        "bicycle",
        "family",
        "quiet",
        "power_socket",
    )
private const val MOST_ON_COACH = 3

/**
 * A train's vehicles in order, the head of the train on the left, scrolling sideways when they
 * don't fit. [onUnit] is told the position of the one tapped.
 */
@Composable
fun CompositionStrip(
    units: List<CompositionUnit>,
    onUnit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ends = cabEnds(units)
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs / 2),
    ) {
        units.forEachIndexed { index, unit -> Vehicle(unit, ends[index], onClick = { onUnit(index) }) }
    }
}

/** A vehicle's name, as read out and at the head of its detail: "Coach 367, class 2". */
@Composable
fun unitName(unit: CompositionUnit): String {
    if (unit.kind == UnitKind.Locomotive) return stringResource(R.string.unit_locomotive)
    val head =
        unit.coachNumber?.let { stringResource(R.string.unit_coach_numbered, it) }
            ?: stringResource(R.string.unit_coach)
    return unit.classLabel?.let { stringResource(R.string.unit_with_class, head, it) } ?: head
}

@Composable
private fun Vehicle(
    unit: CompositionUnit,
    ends: CabEnds,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizes = ObehyTheme.sizes
    val spacing = ObehyTheme.spacing
    val colors = MaterialTheme.colorScheme
    val fill = if (unit.kind == UnitKind.Locomotive) colors.surfaceContainerHighest else colors.surfaceContainer
    val line = colors.outline
    val name = unitName(unit)
    Box(
        modifier
            .size(sizes.coachWidth, sizes.coachHeight)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = name
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }.drawBehind {
                val outline = 1.5.dp.toPx()
                val inset = outline / 2
                val cab = sizes.coachCab.toPx()
                val right = size.width - inset
                val bottom = size.height - inset
                val waist = size.height * CAB_WAIST
                val path =
                    Path().apply {
                        moveTo(if (ends.front) inset + cab else inset, inset)
                        if (ends.rear) {
                            lineTo(right - cab, inset)
                            lineTo(right, waist)
                        } else {
                            lineTo(right, inset)
                        }
                        lineTo(right, bottom)
                        lineTo(inset, bottom)
                        if (ends.front) lineTo(inset, waist)
                        close()
                    }
                drawPath(path, fill)
                drawPath(path, line, style = Stroke(outline))
            },
    ) {
        if (unit.kind != UnitKind.Locomotive) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (ends.front) sizes.coachCab else spacing.xs,
                        end = if (ends.rear) sizes.coachCab else spacing.xs,
                        top = spacing.xs / 2,
                    ),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(unit.coachNumber.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(unit.classLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs / 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    coachIcons(unit).forEach {
                        Icon(it, contentDescription = null, Modifier.size(sizes.iconSmall - spacing.xs / 2))
                    }
                }
            }
        }
    }
}

/** Where a cab end stops slanting, as a share of the vehicle's height. */
private const val CAB_WAIST = 0.55f

private fun coachIcons(unit: CompositionUnit): List<ImageVector> =
    ON_COACH
        .filter { name -> unit.features.any { it.icon == name } }
        .mapNotNull(::featureIcon)
        .distinct()
        .take(MOST_ON_COACH)

/** The icon for one of the API's feature icon names, or null when there isn't one. */
fun featureIcon(name: String?): ImageVector? =
    when (name) {
        "wifi" -> Icons.Rounded.Wifi
        "power_socket" -> Icons.Rounded.Power
        "wheelchair", "wheelchair_lift", "wheelchair_booking", "wheelchair_partly" -> Icons.Rounded.Accessible
        "bicycle", "bicycle_reservation" -> Icons.Rounded.DirectionsBike
        "bicycle_none" -> Icons.Rounded.PedalBike
        "family" -> Icons.Rounded.ChildFriendly
        "cinema" -> Icons.Rounded.Movie
        "portal" -> Icons.Rounded.Tv
        "open_saloon" -> Icons.Rounded.Weekend
        "luggage" -> Icons.Rounded.Luggage
        "couchette", "sleeper" -> Icons.Rounded.Bed
        "restaurant" -> Icons.Rounded.Restaurant
        "refreshments" -> Icons.Rounded.LocalCafe
        "quiet" -> Icons.Rounded.VolumeOff
        "business" -> Icons.Rounded.BusinessCenter
        "ladies" -> Icons.Rounded.Woman
        "reservation", "reservation_required", "class" -> Icons.Rounded.EventSeat
        "calendar" -> Icons.Rounded.CalendarMonth
        "coach", "steam" -> Icons.Rounded.Train
        "car" -> Icons.Rounded.DirectionsCar
        "bus", "interchange_line" -> Icons.Rounded.DirectionsBus
        "interchange", "interchange_rail", "interchange_city", "interchange_metro" -> Icons.Rounded.SyncAlt
        "ticket" -> Icons.Rounded.ConfirmationNumber
        "no_discount" -> Icons.Rounded.MoneyOff
        "integrated" -> Icons.Rounded.Hub
        "queue" -> Icons.Rounded.Groups
        "no_toilet", "toilet", "toilet_accessible" -> Icons.Rounded.Wc
        "visually_impaired", "accessibility_point" -> Icons.Rounded.Visibility
        "on_request" -> Icons.Rounded.Phone
        "conditional" -> Icons.Rounded.Rule
        "diversion" -> Icons.Rounded.AltRoute
        "airport" -> Icons.Rounded.Flight
        "ship" -> Icons.Rounded.DirectionsBoat
        "park_and_ride" -> Icons.Rounded.LocalParking
        "note" -> Icons.Rounded.Info
        else -> null
    }
