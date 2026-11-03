package cz.obehy.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import cz.obehy.core.model.TimePoint
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The time in the phone's zone and clock style (14:31, or 2:31 PM). */
fun Instant.clock(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(this)

/** How big a [TimeLabel] is, and where the timetable time goes when it differs. */
enum class TimeLabelSize {
    /** A stop on a journey: the time over the timetable time. */
    Large,

    /** A stop passed on the way: smaller, same layout. */
    Small,

    /** In a line of text (a result's "14:31 → 17:12"): the timetable time beside the live one. */
    Inline,
}

/**
 * A time, telling live times from timetable times without relying on colour:
 * - from the timetable only: plain;
 * - live and on time: bold in the primary colour, with the live mark;
 * - live and moved: the new time bold (in the error colour when late) with the timetable time
 *   struck through next to it.
 */
@Composable
fun TimeLabel(
    time: TimePoint,
    modifier: Modifier = Modifier,
    size: TimeLabelSize = TimeLabelSize.Large,
) {
    val style =
        when (size) {
            TimeLabelSize.Large -> MaterialTheme.typography.titleSmall
            TimeLabelSize.Small -> MaterialTheme.typography.bodySmall
            TimeLabelSize.Inline -> MaterialTheme.typography.titleMedium
        }
    val moved = time.realtime && time.expected.clock() != time.scheduled.clock()
    val late = (time.delayMinutes ?: 0) >= 1
    val colour =
        when {
            !time.realtime -> {
                if (size ==
                    TimeLabelSize.Small
                ) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            }

            late -> {
                MaterialTheme.colorScheme.error
            }

            else -> {
                MaterialTheme.colorScheme.primary
            }
        }
    val spoken =
        when {
            moved -> stringResource(R.string.time_moved, time.expected.clock(), time.scheduled.clock())
            time.realtime -> stringResource(R.string.time_live, time.expected.clock())
            else -> time.scheduled.clock()
        }
    val now: @Composable () -> Unit = {
        Text(
            time.expected.clock(),
            style = style,
            color = colour,
            fontWeight = if (time.realtime) FontWeight.Bold else null,
        )
    }
    val planned: @Composable () -> Unit = {
        Text(
            time.scheduled.clock(),
            style = smaller(style),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
        )
    }
    val semantics = modifier.clearAndSetSemantics { contentDescription = spoken }
    if (size == TimeLabelSize.Inline) {
        Row(
            semantics,
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            now()
            if (moved) planned()
        }
    } else {
        Column(semantics) {
            now()
            when {
                moved -> planned()
                time.realtime && size == TimeLabelSize.Large -> LiveMark()
            }
        }
    }
}

@Composable
private fun smaller(style: TextStyle): TextStyle =
    when (style) {
        MaterialTheme.typography.titleMedium -> MaterialTheme.typography.bodyMedium
        else -> MaterialTheme.typography.bodySmall
    }
