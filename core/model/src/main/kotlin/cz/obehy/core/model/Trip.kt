package cz.obehy.core.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos

/** What a timetable says about getting on or off at one stop. */
enum class CallFlag { RequestStop, ExitOnly, BoardingOnly }

/** A stop of a whole trip. [sequence] is its place in Oběhy's timetable, when the stop comes from there. */
data class TripStop(
    val call: StopCall,
    val sequence: Int? = null,
    val flags: Set<CallFlag> = emptySet(),
)

/**
 * A whole trip, not only the part of it on a journey. [ride] is the trip as MOTIS knows it, which
 * every server can answer; [stops] its stops, live from Oběhy when [info] is there. [boarded] is
 * which of the [stops] the journey rides between, when that's known.
 */
data class TripDetail(
    val ride: Leg.Transit,
    val stops: List<TripStop>,
    val boarded: IntRange?,
    val info: TripInfo? = null,
    /** How it's drawn: made once, so refreshed times keep the same map. */
    val map: TripMap = TripMap.of(ride, stops, boarded),
) {
    val cancelled: Boolean get() = ride.cancelled || info?.cancelled == true

    /** Whole minutes late now, null when nothing live says so. */
    val delayMinutes: Long?
        get() =
            info?.delayMinutes
                ?: stops
                    .asSequence()
                    .mapNotNull { it.call.departure ?: it.call.arrival }
                    .firstNotNullOfOrNull { it.delayMinutes }

    /**
     * Where the vehicle should be at [now], going by the expected times along the path: a stand-in
     * where nothing reports the real position. Null before it sets off and after it arrives.
     */
    fun estimatedPosition(now: Instant): VehiclePosition? {
        if (cancelled) return null
        val calls = stops.map { it.call }
        calls.zipWithNext().forEachIndexed { index, (from, to) ->
            val leaves = (from.departure ?: from.arrival)?.expected ?: return@forEachIndexed
            val arrives = (to.arrival ?: to.departure)?.expected ?: return@forEachIndexed
            if (index == 0 && now.isBefore(leaves)) return null
            if (now.isBefore(leaves)) return from.position?.let { VehiclePosition(it, estimated = true) }
            if (now.isBefore(arrives)) {
                val start = from.position ?: return null
                val end = to.position ?: return null
                val share =
                    Duration.between(leaves, now).toMillis().toDouble() / Duration.between(leaves, arrives).toMillis()
                val way = ride.path.between(start, end).takeIf { it.size >= 2 } ?: listOf(start, end)
                val here = way.along(share)
                return VehiclePosition(here, way.bearingAt(here), estimated = true)
            }
        }
        return null
    }

    /** Where to show this trip's vehicles at [now]: what's reported, or else where one should be. */
    fun vehiclesAt(now: Instant): List<VehiclePosition> =
        info
            ?.positions
            .orEmpty()
            .map { it.copy(bearing = it.bearing ?: ride.path.bearingAt(it.position)) }
            .ifEmpty { listOfNotNull(estimatedPosition(now)) }

    /** Everything said about disruptions, each once. */
    val alerts: List<TripAlert> get() = (ride.alerts + info?.alerts.orEmpty()).distinctBy { it.title }

    /** What a rider looks for first, most wanted first; each kind of thing once. */
    val attributes: List<MarkedNote>
        get() {
            val notes = info?.notes?.marked.orEmpty()
            val feed =
                buildList {
                    if (ride.accessible == true) add(MarkedNote("", "wheelchair"))
                    if (ride.bikesAllowed == true) add(MarkedNote("", "bicycle"))
                    when (ride.reservation) {
                        Reservation.Required -> add(MarkedNote("", "reservation_required"))
                        Reservation.Possible -> add(MarkedNote("", "reservation"))
                        null -> Unit
                    }
                }
            // The timetable's notes are the fuller account, so where they speak the feed is not asked.
            return ATTRIBUTE_GROUPS.mapNotNull { group -> notes.strongestOf(group) ?: feed.strongestOf(group) }
        }

    companion object {
        /** The stops of [ride], a whole trip: its two ends and everything in between. */
        fun stopsOf(ride: Leg.Transit): List<TripStop> =
            (listOf(ride.from) + ride.intermediateStops + ride.to).map { call ->
                TripStop(
                    call,
                    flags =
                        buildSet {
                            if (call.noBoarding) add(CallFlag.ExitOnly)
                            if (call.noAlighting) add(CallFlag.BoardingOnly)
                        },
                )
            }
    }
}

private fun List<MarkedNote>.strongestOf(names: List<String>): MarkedNote? =
    names.firstNotNullOfOrNull { name -> find { it.icon == name } }

/** The kinds of thing worth a sign beside a trip's name, each as the names that mean it, strongest first. */
private val ATTRIBUTE_GROUPS =
    listOf(
        listOf("wheelchair", "wheelchair_lift", "wheelchair_booking", "wheelchair_partly"),
        listOf("bicycle_reservation", "bicycle"),
        listOf("reservation_required", "reservation"),
        listOf("restaurant", "refreshments"),
        listOf("sleeper", "couchette"),
        listOf("wifi"),
        listOf("power_socket"),
        listOf("quiet"),
        listOf("family"),
    )

/** A stop on the way that a map names only once there's room. [ridden] when the journey rides past it. */
data class MapStop(
    val position: LatLon,
    val name: String,
    val ridden: Boolean,
)

/**
 * The rest of a trip around the legs on a map: drawn quietly, and framed along with them. [approach]
 * is the way its vehicle comes to where the journey boards, in the route's [approachColor].
 */
