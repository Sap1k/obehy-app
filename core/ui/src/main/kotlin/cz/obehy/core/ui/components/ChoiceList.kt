package cz.obehy.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import cz.obehy.core.ui.ObehyTheme

/** One option of a [ChoiceList], with its texts already resolved. */
@Immutable
data class ChoiceItem<T>(
    val value: T,
    val title: String,
    val description: String,
    val icon: ImageVector,
    /** A short label above the title, e.g. "Recommended". */
    val overline: String? = null,
)

/**
 * Pick one of a few options, each with an icon and a sentence of explanation: a segmented list
 * whose picked item turns Lime (the brand's selected state), its icon badge morphing from a circle
 * into a cookie. Nothing in an item moves or reflows when it is picked.
 */
@Composable
fun <T> ChoiceList(
    items: List<ChoiceItem<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        items.forEachIndexed { index, item ->
            val isSelected = item.value == selected
            SegmentedListItem(
                selected = isSelected,
                onClick = { onSelect(item.value) },
                shapes = ListItemDefaults.segmentedShapes(index, items.size),
                modifier = Modifier.semantics { role = Role.RadioButton },
                colors =
                    ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedOverlineContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingContentColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                leadingContent = { ChoiceBadge(item.icon, isSelected) },
                trailingContent = { SelectedCheck(isSelected) },
                overlineContent = item.overline?.let { { Text(it) } },
                supportingContent = { Text(item.description) },
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
    }
}

@Composable
private fun ChoiceBadge(
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val turn by animateFloatAsState(if (selected) 90f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    val shape = (if (selected) MaterialShapes.Cookie9Sided else MaterialShapes.Circle).toShape()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(ObehyTheme.sizes.iconBadge)
            .rotate(turn)
            .clip(shape)
            .background(if (selected) colors.onPrimaryContainer else colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(ObehyTheme.sizes.icon).rotate(-turn),
            tint = if (selected) colors.primaryContainer else colors.onSecondaryContainer,
        )
    }
}

/** Always laid out, only shown when picked, so picking never reflows the text. */
@Composable
private fun SelectedCheck(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val shown by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
    Icon(
        Icons.Rounded.CheckCircle,
        contentDescription = null,
        modifier =
            modifier.graphicsLayer {
                alpha = shown.coerceIn(0f, 1f)
                scaleX = shown
                scaleY = shown
            },
    )
}
