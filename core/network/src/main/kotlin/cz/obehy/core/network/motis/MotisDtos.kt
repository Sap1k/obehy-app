package cz.obehy.core.network.motis

import kotlinx.serialization.Serializable

// The parts of the MOTIS API (v6 plan, v1 geocode and map) the app reads. Times are ISO 8601 UTC.

@Serializable
data class MotisMatch(
    /** `STOP`, `ADDRESS` or `PLACE`. */
    val type: String,
    val name: String,
    val id: String,
    val lat: Double,
    val lon: Double,
    val country: String? = null,
    val category: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    /** From the country down to the district; the last is the most specific. */
    val areas: List<MotisArea> = emptyList(),
    val modes: List<String> = emptyList(),
)

/** A stop or platform on the map. Platforms often, but not always, name their station as [parentId]. */
@Serializable
data class MotisStop(
    val name: String,
    val stopId: String? = null,
    val parentId: String? = null,
    val lat: Double,
    val lon: Double,
    val modes: List<String> = emptyList(),
)

@Serializable
data class MotisArea(
    val name: String,
    val adminLevel: Double? = null,
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
    /** Looks the itinerary up again with fresh times (`refresh-itinerary`). */
    val id: String? = null,
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
    val routeType: Int? = null,
    val distance: Double? = null,
    /** `ACCESSIBLE`, `NOT_ACCESSIBLE` or `UNKNOWN`. */
    val wheelchairAccessible: String? = null,
    val interlineWithPreviousLeg: Boolean = false,
    val intermediateStops: List<MotisPlace> = emptyList(),
    val legGeometry: MotisGeometry? = null,
)

@Serializable
data class MotisPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val tz: String? = null,
    val track: String? = null,
    val scheduledTrack: String? = null,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val cancelled: Boolean = false,
)

/** An encoded polyline with [precision] decimal places. */
@Serializable
data class MotisGeometry(
    val points: String,
    val precision: Int,
    val length: Int,
)