data class RouteSurroundings(
    val path: List<LatLon> = emptyList(),
    val stops: List<MapStop> = emptyList(),
    val approach: List<LatLon> = emptyList(),
    val approachColor: String? = null,
)

/** A trip on a map: the stretch ridden in its colour, or all of it if that isn't known, the rest quietly around it. */
data class TripMap(
    val legs: List<Leg>,
    val around: RouteSurroundings,
) {
    companion object {
        fun of(
            ride: Leg.Transit,
            stops: List<TripStop>,
            boarded: IntRange?,
        ): TripMap {
            val leg = boarded?.let { ride.part(stops[it.first].call, stops[it.last].call) } ?: ride
            val others =
                stops.mapIndexedNotNull { index, stop ->
                    val at = stop.call.position
                    if (at == null || at == leg.from.position || at == leg.to.position) {
                        null
                    } else {
                        MapStop(at, stop.call.name, boarded == null || index in boarded)
                    }
                }
            val rest = ride.path.takeIf { leg !== ride }.orEmpty()
            val approach =
                leg.from.position
                    ?.takeIf { boarded?.first != 0 }
                    ?.let { rest.upTo(it) }
                    .orEmpty()
            return TripMap(listOf(leg), RouteSurroundings(rest, others, approach, ride.color))
        }
    }
}

/** The part of this ride between two of its stops, with its own stretch of the path. */
internal fun Leg.Transit.part(
    from: StopCall,
    to: StopCall,
): Leg.Transit {
    val start = from.position
    val end = to.position
    val stretch = if (start != null && end != null) path.between(start, end) else path
    return copy(from = from, to = to, intermediateStops = emptyList(), path = stretch)
}

/**
 * Where a vehicle on a trip is now, or should be, when [estimated] rather than reported. [bearing]
 * is in degrees clockwise from north; [seenAt] is when a report placed it there.
 */
data class VehiclePosition(
    val position: LatLon,
    val bearing: Double? = null,
    val estimated: Boolean = false,
    val seenAt: Instant? = null,
) {
    /** The report is too old at [now] to say where the vehicle really is. */
    fun isStaleAt(now: Instant): Boolean = seenAt != null && Duration.between(seenAt, now) > STALE_AFTER

    private companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(2)
    }
}

/** What only Oběhy knows about a trip. */
data class TripInfo(
    val tripId: String,
    val serviceDate: String,
    val cancelled: Boolean,
    val delayMinutes: Long?,
    /** Those a source names as physical vehicles, unlike a train known only by its number. */
    val vehicles: List<TripVehicleInfo>,
    val notes: TripNotes,
    val connections: List<TripConnection>,
    val alerts: List<TripAlert>,
    /** A composition source answers for this trip, so asking is worthwhile. */
    val hasComposition: Boolean,
    /** Where the journey boards, in Oběhy's terms, when that's known. */
    val boardingLocationId: String? = null,
    val boardingSequence: Int? = null,
    /** Where anything on the trip was last seen, a train known only by its number included. */
    val positions: List<VehiclePosition> = emptyList(),
)

/** A physical vehicle on the trip right now, as far as anyone can tell. */
data class TripVehicleInfo(
    val model: String?,
    val operator: String?,
    val fleetNumber: String?,
    val licencePlate: String?,
    val lowFloor: Boolean?,
    val airConditioning: Boolean?,
)

/** A connection the timetable promises. */
data class TripConnection(
    val waitsForIt: Boolean,
    val stopName: String?,
    val waitMinutes: Int?,
    val line: String?,
    val destination: String?,
    val note: String?,
)

/** A disruption or notice. */
@Serializable
data class TripAlert(
    val title: String,
    val text: String?,
    val severe: Boolean,
)

/** The point [share] of the way along this path (of at least two points), by distance. */
internal fun List<LatLon>.along(share: Double): LatLon {
    val lengths = zipWithNext { a, b -> a.metresTo(b) }
    var left = lengths.sum() * share.coerceIn(0.0, 1.0)
    lengths.forEachIndexed { index, length ->
        if (left <= length && length > 0) {
            val a = this[index]
            val b = this[index + 1]
            val part = left / length
            return LatLon(a.lat + (b.lat - a.lat) * part, a.lon + (b.lon - a.lon) * part)
        }
        left -= length
    }
    return last()
}

/** Which way this path heads where it passes closest to [point], in degrees clockwise from north. */
fun List<LatLon>.bearingAt(point: LatLon): Double? {
    val (a, b) =
        zipWithNext()
            .filter { (a, b) -> a != b }
            .minByOrNull { (a, b) -> LatLon((a.lat + b.lat) / 2, (a.lon + b.lon) / 2).metresTo(point) }
            ?: return null
    val north = b.lat - a.lat
    val east = (b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
    return (Math.toDegrees(atan2(east, north)) + FULL_CIRCLE) % FULL_CIRCLE
}

private const val FULL_CIRCLE = 360.0

/** This path from its start to the point nearest [end]. */
internal fun List<LatLon>.upTo(end: LatLon): List<LatLon> {
    if (size < 2) return emptyList()
    return subList(0, indices.minBy { this[it].metresTo(end) } + 1).toList()
}

/** One point along a path nearest to [from], and the path on from there to the point nearest [to]. */
internal fun List<LatLon>.between(
    from: LatLon,
    to: LatLon,
): List<LatLon> {
    if (size < 2) return this
    val start = indices.minBy { this[it].metresTo(from) }
    val end = (start until size).minBy { this[it].metresTo(to) }
    return if (end > start) subList(start, end + 1).toList() else this
}
