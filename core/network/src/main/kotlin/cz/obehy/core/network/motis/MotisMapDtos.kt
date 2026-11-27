package cz.obehy.core.network.motis

import kotlinx.serialization.Serializable

// MOTIS's map answers (`/api/v1/map/trips`, railviz) and stop times (`/api/v6/stoptimes`), as
// MOTIS 2.11 sends them (recorded from Transitous and motis.obehy.cz, 2026-11-20).

/** One stretch of a trip between two stops, with its path (polyline at the asked precision, 5 by default). */
@Serializable
data class MotisMapTrip(
    val trips: List<MotisMapTripInfo>,
    val routeColor: String? = null,
    val mode: String,
    val distance: Double? = null,
    val from: MotisPlace,
    val to: MotisPlace,
    val departure: String,
    val arrival: String,
    val scheduledDeparture: String,
    val scheduledArrival: String,
    val realTime: Boolean = false,
    val polyline: String,
)

@Serializable
data class MotisMapTripInfo(
    val tripId: String,
    val routeShortName: String? = null,
    val displayName: String? = null,
)

@Serializable
data class MotisStopTimes(
    val stopTimes: List<MotisStopTime> = emptyList(),
    val place: MotisPlace? = null,
    val nextPageCursor: String? = null,
)

/** A vehicle's call at the asked stop: [place] carries the times. */
@Serializable
data class MotisStopTime(
    val place: MotisPlace,
    val mode: String,
    val realTime: Boolean = false,
    val headsign: String? = null,
    val tripId: String,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val tripShortName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
)
