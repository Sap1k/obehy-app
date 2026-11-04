package cz.obehy.feature.planner

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.ui.components.clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Times are shown in the phone's time zone and its locale's clock (14:31, or 2:31 PM).

@Composable
internal fun Duration.spoken(): String {
    val minutes = toMinutes().coerceAtLeast(0)
    return if (minutes >= 60) {
        stringResource(R.string.duration_hours, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

@Composable
internal fun transfersText(transfers: Int): String =
    when (transfers) {
        0 -> stringResource(R.string.direct)
        else -> pluralStringResource(R.plurals.transfers, transfers, transfers)
    }

/** What a place is called on screen. */
@Composable
internal fun Place.label(): String = if (kind == PlaceKind.MyLocation) stringResource(R.string.my_location) else name

/** "120 m", or "1.4 km" further out. */
@Composable
internal fun distanceText(metres: Int): String =
    if (metres < 1_000) {
        stringResource(R.string.distance_metres, metres / 10 * 10)
    } else {
        stringResource(R.string.distance_kilometres, metres / 1_000.0)
    }

/** "Leave now", "Leave 14:30", "Arrive by Wed 5 Nov 8:00". */
@Composable
internal fun JourneyQuery.timeLabel(): String {
    val time = time ?: return stringResource(R.string.leave_now)
    val zone = ZoneId.systemDefault()
    val date = time.atZone(zone).toLocalDate()
    val day =
        if (date == LocalDate.now(zone)) {
            ""
        } else {
            DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern)).format(date) + " "
        }
    return stringResource(if (arriveBy) R.string.arrive_by else R.string.leave_at, day + time.clock())
}
