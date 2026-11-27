package cz.obehy.core.network.obehy

import cz.obehy.core.model.BoardSide
import cz.obehy.core.model.CallFlag
import cz.obehy.core.model.HitKind
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.LegEnrichment
import cz.obehy.core.model.LiveStop
import cz.obehy.core.model.LiveVehicle
import cz.obehy.core.model.Mode
import cz.obehy.core.model.StopBoard
import cz.obehy.core.model.StopCall
import cz.obehy.core.model.StopDeparture
import cz.obehy.core.model.TimePoint
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripRef
import cz.obehy.core.model.TripStop
import cz.obehy.core.model.VehicleHit
import cz.obehy.core.model.VehiclePosition
import java.text.Normalizer
import java.time.Instant
import java.time.OffsetDateTime

// Oběhy's vehicles, stops, boards and whole trips → what a live map shows. Delay bands are the
// API's own: nothing here judges lateness.

/** An Oběhy mode (`rail`, `metro`, `tram`, `trolleybus`, `bus`, `water`, `cable`, `funicular`). Oběhy doesn't split rail. */
fun obehyMode(name: String?): Mode =
    when (name) {
        "rail" -> Mode.RegionalRail
        "metro" -> Mode.Subway
        "tram" -> Mode.Tram
        "trolleybus", "bus" -> Mode.Bus
        "water" -> Mode.Ferry
        "cable" -> Mode.AerialLift
        "funicular" -> Mode.Funicular
        else -> Mode.Other
    }

/** This vehicle on a map; null without a position. */
fun ObehyVehicle.toLiveVehicle(): LiveVehicle? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return LiveVehicle(
        id = vehicleId,
        at = VehiclePosition(LatLon(lat, lon), bearing, seenAt = instant(positionAt ?: lastSeen)),
        mode = obehyMode(line?.mode),
        line = line?.shortName ?: tripShortName,
        color = line?.color,
        textColor = line?.textColor,
        headsign = headsign,
        delayMinutes = delayMin?.toLong()?.takeIf { punctuality != UNKNOWN },
        late = lateBand(punctuality),
        trip = trip(tripId, serviceDate),
        tripShortName = tripShortName,
    )
}

/** When the position was fixed (the report's time when there is none). */
fun ObehyVehicle.fixedAt(): Instant = instant(positionAt ?: lastSeen)

/** A search's answer for [query], in the API's order, each said what it matched. */
fun ObehyVehicles.toHits(query: String): List<VehicleHit> {
    val asked = query.folded()
    return vehicles.mapNotNull { found ->
        val vehicle = found.toLiveVehicle() ?: return@mapNotNull null
        val numbers = listOfNotNull(found.traits.fleetNumber, found.traits.licencePlate)
        val kind =
            when {
                found.tripShortName?.folded()?.let {
                    it == asked ||
                        it.endsWith(
                            " $asked",
                        )
                } == true -> HitKind.TrainNumber

                found.line?.shortName?.folded() == asked -> HitKind.Line

                numbers.any { it.folded() == asked } -> HitKind.VehicleNumber

                found.line
                    ?.shortName
                    ?.folded()
                    ?.startsWith(asked) == true -> HitKind.Line

                else -> HitKind.Other
            }
        VehicleHit(
            vehicle,
            found.distanceM,
            kind,
            number = numbers.firstOrNull { it.folded() == asked } ?: found.traits.fleetNumber,
        )
    }
}

