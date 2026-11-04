package cz.obehy.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.max
import cz.obehy.core.model.TimePoint
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrentTime(time, MaterialTheme.typography.headlineSmallEmphasized, MaterialTheme.colorScheme.onSurface)
        if (time.moved) TimetableTime(time, MaterialTheme.typography.bodySmall)
    }
}

/**
 * How wide the column in front of a route line must be for all of [times] and for the [notes]
 * beside them (durations, with an icon in front), in the phone's clock style and text size.
 */
@Composable
fun rememberTimeColumnWidth(
    times: List<TimePoint>,
    notes: List<String>,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val time =
        MaterialTheme.typography.titleMedium.tabular
            .copy(fontWeight = FontWeight.Bold)
    val note = MaterialTheme.typography.bodySmall
    val icon = ObehyTheme.sizes.iconSmall
    val gap = ObehyTheme.spacing.xs
    return remember(times, notes, density, time, note, icon, gap) {
        fun widest(
            texts: List<String>,
            style: TextStyle,
        ): Dp = with(density) { (texts.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0).toDp() }
        max(widest(times.map { it.expected.clock() }, time), widest(notes, note) + icon + gap) + gap
    }
}

/**
 * The time that counts at a stop, right-aligned in a column [width] wide next to the route line.
 * [small] is for a stop passed on the way.
 */
@Composable
fun TimeColumn(
    time: TimePoint,
    width: Dp,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val spoken = spokenTime(time)
    Box(
        modifier
            .width(width)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(end = ObehyTheme.spacing.xs),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (small) {
            CurrentTime(time, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            CurrentTime(time, MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * What a [TimeColumn]'s time is based on, for the end of the stop's row: the timetable time,
 * struck through, when the live one differs, or the live mark when it is live and on time.
 * [small] is for a stop passed on the way, which leaves the mark out. [TimeColumn] already speaks
 * all of this.
 */
@Composable
fun TimeBasis(
    time: TimePoint,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Box(modifier.clearAndSetSemantics {}) {
        when {
            time.moved -> TimetableTime(time, MaterialTheme.typography.bodySmall)
            time.realtime && !small -> LiveMark()
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
    val motion = MaterialTheme.motionScheme
    val colour by animateColorAsState(
        when {
            !time.realtime -> timetableColour
            (time.delayMinutes ?: 0) >= 1 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        motion.defaultEffectsSpec(),
    )
    // Rolls up to a later time and down to an earlier one, so a refresh's change gets noticed.
    AnimatedContent(
        time.expected.truncatedTo(ChronoUnit.MINUTES),
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            val entering =
                slideInVertically(motion.fastSpatialSpec()) { it * direction } + fadeIn(motion.defaultEffectsSpec())
            val leaving =
                slideOutVertically(motion.fastSpatialSpec()) { -it * direction } + fadeOut(motion.defaultEffectsSpec())
            entering.togetherWith(leaving).using(SizeTransform(clip = true))
        },
        contentAlignment = Alignment.CenterEnd,
    ) { minute ->
        Text(
            minute.clock(),
            style = style.tabular,
            color = colour,
            fontWeight = if (time.realtime) FontWeight.Bold else null,
            maxLines = 1,
            softWrap = false,
        )
    }
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
