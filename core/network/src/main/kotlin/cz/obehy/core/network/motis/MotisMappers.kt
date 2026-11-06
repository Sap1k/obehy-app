package cz.obehy.core.network.motis

import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.JourneyPage
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.Mode
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.model.Reservation
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.model.StopCall
import cz.obehy.core.model.TimePoint
import cz.obehy.core.model.TripAlert
import cz.obehy.core.model.TripLinks
import java.time.Duration
import java.time.Instant

// MOTIS answers → domain types, and domain searches → MOTIS requests. Nothing MOTIS-shaped
// leaves this file's callers.

fun MotisMatch.toPlace(): Place =
    Place(
        // MOTIS gives addresses an empty id, but a place needs one of its own.
        id = id.ifEmpty { "$lat,$lon" },
        name = name,
        area = areas.lastOrNull()?.name?.takeIf { it != name },
        kind =
            when (type) {
                "STOP" -> PlaceKind.Stop
                "ADDRESS" -> PlaceKind.Address
                else -> PlaceKind.Poi
            },
        lat = lat,
        lon = lon,
        modes = modes.mapTo(mutableSetOf()) { motisMode(it) },
    )

/** This stop as a place to route from, under [routingId] rather than its own platform's ID. */
fun MotisStop.toPlace(
    routingId: String,
    modes: Set<Mode>,
): Place = Place(routingId, name, null, PlaceKind.Stop, lat, lon, modes)

/** The modes MOTIS lists for this stop. */
fun MotisStop.domainModes(): Set<Mode> = modes.mapTo(mutableSetOf()) { motisMode(it) }

/** The MOTIS request for [query] (which must be complete) with [options]. */
fun motisPlanRequest(
    query: JourneyQuery,
    options: SearchOptions,
    language: String?,
    pageCursor: String? = null,
): MotisPlanRequest =
    MotisPlanRequest(
        from = requireNotNull(query.from) { "no start" }.routingRef,
        to = requireNotNull(query.to) { "no destination" }.routingRef,
        via = query.vias.map { it.routingRef },
        time = query.time,
        arriveBy = query.arriveBy,
        routing = options.toRouting(),
        pageCursor = pageCursor,
        language = language,
    )

/** [SearchOptions] as MOTIS takes them. */
fun SearchOptions.toRouting(): MotisRouting =
    MotisRouting(
        wheelchair = lowFloor,
        routedTransfers = routedTransfers,
        transitModes =
            allowedModes
                ?.flatMap { motisNames(it) }
                ?.distinct()
                ?.sorted(),
    )

fun MotisPlan.toJourneyPage(): JourneyPage =
    JourneyPage(
        itineraries = itineraries.map { it.toItinerary() },
        earlierCursor = previousPageCursor,
        laterCursor = nextPageCursor,
    )

internal fun MotisItinerary.toItinerary(): Itinerary =
    Itinerary(
        // Zero-length walks are MOTIS's way of saying "change at the same platform".
        legs = legs.filterNot { it.mode == "WALK" && it.duration == 0L }.map { it.toLeg() }.withWalkDelays(),
        transfers = transfers,
        refreshId = id,
    )

/**
 * MOTIS fits walks around the rides' expected times, but gives them as plain timetable times. A
 * journey that starts or ends on foot would look on time however late its rides are, so those
 * walks take on the delay of the ride they lead to or follow.
 */
private fun List<Leg>.withWalkDelays(): List<Leg> =
    mapIndexed { index, leg ->
        val neighbour =
            when (index) {
                0 -> (getOrNull(1) as? Leg.Transit)?.start
                lastIndex -> (getOrNull(index - 1) as? Leg.Transit)?.end
                else -> null
            }
        if (leg is Leg.Walk && neighbour?.realtime == true) leg.delayedLike(neighbour) else leg
    }

private fun Leg.Walk.delayedLike(ride: TimePoint): Leg.Walk {
    val delay = Duration.between(ride.scheduled, ride.expected)
    return copy(start = start.delayedBy(delay), end = end.delayedBy(delay))
}

private fun TimePoint.delayedBy(delay: Duration) = copy(scheduled = expected - delay, realtime = true)

fun MotisItinerary.toDomain(): Itinerary = toItinerary()

/** The ride of the trip [tripId] in this answer to a trip lookup. */
fun MotisItinerary.toTrip(tripId: String): Leg.Transit? =
    (legs.firstOrNull { it.tripId == tripId } ?: legs.firstOrNull { it.mode != "WALK" })?.toLeg() as? Leg.Transit

