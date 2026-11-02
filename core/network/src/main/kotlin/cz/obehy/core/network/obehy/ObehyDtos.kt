package cz.obehy.core.network.obehy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The parts of the Oběhy API (/api/v1, snake_case through ObehyJson) the app reads. Times are
// ISO 8601 with the Prague offset. Mirrors src/obehy/api/schema.py in the obehy repository.

@Serializable
data class EnrichLegRequest(
    /** The leg's MOTIS `tripId`, verbatim. */
    val tripId: String,
    val fromStopId: String? = null,
    val toStopId: String? = null,
)

@Serializable
data class EnrichRequest(
    val legs: List<EnrichLegRequest>,
)

@Serializable
data class Enrichment(
    val releaseId: String,
    val generatedAt: String,
    val legs: List<EnrichedLeg>,
)

@Serializable
enum class EnrichOutcome {
    @SerialName("resolved")
    Resolved,

    @SerialName("not_running")
    NotRunning,

    @SerialName("not_found")
    NotFound,

    @SerialName("ambiguous")
    Ambiguous,

    @SerialName("unknown_dataset")
    UnknownDataset,

    @SerialName("malformed")
    Malformed,
}

@Serializable
data class EnrichedLeg(
    val motisTripId: String,
    val outcome: EnrichOutcome,
    val detail: String? = null,
    val trip: TripRef? = null,
    val journey: JourneyKey? = null,
    val line: Line? = null,
    val agency: String? = null,
    val tripShortName: String? = null,
    val headsign: String? = null,
    val realtime: TripRealtime? = null,
    val vehicles: List<TripVehicle> = emptyList(),
    val composition: Boolean = false,
    val calls: List<TripCall> = emptyList(),
    /** Whether [calls] are the leg's span (boarding to alighting), not the whole trip. */
    val legCalls: Boolean = false,
    val alerts: List<Alert> = emptyList(),
)

@Serializable
data class TripRef(
    val tripId: String,
    val serviceDate: String,
)

@Serializable
data class JourneyKey(
    val namespace: String,
    val key: String,
)

@Serializable
data class Line(
    val routeId: String,
    val shortName: String? = null,
    val longName: String? = null,
    val mode: String,
    val color: String? = null,
    val textColor: String? = null,
)

@Serializable
data class TripRealtime(
    /** pre_trip, running, finished or cancelled. */
    val lifecycle: String,
    val delayS: Int? = null,
    val offRoute: Boolean = false,
    val stale: Boolean = false,
    val updatedAt: String,
)

@Serializable
data class VehicleTraits(
    val lowFloor: Boolean? = null,
    val airConditioning: Boolean? = null,
    val wheelchairAccessible: Boolean? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val operator: String? = null,
    val licencePlate: String? = null,
    val fleetNumber: String? = null,
)

@Serializable
data class TripVehicle(
    val vehicleId: String,
    val state: String,
    val lastSeen: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearing: Double? = null,
    val traits: VehicleTraits = VehicleTraits(),
)

@Serializable
data class TripCall(
    val sequence: Int,
    val locationId: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val platform: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val estimatedArrival: String? = null,
    val estimatedDeparture: String? = null,
    /** scheduled, predicted, actual, inferred, no_realtime or cancelled. */
    val status: String,
    val sourceClass: String? = null,
)

@Serializable
data class Alert(
    val alertId: String,
    val severity: String,
    val effect: String,
    /** Per language: `cs`, `en`, … */
    val header: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
)
