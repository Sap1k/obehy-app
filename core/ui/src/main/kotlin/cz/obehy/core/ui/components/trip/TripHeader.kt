package cz.obehy.core.ui.components.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import cz.obehy.core.model.MarkedNote
import cz.obehy.core.model.Mode
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripLinks
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R
import cz.obehy.core.ui.components.DelayLabel
import cz.obehy.core.ui.components.LineBadge
import cz.obehy.core.ui.components.featureIcon

private val TRAINS =
    setOf(Mode.RegionalRail, Mode.SuburbanRail, Mode.LongDistanceRail, Mode.HighSpeedRail, Mode.NightRail)

/** On the sheet over the map, so it stays in sight: which trip this is, where it goes, and what it offers. */
@Composable
fun TripHeader(
    trip: TripDetail,
    modifier: Modifier = Modifier,
) {
    val ride = trip.ride
    val spacing = ObehyTheme.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(ride)
            DelayLabel(trip.delayMinutes)
        }
        ride.headsign?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Who runs it, its number, then what it offers, all from the left; what doesn't fit goes on
        // the next line rather than squeezing the operator's name.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // A train's number means something to riders; a bus's is just line and trip (`100195 1224`).
            val number = ride.tripShortName?.takeIf { ride.mode in TRAINS && it !in ride.name }
            ride.agency?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            number?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val icons = trip.attributes.mapNotNull { attribute -> featureIcon(attribute.icon)?.let { attribute to it } }
            if (icons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    icons.forEach { (attribute, icon) ->
                        Icon(
                            icon,
                            contentDescription = attribute.text.ifEmpty { null } ?: attribute.fallbackText(),
                            modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (trip.cancelled) {
            Text(
                stringResource(R.string.trip_cancelled),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The feed's links for a trip, tucked away on the map: only there at all when it has any. */
@Composable
fun TripLinksMenu(
    links: TripLinks,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty) return
    var open by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    @Composable
    fun Link(
        url: String?,
        text: Int,
    ) {
        url ?: return
        DropdownMenuItem(
            text = { Text(stringResource(text)) },
            onClick = {
                open = false
                uri.openUri(url)
            },
        )
    }
    Box(modifier) {
        FilledTonalIconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.trip_links))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Link(links.tickets, R.string.trip_link_tickets)
            Link(links.line, R.string.trip_link_line)
            Link(links.operator, R.string.trip_link_operator)
        }
    }
}

/** What MOTIS says of a trip comes without a text of its own. */
@Composable
private fun MarkedNote.fallbackText(): String =
    stringResource(
        when (icon) {
            "bicycle" -> R.string.trip_bikes
            "reservation_required" -> R.string.trip_reservation_required
            "reservation" -> R.string.trip_reservation
            else -> R.string.vehicle_low_floor
        },
    )