internal fun MotisLeg.toLeg(): Leg {
    val start = TimePoint(instant(scheduledStartTime), instant(startTime), realTime)
    val end = TimePoint(instant(scheduledEndTime), instant(endTime), realTime)
    if (mode == "WALK") {
        return Leg.Walk(from.toStopCall(realTime), to.toStopCall(realTime), start, end, distance?.toInt(), path())
    }
    val name = displayName ?: routeShortName ?: tripShortName ?: ""
    return Leg.Transit(
        mode = motisMode(mode),
        from = from.toStopCall(realTime),
        to = to.toStopCall(realTime),
        start = start,
        end = end,
        name = name,
        line = routeShortName?.takeIf { it.isNotBlank() && it != name },
        headsign = headsign?.takeIf { it.isNotBlank() },
        agency = agencyName,
        color = routeColor?.takeIf { it.isNotBlank() },
        textColor = routeTextColor?.takeIf { it.isNotBlank() },
        accessible =
            when (wheelchairAccessible) {
                "ACCESSIBLE" -> true
                "NOT_ACCESSIBLE" -> false
                else -> null
            },
        cancelled = cancelled,
        tripId = tripId,
        intermediateStops = intermediateStops.map { it.toStopCall(realTime) },
        staySeated = interlineWithPreviousLeg,
        path = path(),
        tripShortName = tripShortName?.takeIf { it.isNotBlank() },
        bikesAllowed = bikesAllowed,
        links = TripLinks(agencyFareUrl.webLink(), routeUrl.webLink(), agencyUrl.webLink()),
        reservation =
            when (reservation) {
                null, "NONE" -> null
                "COMPULSORY" -> Reservation.Required
                else -> Reservation.Possible
            },
        alerts = alerts.mapNotNull { it.toAlert() },
    )
}

/** A feed's link, if it's one a browser should be sent to. */
private fun String?.webLink(): String? =
    this?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

/** Whether a time is live: MOTIS says per time, and the leg's flag only answers for servers that don't. */
private fun isLive(
    reason: String?,
    realtime: Boolean,
): Boolean =
    when (reason) {
        null -> realtime
        "NO_RT_DATA", "SCHEDULED" -> false
        else -> true
    }

private fun MotisAlert.toAlert(): TripAlert? =
    headerText?.takeIf { it.isNotBlank() }?.let { title ->
        TripAlert(title, descriptionText?.takeIf { it.isNotBlank() }, severityLevel.equals("SEVERE", ignoreCase = true))
    }

private fun MotisPlace.toStopCall(realtime: Boolean): StopCall =
    StopCall(
        name = name,
        stopId = stopId,
        arrival = timePoint(scheduledArrival, arrival, isLive(arrivalTimestampReason, realtime)),
        departure = timePoint(scheduledDeparture, departure, isLive(departureTimestampReason, realtime)),
        track = track ?: scheduledTrack,
        scheduledTrack = scheduledTrack,
        cancelled = cancelled,
        position = LatLon(lat, lon),
        noBoarding = pickupType == "NOT_ALLOWED",
        noAlighting = dropoffType == "NOT_ALLOWED",
    )

private fun MotisLeg.path(): List<LatLon> =
    legGeometry
        ?.takeIf {
            it.points.isNotEmpty()
        }?.let { decodePolyline(it.points, it.precision) }
        .orEmpty()

/** Decodes an encoded polyline (Google's algorithm) with [precision] decimal places, as MOTIS sends them. */
internal fun decodePolyline(
    encoded: String,
    precision: Int,
): List<LatLon> {
    val factor = Math.pow(10.0, precision.toDouble())
    val points = mutableListOf<LatLon>()
    var index = 0
    var lat = 0L
    var lon = 0L

    fun next(): Long {
        var result = 0L
        var shift = 0
        do {
            val byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f).toLong() shl shift)
            shift += 5
        } while (byte >= 0x20)
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }
    while (index < encoded.length) {
        lat += next()
        lon += next()
        points += LatLon(lat / factor, lon / factor)
    }
    return points
}

private fun timePoint(
    scheduled: String?,
    expected: String?,
    realtime: Boolean,
): TimePoint? {
    val planned = (scheduled ?: expected)?.let(::instant) ?: return null
    return TimePoint(planned, expected?.let(::instant) ?: planned, realtime)
}

private fun instant(iso: String): Instant = Instant.parse(iso)

internal fun motisMode(name: String): Mode =
    when (name) {
        "WALK" -> Mode.Walk
        "SUBWAY", "METRO" -> Mode.Subway
        "TRAM" -> Mode.Tram
        "REGIONAL_RAIL", "REGIONAL_FAST_RAIL" -> Mode.RegionalRail
        "SUBURBAN" -> Mode.SuburbanRail
        "LONG_DISTANCE", "RAIL" -> Mode.LongDistanceRail
        "HIGHSPEED_RAIL" -> Mode.HighSpeedRail
        "NIGHT_RAIL" -> Mode.NightRail
        "BUS" -> Mode.Bus
        "COACH" -> Mode.Coach
        "FERRY" -> Mode.Ferry
        "FUNICULAR" -> Mode.Funicular
        "AERIAL_LIFT", "AREAL_LIFT", "CABLE_CAR" -> Mode.AerialLift
        else -> Mode.Other
    }

internal fun motisNames(mode: Mode): List<String> =
    when (mode) {
        Mode.Walk -> emptyList()
        Mode.Subway -> listOf("SUBWAY")
        Mode.Tram -> listOf("TRAM")
        Mode.RegionalRail -> listOf("REGIONAL_RAIL")
        Mode.SuburbanRail -> listOf("SUBURBAN")
        Mode.LongDistanceRail -> listOf("LONG_DISTANCE")
        Mode.HighSpeedRail -> listOf("HIGHSPEED_RAIL")
        Mode.NightRail -> listOf("NIGHT_RAIL")
        Mode.Bus -> listOf("BUS")
        Mode.Coach -> listOf("COACH")
        Mode.Ferry -> listOf("FERRY")
        Mode.Funicular -> listOf("FUNICULAR")
        Mode.AerialLift -> listOf("AERIAL_LIFT")
        Mode.Other -> listOf("OTHER", "ODM")
    }
