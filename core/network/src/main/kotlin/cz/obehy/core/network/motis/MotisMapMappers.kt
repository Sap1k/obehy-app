package cz.obehy.core.network.motis

import cz.obehy.core.model.LatLon
import cz.obehy.core.model.LiveStop
import cz.obehy.core.model.Punctuality
import cz.obehy.core.model.RailvizSegment
import cz.obehy.core.model.StopDeparture
import cz.obehy.core.model.TimePoint
import cz.obehy.core.model.TripRef

/** The precision railviz polylines are asked for (MOTIS's default). */
const val RAILVIZ_PRECISION = 5

/** This stretch for a map; null without a path to put it on. */
fun MotisMapTrip.toSegment(precision: Int = RAILVIZ_PRECISION): RailvizSegment? {
    val trip = trips.firstOrNull() ?: return null
    val path = if (polyline.isEmpty()) emptyList() else decodePolyline(polyline, precision)
    if (path.size < 2) return null
    return RailvizSegment(
        tripId = trip.tripId,
        line = trip.displayName ?: trip.routeShortName,
        mode = motisMode(mode),
        color = routeColor,
        fromName = from.name,
        toName = to.name,
        departure = requireNotNull(timePoint(scheduledDeparture, departure, realTime)),
        arrival = requireNotNull(timePoint(scheduledArrival, arrival, realTime)),
        path = path,
    )
}

/** MOTIS's stops in a box, as a live map shows them: one per station, its platforms folded in. */
fun List<MotisStop>.toLiveStops(): List<LiveStop> =
    groupBy { it.parentId ?: it.stopId ?: "${it.lat},${it.lon}" }.map { (id, platforms) ->
        val first = platforms.first()
        LiveStop(
            id = id,
            name = first.name,
            position = LatLon(platforms.map { it.lat }.average(), platforms.map { it.lon }.average()),
            modes = platforms.flatMapTo(mutableSetOf()) { it.domainModes() },
        )
    }

/** These stop times as a board's rows: with [arrivals], when each gets there. */
fun MotisStopTimes.toDepartures(arrivals: Boolean = false): List<StopDeparture> =
    stopTimes.map { time ->
        val call = time.place.toStopCall(time.realTime)
        val at: TimePoint =
            requireNotNull(if (arrivals) call.arrival ?: call.departure else call.departure ?: call.arrival) {
                "a stop time without a time"
            }
        StopDeparture(
            trip = TripRef.Motis(time.tripId),
            mode = motisMode(time.mode),
            line = time.displayName ?: time.routeShortName,
            color = time.routeColor,
            textColor = time.routeTextColor,
            headsign = time.headsign,
            time = at,
            late = Punctuality.isLate(at.delayMinutes),
            cancelled = time.cancelled || time.tripCancelled,
            platform = call.track,
        )
    }
