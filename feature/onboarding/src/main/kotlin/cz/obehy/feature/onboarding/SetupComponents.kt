package cz.obehy.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.RoutingProvider
import cz.obehy.core.ui.R as UiR

internal data class Choice<T>(
    val value: T,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: @Composable () -> ImageVector,
    val recommended: Boolean = false,
)

internal val modeChoices = listOf(
    Choice(AppMode.Enhanced, R.string.mode_enhanced, R.string.mode_enhanced_description, { Icons.Rounded.Map }),
    Choice(AppMode.Transitous, R.string.mode_transitous, R.string.mode_transitous_description, { Icons.Rounded.Route }),
)

internal val providerChoices = listOf(
    Choice(
        RoutingProvider.Transitous,
        R.string.provider_transitous,
        R.string.provider_transitous_description,
        { Icons.Rounded.Public },
        recommended = true,
    ),
    Choice(
        RoutingProvider.Obehy,
        R.string.provider_obehy,
        R.string.provider_obehy_description,
        { ImageVector.vectorResource(UiR.drawable.obehy_symbol) },
    ),
)

/**
 * One choice of several as a segmented list: the picked item turns Lime (the brand's selected
 * state) and its icon badge morphs from a circle into a cookie.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ChoiceList(
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        choices.forEachIndexed { index, choice ->
            val isSelected = choice.value == selected
            SegmentedListItem(
                selected = isSelected,
                onClick = { onSelect(choice.value) },
                shapes = ListItemDefaults.segmentedShapes(index, choices.size),
                modifier = Modifier.semantics { role = Role.RadioButton },
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedOverlineContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingContentColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                leadingContent = { ChoiceBadge(choice.icon(), isSelected) },
                // Always laid out, only shown when picked, so selecting never reflows the text.
                trailingContent = { SelectedCheck(isSelected) },
                overlineContent = if (choice.recommended) {
                    { Text(stringResource(R.string.provider_recommended)) }
                } else {
                    null
                },
                supportingContent = { Text(stringResource(choice.description)) },
            ) {
                Text(stringResource(choice.title), style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceBadge(icon: ImageVector, selected: Boolean) {
    val turn by animateFloatAsState(if (selected) 90f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    val shape = (if (selected) MaterialShapes.Cookie9Sided else MaterialShapes.Circle).toShape()
    Box(
        Modifier
            .size(48.dp)
            .rotate(turn)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp).rotate(-turn),
            tint = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectedCheck(selected: Boolean) {
    val shown by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
    Icon(
        Icons.Rounded.CheckCircle,
        contentDescription = null,
        modifier = Modifier.graphicsLayer {
            alpha = shown.coerceIn(0f, 1f)
            scaleX = shown
            scaleY = shown
        },
    )
}