/** Without case or diacritics, as the API compares. */
private fun String.folded(): String =
    Normalizer
        .normalize(lowercase().trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/** These stops on a map; those without coordinates are left out. */
fun ObehyStops.toLiveStops(): List<LiveStop> =
    stops.mapNotNull { stop ->
        val lat = stop.latitude ?: return@mapNotNull null
        val lon = stop.longitude ?: return@mapNotNull null
        LiveStop(stop.locationId, stop.name, LatLon(lat, lon), stop.modes.mapTo(mutableSetOf(), ::obehyMode))
    }

/** This board ([side]: what it was asked for) for a stop sheet, its notices in [language]. */
fun ObehyDepartures.toBoard(
    language: String,
    side: BoardSide = BoardSide.Departures,
): StopBoard {
    val lat = stop.latitude
    val lon = stop.longitude
    return StopBoard(
        stop =
            LiveStop(
                stop.locationId,
                stop.name,
                if (lat != null && lon != null) LatLon(lat, lon) else LatLon(0.0, 0.0),
                stop.modes.mapTo(mutableSetOf(), ::obehyMode),
            ),
        departures =
            (if (side == BoardSide.Arrivals) arrivals else departures).map { row ->
                val scheduled = instant(row.scheduled)
                val live = row.punctuality != UNKNOWN && row.punctuality != CANCELLED
                StopDeparture(
                    trip = TripRef.Obehy(row.tripId, row.serviceDate),
                    mode = obehyMode(row.line.mode),
                    line = row.line.shortName ?: row.tripShortName,
                    color = row.line.color,
                    textColor = row.line.textColor,
                    headsign = row.headsign,
                    time = TimePoint(scheduled, row.estimated?.let(::instant) ?: scheduled, realtime = live),
                    late = lateBand(row.punctuality),
                    cancelled = row.punctuality == CANCELLED || row.status == CANCELLED,
                    platform = row.platform,
                    // A train's number; other trips' are only the timetable's internal ones.
                    number = row.tripShortName?.takeIf { row.line.mode == "rail" && it != row.line.shortName },
                    stepFree = row.stepFree == STEP_FREE,
                )
            },
        at = instant(generatedAt),
        side = side,
        next = next,
        notices = alerts.filter { it.alertId in stopAlerts }.map { it.toAlert(language) },
    )
}

/**
 * This trip as a whole, from Oběhy alone (a vehicle or a board row opened on the map): the run's
 * calls when asked for (`include=run`), its path from the shape or else straight between its
 * calls, and what's live. [language] picks the wording of notes and alerts.
 */
fun ObehyTrip.toTripDetail(language: String): TripDetail {
    val runCalls = run?.calls?.map { it.call }?.takeIf { it.isNotEmpty() } ?: calls
    val stops =
        runCalls.map { call ->
            TripStop(
                call.toStopCall(),
                sequence = call.sequence,
                flags =
                    buildSet {
                        if ("request_stop" in call.flags) add(CallFlag.RequestStop)
                        if ("exit_only" in call.flags) add(CallFlag.ExitOnly)
                        if ("boarding_only" in call.flags) add(CallFlag.BoardingOnly)
                    },
                passed = call.passed,
            )
        }
    val path =
        shape
            ?.coordinates
            ?.mapNotNull { pair -> if (pair.size >= 2) LatLon(pair[1], pair[0]) else null }
            ?.takeIf { it.size >= 2 }
            ?: stops.mapNotNull { it.call.position }
    val first = stops.first().call
    val last = stops.last().call
    val start = requireNotNull(first.departure ?: first.arrival) { "a trip without a first time" }
    val end = requireNotNull(last.arrival ?: last.departure) { "a trip without a last time" }
    val name = listOfNotNull(line?.shortName, tripShortName?.takeIf { it != line?.shortName }).joinToString(" ")
    val ride =
        Leg.Transit(
            mode = obehyMode(line?.mode),
            from = first,
            to = last,
            start = start,
            end = end,
            name = name.ifEmpty { headsign.orEmpty() },
            line = line?.shortName ?: tripShortName,
            headsign = headsign,
            agency = null,
            color = line?.color,
            textColor = line?.textColor,
            accessible = null,
            cancelled = realtime?.lifecycle == CANCELLED,
            tripId = null,
            intermediateStops = stops.drop(1).dropLast(1).map { it.call },
            path = path,
            enrichment = LegEnrichment.Resolved(tripId, serviceDate),
            tripShortName = tripShortName,
        )
    return TripDetail(ride, stops, boarded = null, info = toTripInfo(language))
}

private fun TripCall.toStopCall(): StopCall {
    val live = punctuality != UNKNOWN && punctuality != CANCELLED
    return StopCall(
        name = name,
        stopId = locationId,
        arrival = point(scheduledArrival, estimatedArrival, live),
        departure = point(scheduledDeparture, estimatedDeparture, live),
        track = platform,
        scheduledTrack = platform,
        cancelled = status == CANCELLED || punctuality == CANCELLED,
        position = if (latitude != null && longitude != null) LatLon(latitude, longitude) else null,
    )
}

private fun point(
    scheduled: String?,
    estimated: String?,
    live: Boolean,
): TimePoint? {
    val planned = (scheduled ?: estimated)?.let(::instant) ?: return null
    return TimePoint(planned, estimated?.let(::instant) ?: planned, realtime = live && estimated != null)
}

private fun trip(
    tripId: String?,
    serviceDate: String?,
): TripRef? = if (tripId != null && serviceDate != null) TripRef.Obehy(tripId, serviceDate) else null

private fun instant(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

private const val CANCELLED = "cancelled"

/** A board row one boards in a wheelchair without asking. */
private const val STEP_FREE = "accessible"
