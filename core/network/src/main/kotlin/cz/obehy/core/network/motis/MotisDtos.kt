package cz.obehy.core.network.motis

import kotlinx.serialization.Serializable

// The parts of the MOTIS API (v5 plan, v1 geocode) the app reads. Times are ISO 8601 UTC.

@Serializable
data class MotisMatch(
    val type: String,
    val name: String,
    val id: String,
    val lat: Double,
    val lon: Double,
    val country: String? = null,
)

@Serializable
data class MotisPlan(
    val itineraries: List<MotisItinerary> = emptyList(),
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)

@Serializable
data class MotisItinerary(
    val duration: Long,
    val startTime: String,
    val endTime: String,
    val transfers: Int,
    val legs: List<MotisLeg>,
)

@Serializable
data class MotisLeg(
    val mode: String,
    val from: MotisPlace,
    val to: MotisPlace,
    val duration: Long,
    val startTime: String,
    val endTime: String,
    val scheduledStartTime: String,
    val scheduledEndTime: String,
    val realTime: Boolean = false,
    val cancelled: Boolean = false,
    /** `{service date}_{GTFS time}_{dataset tag}_{trip_id}`: sent to Oběhy verbatim. */
    val tripId: String? = null,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val tripShortName: String? = null,
    val headsign: String? = null,
    val agencyName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val distance: Double? = null,
    val legGeometry: MotisGeometry? = null,
)

@Serializable
data class MotisPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val track: String? = null,
    val scheduledTrack: String? = null,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
)

/** An encoded polyline with [precision] decimal places. */
@Serializable
data class MotisGeometry(
    val points: String,
    val precision: Int,
    val length: Int,
)
