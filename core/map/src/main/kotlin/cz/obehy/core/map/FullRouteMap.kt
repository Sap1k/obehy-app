package cz.obehy.core.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.obehy.core.model.Leg
import cz.obehy.core.model.RouteSurroundings
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R as UiR

/**
 * A route on a map of its own, edge to edge: only a back button and [controls] float over it.
 * The other arguments are [RouteMap]'s.
 */
@Composable
fun FullRouteMap(
    legs: List<Leg>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    around: RouteSurroundings? = null,
    vehicles: List<MapVehicle> = emptyList(),
    glideMillis: Int = GLIDE_MS,
    framing: MapFraming = MapFraming.Whole,
    onGesture: () -> Unit = {},
    controls: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        RouteMap(
            legs,
            Modifier.fillMaxSize(),
            interactive = true,
            fullscreen = true,
            around = around,
            vehicles = vehicles,
            glideMillis = glideMillis,
            framing = framing,
            onGesture = onGesture,
        )
        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.safeDrawingPadding().padding(ObehyTheme.spacing.s),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(UiR.string.back))
        }
        Row(
            Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(ObehyTheme.spacing.s),
            content = controls,
        )
    }
}

/** Off, it's solid over a map like the tonal buttons beside it; on, it's the theme's own filled look. */
@Composable
private fun mapToggleColors() =
    IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
    )

/** Keeps a map on the vehicle while [following]. */
@Composable
fun FollowButton(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconToggleButton(
        following,
        onFollowingChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier,
        colors = mapToggleColors(),
    ) {
        Icon(Icons.Rounded.NearMe, contentDescription = stringResource(R.string.map_follow))
    }
}

/** Flips a map between the part of a journey that matters now and, when [whole], all of it. */
@Composable
fun WholeJourneyButton(
    whole: Boolean,
    onWholeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconToggleButton(
        whole,
        onWholeChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier,
        colors = mapToggleColors(),
    ) {
        Icon(Icons.Rounded.ZoomOutMap, contentDescription = stringResource(R.string.map_whole_journey))
    }
}
