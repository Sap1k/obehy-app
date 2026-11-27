package cz.obehy.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DepartureBoard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import cz.obehy.core.model.BoardSide
import cz.obehy.core.model.LiveStop
import cz.obehy.core.model.StopBoard
import cz.obehy.core.model.StopDeparture
import cz.obehy.core.model.TripRef
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.LineBadge
import cz.obehy.core.ui.components.LowFloorIcon
import cz.obehy.core.ui.components.NoticeCard
import cz.obehy.core.ui.components.PlannedTime
import cz.obehy.core.ui.components.PlatformLabel
import cz.obehy.core.ui.components.PunctualityTone
import cz.obehy.core.ui.components.SegmentedRows
import cz.obehy.core.ui.components.StateMessage
import cz.obehy.core.ui.components.clock
import cz.obehy.core.ui.components.rememberLineBadgeWidth
import cz.obehy.core.ui.components.text
import cz.obehy.core.ui.icon
import java.time.Duration
import java.time.Instant

/**
 * A stop opened on the map, as its board: its name and modes up by close, departures or arrivals
 * to switch between, the stop's own notices, then the rows (each opening its trip on the map) and
 * a button for later ones. It peeks with its first few rows in view.
 */
@Composable
internal fun StopSheet(
    stop: StopState,
    actions: MapActions,
    maxHeight: Dp,
    peek: Dp,
    sheetPeek: SheetPeek,
    resting: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    val arrivals = stop.side == BoardSide.Arrivals
    SheetFrame(
        maxHeight,
        peek,
        resting,
        sheetPeek = sheetPeek,
        top = {
            SheetTop(onClose = actions::closeAll, title = { StopTitle(stop.stop) })
        },
        modifier = modifier,
    ) {
        BoardSwitch(stop.side, actions::showBoard)
        val board = stop.board
        board?.notices?.forEach { NoticeCard(it.title, text = it.text, severe = it.severe) }
        when {
            board != null && board.departures.isEmpty() -> {
                StateMessage(
                    Icons.Rounded.DepartureBoard,
                    stringResource(if (arrivals) R.string.map_board_empty_arrivals else R.string.map_board_empty),
                    sheetPeek.mark(stop.side to 0),
                )
            }

            board != null -> {
                Rows(board, stop.side, sheetPeek, onOpen = actions::selectTrip)
                if (board.next != null) LaterButton(arrivals, stop.loadingMore, actions::moreBoard)
            }

            stop.failed -> {
                StateMessage(
                    Icons.Rounded.CloudOff,
                    stringResource(if (arrivals) R.string.map_board_failed_arrivals else R.string.map_board_failed),
                    sheetPeek.mark(stop.side to FAILED),
                    action = stringResource(R.string.map_retry),
                    onAction = actions::loadBoard,
                )
            }

            else -> {
                Box(Modifier.fillMaxWidth().padding(spacing.l), Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}

/** Departures or arrivals, as a connected pair: the one shown is Lime. */
@Composable
private fun BoardSwitch(
    side: BoardSide,
    onSide: (BoardSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        ToggleButtonDefaults.colors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        BoardSide.entries.forEachIndexed { index, each ->
            ToggleButton(
                checked = side == each,
                onCheckedChange = { onSide(each) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes =
                    if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                colors = colors,
            ) {
                Text(
                    stringResource(
                        if (each == BoardSide.Arrivals) R.string.map_board_arrivals else R.string.map_board_departures,
                    ),
                )
            }
        }
    }
}

/** The board's rows, lined up on one width of line; the peek ends under the first few. */
@Composable
private fun Rows(
    board: StopBoard,
    side: BoardSide,
    sheetPeek: SheetPeek,
    onOpen: (TripRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = board.departures
    val peeking = rows[minOf(PEEK_ROWS, rows.size) - 1]
    // One width for every line, so where they go lines up.
    val lineWidth = rememberLineBadgeWidth(rows.map { it.line.orEmpty() })
    SegmentedRows(rows, modifier) { row ->
        DepartureRow(
            row,
            board.at,
            lineWidth,
            Modifier
                .clickable { onOpen(row.trip) }
                .then(if (row === peeking) sheetPeek.mark(side to minOf(PEEK_ROWS, rows.size)) else Modifier),
        )
    }
}

/** More rows, after the last shown; a loading indicator in it while they come. */
@Composable
private fun LaterButton(
    arrivals: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), Alignment.Center) {
        FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), enabled = !loading) {
            if (loading) {
                LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
            } else {
                Text(stringResource(if (arrivals) R.string.map_board_more_arrivals else R.string.map_board_more))
            }
        }
    }
}

/** The stop's name, after a small sign for each mode stopping there, as at the kerb. */
@Composable
private fun RowScope.StopTitle(
    stop: LiveStop,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    stop.modes
        .sortedBy { it.ordinal }
        .distinctBy { it.icon }
        .forEach { mode ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    mode.icon,
                    contentDescription = null,
                    Modifier.padding(spacing.xs).size(ObehyTheme.sizes.iconSmall),
                )
            }
        }
    Text(
        stop.name,
        modifier.weight(1f),
        style = MaterialTheme.typography.titleLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * One row on two lines: its line, in a column [lineWidth] wide, then → where it goes, across the
 * whole row; under that its platform, its train number and whether it's step-free, and at the end
 * when, large, after the timetable time struck through when it has moved.
 */
@Composable
private fun DepartureRow(
    departure: StopDeparture,
    now: Instant,
    lineWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val minutes = Duration.between(now, departure.time.expected).toMinutes()
    val soon = !departure.cancelled && minutes < SOON_MINUTES
    Row(
        modifier.fillMaxWidth().padding(horizontal = spacing.m, vertical = spacing.m),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(lineWidth)) {
            LineBadge(
                departure.mode,
                departure.line.orEmpty(),
                departure.color,
                departure.textColor,
                cancelled = departure.cancelled,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    Modifier.size(ObehyTheme.sizes.iconSmall),
                    tint = quiet,
                )
                Text(
                    departure.headsign.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (departure.cancelled) TextDecoration.LineThrough else null,
                )
            }
            // Under where it goes, which has the whole width: its platform and the rest, then when,
            // on one line so every row keeps its height (the timetable time struck through when it
            // has moved, then the countdown or clock).
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    departure.platform?.let { PlatformLabel(it, changed = false) }
                    departure.number?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = quiet, maxLines = 1)
                    }
                    if (departure.stepFree) LowFloorIcon()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    if (!departure.cancelled) PlannedTime(departure.time, Modifier.alignByBaseline())
                    Text(
                        when {
                            departure.cancelled -> stringResource(R.string.map_cancelled)
                            minutes <= 0 -> stringResource(R.string.map_departure_now)
                            soon -> stringResource(R.string.map_departure_in, minutes)
                            else -> departure.time.expected.clock()
                        },
                        Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.titleMedium,
                        // Its tone, as on the web's boards; the timetable's own time stays plain.
                        color =
                            if (departure.time.realtime || departure.cancelled) {
                                PunctualityTone.of(departure.time.delayMinutes, departure.cancelled).text
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Under this, a row says "in N min" rather than its clock time. */
private const val SOON_MINUTES = 15

/** How many rows the sheet peeks with. */
private const val PEEK_ROWS = 3

/** What the peek is measured for when the board couldn't be had. */
private const val FAILED = -1
