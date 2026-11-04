package cz.obehy.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import cz.obehy.core.ui.ObehyTheme

/** A short list as one group of tiles: the ends rounded, the tiles between them close together. */
@Composable
fun <T> SegmentedRows(
    items: List<T>,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        items.forEachIndexed { index, item ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(ListItemDefaults.segmentedShapes(index, items.size).shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) { row(item) }
        }
    }
}

/** A line of information in a [SegmentedRows]: a small icon on a cookie, the text beside it. */
@Composable
fun InfoRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ObehyTheme.spacing.m, vertical = ObehyTheme.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ObehyTheme.sizes.iconCookie)
                .clip(MaterialShapes.Cookie6Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
