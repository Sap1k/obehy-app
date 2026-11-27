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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
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
import cz.obehy.core.model.TimePoint
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A clock format, kept because a list of results asks for it many times a row. */
private class ClockFormat(
    val locale: Locale,
    val zone: ZoneId,
) {
    val formatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
}

@Volatile
private var clockFormat: ClockFormat? = null

/** The time in the phone's zone and clock style (14:31, or 2:31 PM). */
fun Instant.clock(): String {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    val zone = ZoneId.systemDefault()
    val format =
        clockFormat?.takeIf { it.locale == locale && it.zone == zone }
            ?: ClockFormat(locale, zone).also { clockFormat = it }
    return format.formatter.format(this)
}

/** The time now, moving on every [everyMillis]. */
@Composable
fun rememberNow(everyMillis: Long): State<Instant> =
    produceState(Instant.now(), everyMillis) {
        while (true) {
            value = Instant.now()
            delay(everyMillis)
        }
    }

/**
 * The clock for something that creeps along, to be read only while drawing: a tick moves it by less
 * than a pixel, and redraws without recomposing. It rests while no frames are being drawn.
 */
@Composable
fun rememberSmoothNow(): State<Instant> =
    produceState(Instant.now()) {
        while (true) {
            withFrameMillis { value = Instant.now() }
            delay(SMOOTH_STEP_MS)
        }
    }

private const val SMOOTH_STEP_MS = 250L

/** Whether [minute] is off the timetable, by the clock as it reads. */
private fun TimePoint.movedAt(minute: Instant): Boolean =
    realtime && minute.truncatedTo(ChronoUnit.MINUTES) != scheduled.truncatedTo(ChronoUnit.MINUTES)

private val TimePoint.moved: Boolean
    get() = movedAt(expected)

/**
 * A time in a line of text (a result's "14:31 → 17:12"), telling live times from timetable times
 * without relying on colour:
 * - from the timetable only: plain;
 * - live: bold, in its punctuality's colour;
 * - live and moved off the timetable: the timetable time small and struck through in front of it,
 *   on the same line, so the row keeps its height and the live times stay lined up.
 */
