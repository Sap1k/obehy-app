package cz.obehy.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import cz.obehy.core.model.HitKind
import cz.obehy.core.model.VehicleHit
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.DelayLabel
import cz.obehy.core.ui.components.LineBadge
import cz.obehy.core.ui.components.SegmentedRows
import cz.obehy.core.ui.components.StateMessage

/**
 * Finding a vehicle by its line, train number or fleet number, over the map: the matches grouped
 * the way the API ranks them, nearest first in each. Picking one opens it on the map.
 */
@Composable
internal fun VehicleSearch(
    search: SearchState,
    onQuery: (String) -> Unit,
    onPick: (VehicleHit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().imePadding().padding(horizontal = spacing.m)) {
            TextField(
                value = search.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s).focusRequester(focus),
                placeholder = { Text(stringResource(R.string.map_search_hint)) },
                leadingIcon = {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.map_search_close),
                        )
                    }
                },
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.map_search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors =
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            when {
                search.searching && search.hits.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(spacing.xl), Alignment.Center) { LoadingIndicator() }
                }

                search.failed && search.hits.isEmpty() -> {
                    StateMessage(Icons.Rounded.CloudOff, stringResource(R.string.map_search_failed))
                }

                search.query.isNotBlank() && search.hits.isEmpty() && !search.searching -> {
                    StateMessage(Icons.Rounded.SearchOff, stringResource(R.string.map_search_empty))
                }

                else -> {
                    Hits(search.hits, onPick)
                }
            }
        }
    }
}

@Composable
private fun Hits(
    hits: List<VehicleHit>,
    onPick: (VehicleHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        hits.groupBy { it.matched }.toSortedMap().forEach { (kind, group) ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(
                    stringResource(kind.title),
                    Modifier.padding(start = spacing.s, top = spacing.s),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedRows(group) { hit -> HitRow(hit, Modifier.clickable { onPick(hit) }) }
            }
        }
    }
}

@Composable
private fun HitRow(
    hit: VehicleHit,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    val vehicle = hit.vehicle
    Row(
        modifier.fillMaxWidth().padding(spacing.m),
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(vehicle.mode, vehicle.line.orEmpty(), vehicle.color, vehicle.textColor)
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            Modifier.size(ObehyTheme.sizes.iconSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                vehicle.headsign.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val number = hit.number?.takeIf { hit.matched == HitKind.VehicleNumber }
            val trainNumber = vehicle.tripShortName?.takeIf { hit.matched == HitKind.TrainNumber }
            (trainNumber ?: number?.let { stringResource(R.string.map_search_fleet, it) })?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (vehicle.late) DelayLabel(vehicle.delayMinutes)
        hit.metres?.let { metres ->
            Text(
                stringResource(R.string.map_search_distance, distance(metres)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun distance(metres: Int): String =
    if (metres < KILOMETRE) {
        stringResource(R.string.map_metres, metres)
    } else {
        stringResource(R.string.map_kilometres, metres / KILOMETRE.toDouble())
    }

private val HitKind.title: Int
    get() =
        when (this) {
            HitKind.TrainNumber -> R.string.map_search_train_numbers
            HitKind.Line -> R.string.map_search_lines
            HitKind.VehicleNumber -> R.string.map_search_vehicle_numbers
            HitKind.Other -> R.string.map_search_other
        }

private const val KILOMETRE = 1_000
