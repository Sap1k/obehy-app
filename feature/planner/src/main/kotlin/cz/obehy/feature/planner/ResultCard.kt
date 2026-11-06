package cz.obehy.feature.planner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.JourneyTrouble
import cz.obehy.core.model.troubleAt
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.DelayLabel
import cz.obehy.core.ui.components.JourneyBar
import cz.obehy.core.ui.components.LowFloorIcon
import cz.obehy.core.ui.components.TimeLabel
import cz.obehy.core.ui.pressScale
import cz.obehy.core.ui.scaledBy
import java.time.Instant

/**
 * One connection in a connected list of [count] results: when, then the bar of its legs, then how
 * long, the changes and any delay. Its corners morph and it sinks in when pressed.
 */
@Composable
internal fun ResultCard(
    itinerary: Itinerary,
    index: Int,
    count: Int,
    selected: Boolean,
    measurer: TextMeasurer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val press = remember { MutableInteractionSource() }
    val scale = pressScale(press)
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.fillMaxWidth().scaledBy(scale),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = ObehyTheme.cardColor,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        interactionSource = press,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeLabel(itinerary.start)
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeLabel(itinerary.end)
            }
            JourneyBar(itinerary.legs, measurer = measurer)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // How long it takes is what connections get compared by.
                Text(itinerary.duration.spoken(), style = MaterialTheme.typography.labelLargeEmphasized)
                Text(
                    transfersText(itinerary.transfers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DelayLabel(itinerary.worstDelayMinutes)
                TroubleLabel(itinerary)
                if (itinerary.accessible == true) LowFloorIcon()
            }
        }
    }
}

/** Says so, briefly, when a journey in the list no longer works: its times may have changed since it was found. */
@Composable
private fun TroubleLabel(
    itinerary: Itinerary,
    modifier: Modifier = Modifier,
) {
    val trouble = remember(itinerary) { itinerary.troubleAt(Instant.now()) } ?: return
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
            tint = color,
        )
        Text(
            stringResource(
                when (trouble) {
                    is JourneyTrouble.Cancelled -> R.string.ride_cancelled
                    is JourneyTrouble.MissedChange -> R.string.change_missed
                },
            ),
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
