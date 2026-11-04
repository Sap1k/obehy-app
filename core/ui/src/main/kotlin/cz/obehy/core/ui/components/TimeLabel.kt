package cz.obehy.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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

private val TimePoint.moved: Boolean get() = realtime && expected.clock() != scheduled.clock()

/**
 * A time in a line of text (a result's "14:31 → 17:12"), telling live times from timetable times
 * without relying on colour:
 * - from the timetable only: plain;
 * - live and on time: bold in the primary colour;
 * - live and moved: the new time bold (in the error colour when late) with the timetable time
 *   struck through next to it.
 */
@Composable
fun TimeLabel(
    time: TimePoint,
    modifier: Modifier = Modifier,
) {
    val spoken = spokenTime(time)
    Row(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        CurrentTime(time, MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSurface)
        if (time.moved) TimetableTime(time, MaterialTheme.typography.bodyMedium)
    }
}

/** How wide [TimeColumns] are, so they line up down a journey. */
@Immutable
data class TimeWidths(
    /** Zero when nothing in the journey is live. */
    val timetable: Dp,
    val current: Dp,
) {
    val total: Dp get() = timetable + current
}

/** The [TimeWidths] that fit all of [times], in the phone's clock style and text size. */
@Composable
fun rememberTimeWidths(times: List<TimePoint>): TimeWidths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val current =
        MaterialTheme.typography.titleSmall.tabular
            .copy(fontWeight = FontWeight.Bold)
    val timetable = MaterialTheme.typography.bodySmall.tabular
    val mark = ObehyTheme.sizes.iconSmall
    val gap = ObehyTheme.spacing.xs
    return remember(times, density, current, timetable, mark, gap) {
        fun widest(
            texts: List<String>,
            style: TextStyle,
        ): Dp = with(density) { (texts.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0).toDp() }
        TimeWidths(
            timetable =
                if (times.any { it.realtime }) {
                    max(mark, widest(times.filter { it.moved }.map { it.scheduled.clock() }, timetable)) + gap
                } else {
                    0.dp
                },
            current = widest(times.map { it.expected.clock() }, current) + gap,
        )
    }
}

/**
 * A stop's times in two right-aligned columns, like a printed timetable: the timetable time, struck through,
 * when the live one differs from it, then the time that counts next to the route line. An
 * on-time live time has the live mark in the first column. [small] is for a stop passed on the
 * way, which leaves the mark out.
 */
@Composable
fun TimeColumns(
    time: TimePoint,
    widths: TimeWidths,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val spoken = spokenTime(time)
    val gap = ObehyTheme.spacing.xs
    // The total sets the width, like rows without a time: two widths rounded apart would nudge the route line.
    Row(
        modifier.width(widths.total).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(widths.timetable)
                .then(if (time.moved) Modifier.alignByBaseline() else Modifier)
                .padding(end = gap),
            contentAlignment = Alignment.CenterEnd,
        ) {
            when {
                time.moved -> TimetableTime(time, MaterialTheme.typography.bodySmall)
                time.realtime && !small -> LiveMark()
            }
        }
        Box(Modifier.weight(1f).alignByBaseline().padding(end = gap), contentAlignment = Alignment.CenterEnd) {
            if (small) {
                CurrentTime(time, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                CurrentTime(time, MaterialTheme.typography.titleSmall, MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Digits of one width, so times line up down a column. */
private val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

@Composable
private fun CurrentTime(
    time: TimePoint,
    style: TextStyle,
    timetableColour: Color,
) {
    val colour =
        when {
            !time.realtime -> timetableColour
            (time.delayMinutes ?: 0) >= 1 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
    Text(
        time.expected.clock(),
        style = style.tabular,
        color = colour,
        fontWeight = if (time.realtime) FontWeight.Bold else null,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun TimetableTime(
    time: TimePoint,
    style: TextStyle,
) {
    Text(
        time.scheduled.clock(),
        style = style.tabular,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = TextDecoration.LineThrough,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun spokenTime(time: TimePoint): String =
    when {
        time.moved -> stringResource(R.string.time_moved, time.expected.clock(), time.scheduled.clock())
        time.realtime -> stringResource(R.string.time_live, time.expected.clock())
        else -> time.scheduled.clock()
    }
