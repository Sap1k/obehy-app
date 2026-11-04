package cz.obehy.core.model

import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sqrt

/** A planned time and, once realtime data says otherwise, the expected one. */
data class TimePoint(
    val scheduled: Instant,
    val expected: Instant = scheduled,
    val realtime: Boolean = false,
) {
    /** Whole minutes late (negative: early); null without realtime data. */
    val delayMinutes: Long? get() = if (realtime) Duration.between(scheduled, expected).toMinutes() else null
}

/** A point on the map. */
data class LatLon(
    val lat: Double,
    val lon: Double,
) {
    /** The straight-line distance to [other]; accurate enough at the scale of a town. */
    fun metresTo(other: LatLon): Double {
        val dLat = (lat - other.lat) * METRES_PER_DEGREE
        val dLon = (lon - other.lon) * METRES_PER_DEGREE * cos(Math.toRadians((lat + other.lat) / 2))
        return sqrt(dLat * dLat + dLon * dLon)
    }

    /** About 100 m coarser: all a list of stops or a ranking needs to send to a server. */
    fun rounded(): LatLon = LatLon(round(lat * 1_000) / 1_000, round(lon * 1_000) / 1_000)

    companion object {
        const val METRES_PER_DEGREE = 111_000.0
    }
}

/** A stop on a journey and its times there. */
data class StopCall(
    val name: String,
    val stopId: String?,
    val arrival: TimePoint?,
    val departure: TimePoint?,
    /** The platform or stand, as expected now; [scheduledTrack] when it was planned elsewhere. */
    val track: String?,
    val scheduledTrack: String?,
    val cancelled: Boolean = false,
    val position: LatLon? = null,
) {
    val trackChanged: Boolean get() = track != null && scheduledTrack != null && track != scheduledTrack
}

/** One part of a journey. */
sealed interface Leg {
    val from: StopCall
    val to: StopCall
    val start: TimePoint
    val end: TimePoint
    val mode: Mode

    /** The way the leg goes on the map; empty when the routing engine gave none. */
    val path: List<LatLon>

    val duration: Duration get() = Duration.between(start.expected, end.expected)

    /** A walk: to the first stop, between two, or from the last. */
    data class Walk(
        override val from: StopCall,
        override val to: StopCall,
        override val start: TimePoint,
        override val end: TimePoint,
        val distanceMeters: Int?,
        override val path: List<LatLon> = emptyList(),
    ) : Leg {
        override val mode: Mode get() = Mode.Walk
    }

    /** A ride on one trip. */
    data class Transit(
        override val mode: Mode,
        override val from: StopCall,
        override val to: StopCall,
        override val start: TimePoint,
        override val end: TimePoint,
        /** The full name as the operator shows it: `S49 (Os 25647)`. */
        val name: String,
        /** The line alone (`S49`), for where the full name does not fit. */
        val line: String?,
        val headsign: String?,
        val agency: String?,
        /** `RRGGBB` as the feed gives it: route colours are data, never restyled. */
        val color: String?,
        val textColor: String?,
        /** True or false when the feed says so, null when it doesn't. */
        val accessible: Boolean?,
        val cancelled: Boolean,
        /** The routing engine's trip ID, sent to Oběhy for enrichment. */
        val tripId: String?,
        val intermediateStops: List<StopCall> = emptyList(),
        /** The same vehicle goes on from the previous ride under another line or number: no change. */
        val staySeated: Boolean = false,
        override val path: List<LatLon> = emptyList(),
        val enrichment: LegEnrichment = LegEnrichment.Pending,
    ) : Leg
}

/** One way to make the journey. */
data class Itinerary(
    val legs: List<Leg>,
    val transfers: Int,
    /** What the routing engine needs to look this journey up again with fresh times. */
    val refreshId: String? = null,
) {
    /**
     * Tells this journey apart from the others, and finds it again in a later search: the trips it
     * rides, which stay the same when their times change.
     */
    val key: String =
        legs.filterIsInstance<Leg.Transit>().let { rides ->
            rides.mapNotNull { it.tripId }.joinToString("|").ifEmpty {
                "${legs.firstOrNull()?.start?.scheduled}-${rides.joinToString { it.name }}"
            }
        }

    /**
     * Whether [fresh] is this journey with newer times: the same trips, each ride still knowing
     * its trip. A refresh that answers otherwise (rides stripped of their trip, stops and times)
     * isn't the same journey and mustn't replace it.
     */
    fun isRefreshedBy(fresh: Itinerary): Boolean =
        fresh.rides.size == rides.size &&
            fresh.rides.all { it.tripId != null } &&
            fresh.rides.map { it.tripId } == rides.map { it.tripId }

    val start: TimePoint get() = legs.first().start
    val end: TimePoint get() = legs.last().end
    val duration: Duration get() = Duration.between(start.expected, end.expected)
    val rides: List<Leg.Transit> get() = legs.filterIsInstance<Leg.Transit>()

    /** True when every ride is low-floor, false when one isn't, null when a feed doesn't say. */
    val accessible: Boolean?
        get() =
            when {
                rides.any { it.accessible == false } -> false
                rides.isNotEmpty() && rides.all { it.accessible == true } -> true
                else -> null
            }

    /** The largest delay on the way, for the result list; null without realtime data. */
    val worstDelayMinutes: Long?
        get() =
            rides
                .flatMap { listOfNotNull(it.start.delayMinutes, it.end.delayMinutes) }
                .maxOrNull()
}

/** A page of results, with the cursors for earlier and later ones. */
data class JourneyPage(
    val itineraries: List<Itinerary>,
    val earlierCursor: String?,
    val laterCursor: String?,
)
