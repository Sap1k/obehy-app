package cz.obehy.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import cz.obehy.core.ui.ObehyTheme

/** A small fact about a vehicle: an icon and a word or two on a tinted pill. */
@Composable
fun InfoChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = ObehyTheme.spacing.s, vertical = ObehyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/** What a vehicle goes by, its plate or fleet number. [large] when it's all that's known of it. */
@Composable
fun VehicleId(
    id: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Text(
        id,
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = ObehyTheme.spacing.s, vertical = ObehyTheme.spacing.xs),
        style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}
