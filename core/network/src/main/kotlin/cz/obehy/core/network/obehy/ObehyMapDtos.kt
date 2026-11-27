package cz.obehy.core.network.obehy

import kotlinx.serialization.Serializable

// The live map's parts of the Oběhy API: vehicles, stops and boards. Mirrors `Vehicle`,
// `VehicleDetail`, `Stop` and `Departure` in src/obehy/api/schema.py (obehy 7eb27e8).

@Serializable
data class ObehyVehicles(
    val generatedAt: String,
    val vehicles: List<ObehyVehicle>,
)

@Serializable
data class ObehyVehicle(
    /** The registry's ID when the source vehicle is bound to one, else `<source>:<source_vehicle_id>`. */
    val vehicleId: String,
    val feed: String,
    val source: String,
    /** running, positioning, layover, unmatched or not_in_service. */
    val state: String,
    val lastSeen: String,
    /** The source time of the position: a client keeps a newer one it already has. */
    val positionAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearing: Double? = null,
    val tripId: String? = null,
    val serviceDate: String? = null,
    val line: Line? = null,
    /** The train number (Os 6819). */
    val tripShortName: String? = null,
    val headsign: String? = null,
    /** How late its journey runs now, in shown minutes. */
    val delayMin: Int? = null,
    /** early, on_time, late, very_late, unknown or cancelled: decided by the API. */
    val punctuality: String = UNKNOWN,
    val traits: VehicleTraits = VehicleTraits(),
    val sources: List<String> = emptyList(),
    /** Metres from `near`, when a search gives it. */
    val distanceM: Int? = null,
)

@Serializable
data class ObehyVehicleDetail(
    val vehicleId: String,
    val vehicle: ObehyVehicle? = null,
    val registry: ObehyRegistryEntry? = null,
    val reports: List<ObehyVehicleReport> = emptyList(),
)

@Serializable
data class ObehyRegistryEntry(
    val vehicleId: String,
    val publicLabel: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
)

@Serializable
data class ObehyVehicleReport(
    val source: String,
    val label: Label? = null,
    val sourceVehicleId: String,
    val physical: Boolean = true,
)

@Serializable
data class ObehyStops(
    val stops: List<ObehyStop>,
)

@Serializable
data class ObehyStop(
    val locationId: String,
    val name: String,
    /** stop_place, boarding_point, … */
    val kind: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** rail, metro, tram, trolleybus, bus, water, cable, funicular. */
    val modes: List<String> = emptyList(),
)

@Serializable
data class ObehyDepartures(
    val generatedAt: String,
    val stop: ObehyStop,
    val departures: List<ObehyDeparture> = emptyList(),
    /** On an arrivals board, its rows instead of [departures]. */
    val arrivals: List<ObehyDeparture> = emptyList(),
    /** Cursor for the rows after these. */
    val next: String? = null,
    val alerts: List<Alert> = emptyList(),
    /** Of [alerts], the IDs of the stop's own. */
    val stopAlerts: List<String> = emptyList(),
)

@Serializable
data class ObehyDeparture(
    val tripId: String,
    val serviceDate: String,
    val line: Line,
    val tripShortName: String? = null,
    val headsign: String? = null,
    val scheduled: String,
    val estimated: String? = null,
    val delayMin: Int? = null,
    val punctuality: String = UNKNOWN,
    /** scheduled, predicted, actual, inferred, no_realtime or cancelled. */
    val status: String,
    val platform: String? = null,
    /** accessible, booking or partly; null: not step-free or not known. */
    val stepFree: String? = null,
)

/** A GeoJSON LineString, `[lon, lat]` pairs. */
@Serializable
data class ObehyLineString(
    val type: String,
    val coordinates: List<List<Double>> = emptyList(),
)

/** A rail run from this trip on: its calls, the later parts' merged in. */
@Serializable
data class ObehyRun(
    val calls: List<ObehyRunCall> = emptyList(),
    /** Index of the first call not passed. */
    val next: Int? = null,
)

@Serializable
data class ObehyRunCall(
    /** The part the call is of. */
    val tripId: String,
    /** On the first call of a later part on another line: the line it goes on as. */
    val continuesAs: Line? = null,
    val call: TripCall,
)

/** A band without a live estimate behind it, or a stale one. */
const val UNKNOWN = "unknown"

/** Whether an API band says late enough to show it ("+4 min", the error colour). */
fun lateBand(punctuality: String): Boolean = punctuality == "late" || punctuality == "very_late"
