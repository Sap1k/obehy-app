package cz.obehy.core.model

import java.time.Duration
import java.time.Instant

/**
 * One stretch of a trip between two stops, as MOTIS railviz (`/api/v1/map/trips`) gives it:
 * nothing reports where its vehicle is, so a map puts it where [departure] and [arrival] say.
 * [realtime] when those times carry a delay someone shared.
 */
data class RailvizSegment(
    val tripId: String,
    val line: String?,
    val mode: Mode,
    val color: String?,
    val fromName: String,
    val toName: String,
    val departure: TimePoint,
    val arrival: TimePoint,
    val path: List<LatLon>,
)

/** Where railviz puts a trip's vehicle at a moment, and which stretch it is on. */
data class RailvizPlace(
    val position: VehiclePosition,
    val segment: RailvizSegment,
    /** At a stop between two stretches, waiting to leave. */
    val waiting: Boolean,
)

/**
 * Where this trip's vehicle should be at [now], from its stretches in a window: along the one
 * under way, by time; at the stop between two of them; nowhere before the first leaves or after
 * the last arrives (railviz knows nothing outside its window, and nothing is guessed).
 */
fun List<RailvizSegment>.placeAt(now: Instant): RailvizPlace? {
    val ordered = sortedBy { it.departure.expected }
    ordered.forEachIndexed { index, segment ->
        val leaves = segment.departure.expected
        val arrives = segment.arrival.expected
        if (now.isBefore(leaves)) {
            val before = ordered.getOrNull(index - 1) ?: return null
            val at = before.path.lastOrNull() ?: return null
            return RailvizPlace(
                VehiclePosition(at, before.path.bearingAt(at), estimated = true),
                segment,
                waiting = true,
            )
        }
        if (now.isBefore(arrives)) {
            if (segment.path.size < 2) return null
            val span = Duration.between(leaves, arrives).toMillis().coerceAtLeast(1)
            val share = Duration.between(leaves, now).toMillis().toDouble() / span
            val here = segment.path.along(share)
            return RailvizPlace(
                VehiclePosition(here, segment.path.bearingAt(here), estimated = true),
                segment,
                waiting = false,
            )
        }
    }
    return null
}

/** The vehicles of these stretches at [now], one per trip, as a live map shows them. */
fun List<RailvizSegment>.vehiclesAt(now: Instant): List<LiveVehicle> =
    groupBy { it.tripId }.mapNotNull { (tripId, stretches) ->
        val place = stretches.placeAt(now) ?: return@mapNotNull null
        val segment = place.segment
        val delay = segment.departure.delayMinutes
        LiveVehicle(
            id = tripId,
            at = place.position,
            mode = segment.mode,
            line = segment.line,
            color = segment.color,
            textColor = null,
            headsign = null,
            delayMinutes = delay,
            late = Punctuality.isLate(delay),
            trip = TripRef.Motis(tripId),
        )
    }

/** The stretch railviz has [tripId] on at [now]: for "should be near" on its sheet. */
fun List<RailvizSegment>.placeOf(
    tripId: String,
    now: Instant,
): RailvizPlace? = filter { it.tripId == tripId }.placeAt(now)
