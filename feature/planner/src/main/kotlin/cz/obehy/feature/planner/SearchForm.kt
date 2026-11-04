package cz.obehy.feature.planner

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.Place
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.LowFloorIcon
import cz.obehy.core.ui.components.RouteMark
import cz.obehy.core.ui.components.RouteRow
import cz.obehy.core.ui.components.RouteStroke
import cz.obehy.core.ui.components.ScrollingChipRow
import kotlin.math.roundToInt
import cz.obehy.core.ui.R as UiR

/**
 * The search: from, the stops on the way and to along a route line, the swap button beside them,
 * then chips for the time, another stop, low-floor and the other options, and the search button,
 * which draws itself into a circle around a loading indicator while [searching].
 */
@Composable
internal fun SearchForm(
    query: JourneyQuery,
    options: SearchOptions,
    searching: Boolean,
    onAction: (PlannerAction) -> Unit,
    onTime: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = RouteStroke(MaterialTheme.colorScheme.primary)
    var swaps by rememberSaveable { mutableIntStateOf(0) }
    val turn by animateFloatAsState(swaps * 180f, MaterialTheme.motionScheme.defaultSpatialSpec())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RouteRow(RouteMark.Stop, below = line) {
                    PlaceField(
                        R.string.from,
                        query.from,
                        R.string.from_placeholder,
                        { onAction(PlannerAction.Pick(PlaceTarget.From)) },
                        arrivesFromBelow = true,
                    )
                }
                query.vias.forEachIndexed { index, via ->
                    RouteRow(RouteMark.Via, above = line, below = line) {
                        PlaceField(
                            R.string.via,
                            via,
                            R.string.place_hint_stop,
                            { onAction(PlannerAction.Pick(PlaceTarget.Via(index))) },
                            onRemove = { onAction(PlannerAction.RemoveVia(index)) },
                        )
                    }
                }
                RouteRow(RouteMark.End, above = line) {
                    PlaceField(
                        R.string.to,
                        query.to,
                        R.string.to_placeholder,
                        { onAction(PlannerAction.Pick(PlaceTarget.To)) },
                    )
                }
            }
            FilledTonalIconButton(
                onClick = {
                    swaps++
                    onAction(PlannerAction.Swap)
                },
                modifier = Modifier.padding(start = ObehyTheme.spacing.s),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.SwapVert, stringResource(R.string.swap), Modifier.rotate(turn))
            }
        }
        ScrollingChipRow {
            AssistChip(
                onClick = onTime,
                label = { Text(query.timeLabel()) },
                leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(ObehyTheme.sizes.iconSmall)) },
            )
            if (query.vias.size < JourneyQuery.MAX_VIAS) {
                AssistChip(
                    onClick = { onAction(PlannerAction.Pick(PlaceTarget.Via(query.vias.size))) },
                    label = { Text(stringResource(R.string.add_via)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(ObehyTheme.sizes.iconSmall)) },
                )
            }
            FilterChip(
                selected = options.lowFloor,
                onClick = { onAction(PlannerAction.SetOptions(options.copy(lowFloor = !options.lowFloor))) },
                label = { Text(stringResource(UiR.string.low_floor)) },
                leadingIcon = { LowFloorIcon() },
            )
            FilterChip(
                selected = options.changedCount > 0,
                onClick = onOptions,
                label = {
                    Text(
                        if (options.changedCount > 0) {
                            stringResource(R.string.options_changed, options.changedCount)
                        } else {
                            stringResource(R.string.options)
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(ObehyTheme.sizes.iconSmall)) },
            )
        }
        val height = ButtonDefaults.MediumContainerHeight
        val searchingText = stringResource(R.string.searching)
        val circle by animateFloatAsState(if (searching) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = { if (!searching) onAction(PlannerAction.Search) },
                enabled = query.isComplete,
                modifier =
                    Modifier
                        .height(height)
                        .then(if (searching) Modifier else Modifier.clip(ButtonDefaults.shape))
                        .closingToCircle(searching) { circle }
                        .semantics { if (searching) stateDescription = searchingText },
                shapes = ButtonDefaults.shapesFor(height),
                contentPadding = if (searching) PaddingValues(0.dp) else ButtonDefaults.contentPaddingFor(height),
            ) {
                Crossfade(searching, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) { busy ->
                    if (busy) {
                        LoadingIndicator(color = LocalContentColor.current)
                    } else {
                        Text(
                            stringResource(R.string.search),
                            style = ButtonDefaults.textStyleFor(height),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lays the content out at its final size straight away, in a box going from the full width at a
 * [progress] of 0 to as wide as it is tall at 1. Whatever is outside the box is up to the caller to clip:
 * unlike with `animateContentSize`, a spring going past the circle doesn't slice into it.
 */
private fun Modifier.closingToCircle(
    closed: Boolean,
    progress: () -> Float,
): Modifier =
    layout { measurable, constraints ->
        val fullWidth = constraints.maxWidth
        val height = constraints.maxHeight
        val width = (fullWidth + (height - fullWidth) * progress()).roundToInt().coerceAtMost(fullWidth)
        val placeable = measurable.measure(Constraints.fixed(if (closed) height else fullWidth, height))
        layout(width, height) { placeable.place(0, 0) }
    }

/** A place on the route line: its label above, the place (or a prompt) below. */
@Composable
private fun PlaceField(
    @StringRes label: Int,
    place: Place?,
    @StringRes placeholder: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    arrivesFromBelow: Boolean = false,
) {
    val motion = MaterialTheme.motionScheme
    Surface(
        onClick = onClick,
        modifier =
            modifier.fillMaxWidth().padding(
                start = ObehyTheme.spacing.s,
                top = ObehyTheme.spacing.xs / 2,
                bottom =
                    ObehyTheme.spacing.xs / 2,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = ObehyTheme.spacing.l, vertical = ObehyTheme.spacing.s),
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The name slides in the way the places trade when they are swapped.
                AnimatedContent(
                    place?.label(),
                    transitionSpec = {
                        val height = { full: Int -> if (arrivesFromBelow) full else -full }
                        val leaving = { full: Int -> -height(full) }
                        (slideInVertically(motion.defaultSpatialSpec(), height) + fadeIn(motion.defaultEffectsSpec()))
                            .togetherWith(
                                slideOutVertically(motion.defaultSpatialSpec(), leaving) +
                                    fadeOut(motion.defaultEffectsSpec()),
                            )
                    },
                    contentAlignment = Alignment.CenterStart,
                ) { name ->
                    Text(
                        name ?: stringResource(placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            if (name != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove_via))
                }
            }
        }
    }
}
