package cz.obehy.core.network.obehy

import cz.obehy.core.model.CallFlag
import cz.obehy.core.model.CoachFeature
import cz.obehy.core.model.Composition
import cz.obehy.core.model.CompositionCall
import cz.obehy.core.model.CompositionSegment
import cz.obehy.core.model.CompositionStatus
import cz.obehy.core.model.CompositionUnit
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.TripAlert
import cz.obehy.core.model.TripConnection
import cz.obehy.core.model.TripFeature
import cz.obehy.core.model.TripInfo
import cz.obehy.core.model.TripStop
import cz.obehy.core.model.TripVehicleInfo
import cz.obehy.core.model.UnitKind
import cz.obehy.core.model.VehiclePosition
import cz.obehy.core.model.tripNotes
import java.time.OffsetDateTime

// Oběhy answers → domain types. Nothing Oběhy-shaped leaves this file's callers.

/**
 * [stops] with what only Oběhy knows about each: its place in Oběhy's timetable, for the train's
 * composition, and the request stops MOTIS can't say. Stops are matched in order by name, so ones
 * either side leaves out don't shift the rest.
 */
fun ObehyTrip.addedTo(stops: List<TripStop>): List<TripStop> {
    val passenger = calls.filterNot { "not_stopping" in it.flags }
    var next = 0
    return stops.map { stop ->
        val at = (next..passenger.lastIndex).firstOrNull { passenger[it].name == stop.call.name }
        if (at == null) {
            stop
        } else {
            next = at + 1
            val call = passenger[at]
            val request = if ("request_stop" in call.flags) setOf(CallFlag.RequestStop) else emptySet()
            stop.copy(sequence = call.sequence, flags = stop.flags + request)
        }
    }
}

/** This trip's details in [language], for a journey boarding at [boarding] (a call of the trip). */
fun ObehyTrip.toTripInfo(
    language: String,
    boarding: TripCall? = null,
): TripInfo =
    TripInfo(
        tripId = tripId,
        serviceDate = serviceDate,
        cancelled = realtime?.lifecycle == "cancelled",
        delayMinutes = realtime?.takeUnless { it.stale }?.delayS?.let { it / SECONDS_PER_MINUTE },
        vehicles = vehicles.filter { it.physical }.map { it.traits.toVehicleInfo() },
        notes = tripNotes(features.map { it.toFeature(language) }),
        connections = connections.map { connection -> connection.toConnection(callName(connection.atSequence)) },
        alerts = alerts.map { it.toAlert(language) },
        hasComposition = composition,
        boardingLocationId = boarding?.locationId,
        boardingSequence = boarding?.sequence,
        positions =
            vehicles.mapNotNull { vehicle ->
                val lat = vehicle.latitude
                val lon = vehicle.longitude
                if (lat != null && lon != null) {
                    val seenAt = OffsetDateTime.parse(vehicle.positionAt ?: vehicle.lastSeen).toInstant()
                    VehiclePosition(LatLon(lat, lon), vehicle.bearing, seenAt = seenAt)
                } else {
                    null
                }
            },
    )

private const val SECONDS_PER_MINUTE = 60L

private fun ObehyTrip.callName(sequence: Int?) = calls.firstOrNull { it.sequence == sequence }?.name

private fun VehicleTraits.toVehicleInfo() =
    TripVehicleInfo(
        model = listOfNotNull(manufacturer, model).joinToString(" ").ifEmpty { null },
        operator = operator,
        fleetNumber = fleetNumber,
        licencePlate = licencePlate,
        lowFloor = lowFloor,
        airConditioning = airConditioning,
    )

private fun Feature.toFeature(language: String) =
    TripFeature(
        kinds = kinds.toSet(),
        icon = icon,
        text = label.of(language),
        scope = scope,
        system = sourceCode?.system,
        code = sourceCode?.code,
        noteKind = note?.kind,
        noteCode = note?.code,
        noteText = note?.text ?: note?.label,
    )

private fun Label.of(language: String) = if (language == "cs") cs else en

private fun Connection.toConnection(stopName: String?) =
    TripConnection(
        waitsForIt = direction == "waits_for",
        stopName = stopName,
        waitMinutes = waitMinutes,
        line = line,
        destination = destination,
        note = note,
    )

private fun Alert.toAlert(language: String) =
    TripAlert(
        title = header.of(language).orEmpty(),
        text = description.of(language),
        severe = severity.equals("severe", ignoreCase = true),
    )

/** The text in [language], else in Czech, else whatever there is. */
private fun Map<String, String>.of(language: String): String? = this[language] ?: this["cs"] ?: values.firstOrNull()

/** This composition, with its wording in [language]. */
fun ObehyComposition.toDomain(language: String): Composition =
    Composition(
        status =
            when (status) {
                "fresh" -> CompositionStatus.Fresh
                "stale" -> CompositionStatus.Stale
                "pending" -> CompositionStatus.Pending
                else -> CompositionStatus.NotAvailable
            },
        retryAfterSeconds = retryAfterS,
        units = units.map { it.toUnit(language) },
        segments = segments.map { CompositionSegment(it.fromCall.toCall(), it.toCall.toCall(), it.units) },
        sources = units.map { it.label?.of(language) ?: it.source }.distinct(),
    )

private fun ObehyCompositionCall.toCall() = CompositionCall(sequence, name)

private fun ObehyCompositionUnit.toUnit(language: String) =
    CompositionUnit(
        kind =
            when (kind) {
                "locomotive" -> UnitKind.Locomotive
                "unit" -> UnitKind.MultipleUnit
                else -> UnitKind.Coach
            },
        vehicleType = vehicleType,
        coachNumber = coachNumber,
        classes = classes,
        features = features.map { it.toCoachFeature(language) },
        destination = destination,
        fromCall = fromCall.toCall(),
        toCall = toCall.toCall(),
        description = details["label"],
        notes = details["notes"],
        leavesAt = details["direct_coach_leaves_at"],
        continuesAs = details["direct_coach_train"],
        cab = details["cab"],
    )

private fun ObehyUnitFeature.toCoachFeature(language: String) =
    CoachFeature(
        id = feature,
        text = label?.of(language) ?: text ?: feature.replace(RAW_CODE, ""),
        icon = icon,
    )

private val RAW_CODE = Regex("^raw:[^:]+:")
