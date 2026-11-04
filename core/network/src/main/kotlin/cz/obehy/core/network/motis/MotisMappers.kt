package cz.obehy.core.network.motis

import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.JourneyPage
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.Mode
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.model.StopCall
import cz.obehy.core.model.TimePoint
import java.time.Instant

// MOTIS answers → domain types, and domain searches → MOTIS requests. Nothing MOTIS-shaped
// leaves this file's callers.

fun MotisMatch.toPlace(): Place =
    Place(
        id = id,
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
        wheelchair = options.lowFloor,
        routedTransfers = options.routedTransfers,
        transitModes =
            options.allowedModes
                ?.flatMap { motisNames(it) }
                ?.distinct()
                ?.sorted(),
        pageCursor = pageCursor,
        language = language,
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
        legs = legs.filterNot { it.mode == "WALK" && it.duration == 0L }.map { it.toLeg() },
        transfers = transfers,
        refreshId = id,
    )

fun MotisItinerary.toDomain(): Itinerary = toItinerary()

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
    )
}

private fun MotisPlace.toStopCall(realtime: Boolean): StopCall =
    StopCall(
        name = name,
        stopId = stopId,
        arrival = timePoint(scheduledArrival, arrival, realtime),
        departure = timePoint(scheduledDeparture, departure, realtime),
        track = track ?: scheduledTrack,
        scheduledTrack = scheduledTrack,
        cancelled = cancelled,
        position = LatLon(lat, lon),
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
