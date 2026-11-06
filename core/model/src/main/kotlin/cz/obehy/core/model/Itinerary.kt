package cz.obehy.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sqrt

/** A planned time and, once realtime data says otherwise, the expected one. */
@Serializable
data class TimePoint(
    @Serializable(InstantSerializer::class) val scheduled: Instant,
    @Serializable(InstantSerializer::class) val expected: Instant = scheduled,
    val realtime: Boolean = false,
) {
    /** Whole minutes late (negative: early); null without realtime data. */
    val delayMinutes: Long? get() = if (realtime) Duration.between(scheduled, expected).toMinutes() else null
}

/** A point on the map. */
@Serializable
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

/** Where the feed points to for more about a ride. */
@Serializable
data class TripLinks(
    val tickets: String? = null,
    val line: String? = null,
    val operator: String? = null,
) {
    val isEmpty: Boolean get() = tickets == null && line == null && operator == null
}

/** What the feed says about booking a seat; the absence of this says nothing either way. */
enum class Reservation { Possible, Required }

/** A stop on a journey and its times there. */
@Serializable
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
    /** The timetable lets nobody on, or nobody off, here. */
    val noBoarding: Boolean = false,
    val noAlighting: Boolean = false,
) {
    val trackChanged: Boolean get() = track != null && scheduledTrack != null && track != scheduledTrack

    /** This stop as a place to search from; null when it isn't known where it is. */
    fun toPlace(): Place? =
        position?.let {
            Place(
                stopId ?: "${it.lat},${it.lon}",
                name,
                area = null,
                kind = if (stopId != null) PlaceKind.Stop else PlaceKind.Address,
                lat = it.lat,
                lon = it.lon,
            )
        }
}

/** One part of a journey. */
@Serializable
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
    @Serializable
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
    @Serializable
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
        /** The train's own number or the like (`R 600`), apart from the line in [name]. */
        val tripShortName: String? = null,
        /** True or false when the feed says so, null when it doesn't. */
        val bikesAllowed: Boolean? = null,
        val reservation: Reservation? = null,
        val links: TripLinks = TripLinks(),
        val alerts: List<TripAlert> = emptyList(),
    ) : Leg
}

/** One way to make the journey. */
@Serializable
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
    @Transient
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

/** The stretch of a journey that matters right now: [legs] to show, and the [ride] they lead to. */
data class JourneyFocus(
    val legs: List<Leg>,
    val ride: Leg.Transit?,
)

/** How long before a journey sets off it's what you're doing, rather than a plan. */
private val FOCUS_AHEAD: Duration = Duration.ofHours(1)

/**
 * What of these legs matters at [now]: the ride you're on, or the next one with any walk to it,
 * which may be all of them. Null further than an hour ahead, and once it's over.
 */
fun List<Leg>.focusAt(now: Instant): JourneyFocus? {
    if (isEmpty() || now < first().start.expected.minus(FOCUS_AHEAD) || now >= last().end.expected) return null
    val current = indexOfFirst { now < it.end.expected }
    val last = (current until size).firstOrNull { this[it] is Leg.Transit } ?: lastIndex
    return JourneyFocus(subList(current, last + 1).toList(), this[last] as? Leg.Transit)
}

/** Where in a journey you should be by now, going by the clock: nothing says you really are. */
sealed interface JourneyStage {
    data class Walking(
        val walk: Leg.Walk,
    ) : JourneyStage

    data class Waiting(
        val ride: Leg.Transit,
    ) : JourneyStage

    data class Riding(
        val ride: Leg.Transit,
    ) : JourneyStage
}

/** The stage of these legs at [now]; null before they start and once they're over. */
fun List<Leg>.stageAt(now: Instant): JourneyStage? {
    if (isEmpty() || now < first().start.expected || now >= last().end.expected) return null
    return when (val leg = first { now < it.end.expected }) {
        is Leg.Walk -> JourneyStage.Walking(leg)
        is Leg.Transit -> if (now < leg.start.expected) JourneyStage.Waiting(leg) else JourneyStage.Riding(leg)
    }
}

/**
 * The time a row of a timeline stands for, and the stretch of time it covers: [from] and [to] are
 * halfway to its neighbours, so one row hands over to the next whatever their heights.
 */
data class RowSpan(
    val from: Instant,
    val at: Instant,
    val to: Instant,
) {
    /** Whether [now] is within this row. */
    fun holds(now: Instant): Boolean = now >= from && now < to

    /** How far down a row of [height] things have got at [now], with [at] drawn at [mark]. */
    fun reached(
        now: Instant,
        mark: Float,
        height: Float,
    ): Float =
        when {
            now < from -> 0f
            now >= to -> height
            now < at -> mark * share(from, now, at)
            else -> mark + (height - mark) * share(at, now, to)
        }

    private fun share(
        start: Instant,
        now: Instant,
        end: Instant,
    ): Float = Duration.between(start, now).toMillis().toFloat() / Duration.between(start, end).toMillis()
}