@Composable
fun TimeLabel(
    time: TimePoint,
    modifier: Modifier = Modifier,
) {
    val spoken = spokenTime(time)
    CurrentTime(
        time,
        MaterialTheme.typography.headlineSmallEmphasized,
        MaterialTheme.typography.bodyMedium,
        MaterialTheme.colorScheme.onSurface,
        modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

/** How wide the column in front of a route line must be for all of [times], in the phone's clock style and text size. */
@Composable
fun rememberTimeColumnWidth(times: List<TimePoint>): Dp =
    rememberTimesWidth(times, MaterialTheme.typography.titleMedium, MaterialTheme.typography.labelMedium) +
        ObehyTheme.spacing.xs

/** How wide the time column of a schedule must be for all of [times]. */
@Composable
fun rememberScheduleTimeWidth(times: List<TimePoint>): Dp =
    rememberTimesWidth(times, MaterialTheme.typography.bodyMedium, MaterialTheme.typography.labelSmall)

/** The widest of [times] as [CurrentTime] draws them in [style], with the timetable's own in [plannedStyle]. */
@Composable
private fun rememberTimesWidth(
    times: List<TimePoint>,
    style: TextStyle,
    plannedStyle: TextStyle,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val time = style.tabular.copy(fontWeight = FontWeight.Bold)
    val planned = plannedStyle.tabular
    val gap = PlannedGap
    return remember(times, density, time, planned, gap) {
        val widest =
            times.maxOfOrNull { point ->
                val clock = measurer.measure(point.expected.clock(), time).size.width
                if (!point.moved) return@maxOfOrNull clock
                clock + measurer.measure(point.scheduled.clock(), planned).size.width +
                    with(density) { gap.roundToPx() }
            } ?: 0
        with(density) { widest.toDp() }
    }
}

/**
 * A time in a schedule, one size for every stop; [passed] once the trip is past it. Moved off the
 * timetable, the timetable time is struck through in front of it, small.
 */
@Composable
fun ScheduleTime(
    time: TimePoint,
    modifier: Modifier = Modifier,
    passed: Boolean = false,
) {
    val spoken = spokenTime(time)
    CurrentTime(
        time,
        MaterialTheme.typography.bodyMedium,
        MaterialTheme.typography.labelSmall,
        MaterialTheme.colorScheme.onSurfaceVariant,
        modifier.clearAndSetSemantics { contentDescription = spoken },
        passed,
    )
}

/**
 * The time that counts at a stop, right-aligned in a column [width] wide next to the route line,
 * the timetable time small and struck through in front of it when it has moved. [small] is for a
 * stop passed on the way, [passed] for one the journey has already got past.
 */
@Composable
fun TimeColumn(
    time: TimePoint,
    width: Dp,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    passed: Boolean = false,
) {
    val spoken = spokenTime(time)
    Box(
        modifier
            .width(width)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(end = ObehyTheme.spacing.xs),
        Alignment.TopEnd,
    ) {
        val colors = MaterialTheme.colorScheme
        if (small) {
            CurrentTime(
                time,
                MaterialTheme.typography.bodySmall,
                MaterialTheme.typography.labelSmall,
                colors.onSurfaceVariant,
                passed = passed,
            )
        } else {
            CurrentTime(
                time,
                MaterialTheme.typography.titleMedium,
                MaterialTheme.typography.labelMedium,
                if (passed) colors.onSurfaceVariant else colors.onSurface,
                passed = passed,
            )
        }
    }
}

/**
 * What a [TimeColumn]'s time is based on, for the end of the stop's row: the live mark when it's
 * live and on time (a moved time says it's live by its colour and the struck timetable time).
 * [small] is for a stop passed on the way, which leaves it out. [TimeColumn] already speaks all of
 * this.
 */
@Composable
fun TimeBasis(
    time: TimePoint,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    if (time.realtime && !time.moved && !small) Box(modifier.clearAndSetSemantics {}) { LiveMark() }
}

/**
 * The timetable time of a live [time] that has moved off it, small, quiet and struck through, to
 * go in front of what gives the time now (a countdown); nothing when it hasn't moved.
 */
@Composable
fun PlannedTime(
    time: TimePoint,
    modifier: Modifier = Modifier,
) {
    if (time.moved) Planned(time, MaterialTheme.typography.labelMedium, modifier)
}

/** Digits of one width, so times line up down a column. */
private val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

/** Between the struck timetable time and the time now. */
private val PlannedGap: Dp
    @Composable get() = ObehyTheme.spacing.xs

/**
 * The time that counts in [style], and when it's live and off the timetable, the timetable time
 * struck through in [plannedStyle] in front of it, on its baseline.
 */
@Composable
private fun CurrentTime(
    time: TimePoint,
    style: TextStyle,
    plannedStyle: TextStyle,
    timetableColour: Color,
    modifier: Modifier = Modifier,
    passed: Boolean = false,
) {
    val motion = MaterialTheme.motionScheme
    val colour by animateColorAsState(
        when {
            !time.realtime || passed -> timetableColour
            else -> PunctualityTone.of(time.delayMinutes).text
        },
        motion.defaultEffectsSpec(),
    )
    val gap = PlannedGap
    // Rolls up to a later time and down to an earlier one, so a refresh's change gets noticed.
    AnimatedContent(
        time.expected.truncatedTo(ChronoUnit.MINUTES),
        modifier,
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
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (time.movedAt(minute)) Planned(time, plannedStyle, Modifier.alignByBaseline())
            Text(
                minute.clock(),
                Modifier.alignByBaseline(),
                style = style.tabular,
                color = colour,
                fontWeight = if (time.realtime) FontWeight.Bold else null,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The timetable time a live time has moved off: small, quiet and struck through. */
@Composable
private fun Planned(
    time: TimePoint,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        time.scheduled.clock(),
        modifier,
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
