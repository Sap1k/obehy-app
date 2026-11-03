package cz.obehy.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.obehy.core.model.Itinerary
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.DelayLabel
import cz.obehy.core.ui.components.JourneyBar
import cz.obehy.core.ui.components.LowFloorIcon
import cz.obehy.core.ui.components.TimeLabel
import cz.obehy.core.ui.components.TimeLabelSize
import cz.obehy.core.ui.components.clock

/** One connection in the results: when, how long, the bar of its legs, changes and delays. */
@Composable
internal fun ResultCard(
    itinerary: Itinerary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.padding(horizontal = ObehyTheme.spacing.l, vertical = ObehyTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TimeLabel(itinerary.start, size = TimeLabelSize.Inline)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                    TimeLabel(itinerary.end, size = TimeLabelSize.Inline)
                }
                Text(
                    itinerary.duration.spoken(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            JourneyBar(itinerary.legs)
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    transfersText(itinerary.transfers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DelayLabel(itinerary.worstDelayMinutes)
                if (itinerary.accessible == true) LowFloorIcon()
            }
        }
    }
}