/**
 * The [RowSpan]s of a timeline's rows from the time of each, top to bottom. A row without a time
 * goes with the one above it, and a time earlier than the row above (an early vehicle) is held
 * back to it, so the spans always run forwards. Empty when there are no times at all.
 */
fun List<Instant?>.rowSpans(): List<RowSpan> {
    val known = firstNotNullOfOrNull { it } ?: return emptyList()
    val times =
        drop(1).runningFold(first() ?: known) { above, time -> if (time != null && time > above) time else above }
    return times.indices.map { index ->
        val at = times[index]
        RowSpan(
            from = times.getOrNull(index - 1)?.let { midpoint(it, at) } ?: at,
            at = at,
            to = times.getOrNull(index + 1)?.let { midpoint(at, it) } ?: at,
        )
    }
}

private fun midpoint(
    start: Instant,
    end: Instant,
): Instant = start.plus(Duration.between(start, end).dividedBy(2))

/**
 * One [RowSpan] per row of a journey's timeline, top to bottom: the stop you set off from, each
 * walk, each ride with the stops where you board and alight, and the stops passed on the rides
 * at the [unfolded] indices of these legs.
 */
fun List<Leg>.timelineSpans(unfolded: Set<Int> = emptySet()): List<RowSpan> {
    val legs = this
    return flatMapIndexed { index, leg ->
        buildList<Instant?> {
            when (leg) {
                is Leg.Walk -> {
                    if (index == 0) add(leg.start.expected)
                    add(midpoint(leg.start.expected, leg.end.expected))
                    if (index == legs.lastIndex) add(leg.end.expected)
                }

                is Leg.Transit -> {
                    if (!leg.staySeated) add(leg.start.expected)
                    val passed =
                        if (index in unfolded) {
                            leg.intermediateStops.map { (it.departure ?: it.arrival)?.expected }
                        } else {
                            emptyList()
                        }
                    add(midpoint(leg.start.expected, passed.firstNotNullOfOrNull { it } ?: leg.end.expected))
                    addAll(passed)
                    add(leg.end.expected)
                }
            }
        }
    }.rowSpans()
}

/** Why a journey won't work out as it stands. */
sealed interface JourneyTrouble {
    /** The [ride] won't run, or won't call where it's needed. */
    data class Cancelled(
        val ride: Leg.Transit,
    ) : JourneyTrouble

    /** [departing] leaves [shortBy] before you can get to it from [arriving]. */
    data class MissedChange(
        val arriving: Leg.Transit,
        val departing: Leg.Transit,
        val shortBy: Duration,
    ) : JourneyTrouble
}

/** The first thing still ahead at [now] that stops this journey working; null when it all holds. */
fun Itinerary.troubleAt(now: Instant): JourneyTrouble? {
    var arriving: Leg.Transit? = null
    var walking = Duration.ZERO
    for (leg in legs) {
        if (leg is Leg.Walk) {
            walking += leg.duration
        } else if (leg is Leg.Transit) {
            val ahead = now < leg.end.expected
            if (ahead && (leg.cancelled || leg.from.cancelled || leg.to.cancelled)) {
                return JourneyTrouble.Cancelled(leg)
            }
            val from = arriving?.takeUnless { leg.staySeated }
            if (ahead && from != null) {
                val shortBy = Duration.between(leg.start.expected, from.end.expected) + walking
                if (shortBy > Duration.ZERO) return JourneyTrouble.MissedChange(from, leg, shortBy)
            }
            arriving = leg
            walking = Duration.ZERO
        }
    }
    return null
}

/** Where to look for another way from: a [stop] on the journey, and when you'd be there. */
data class ReplanStart(
    val stop: StopCall,
    val at: Instant,
)

/**
 * Where you'd be left standing by [trouble]: the stop you'd get off at, from the time you'd get
 * there, or null when it's still at the start of the journey and that's where to look from.
 */
fun Itinerary.replanStart(
    trouble: JourneyTrouble,
    now: Instant,
): ReplanStart? {
    val (stop, arrival) =
        when (trouble) {
            is JourneyTrouble.MissedChange -> {
                trouble.arriving.to to trouble.arriving.end.expected
            }

            is JourneyTrouble.Cancelled -> {
                val before = rides.getOrNull(rides.indexOf(trouble.ride) - 1) ?: return null
                trouble.ride.from to before.end.expected
            }
        }
    return ReplanStart(stop, maxOf(now, arrival))
}

/** A page of results, with the cursors for earlier and later ones. */
data class JourneyPage(
    val itineraries: List<Itinerary>,
    val earlierCursor: String?,
    val laterCursor: String?,
)
