package cz.obehy.core.network.obehy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

/** The Oběhy API (`/api/v1`). */
class ObehyApi(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    /**
     * What Oběhy knows of the trips of MOTIS legs, one answer per leg in the order asked. At
     * most [MAX_LEGS] legs per call.
     */
    suspend fun enrich(legs: List<EnrichLegRequest>): Enrichment {
        require(legs.size in 1..MAX_LEGS) { "1 to $MAX_LEGS legs, not ${legs.size}" }
        return client
            .post("${baseUrl}journeys/enrich") {
                contentType(ContentType.Application.Json)
                setBody(EnrichRequest(legs))
            }.body()
    }

    /**
     * A trip on a service date, whole, with its calls, notes and what's live on it. [include] asks
     * for more: `shape` (its path) and `run` (a rail run's later parts merged in).
     */
    suspend fun trip(
        tripId: String,
        serviceDate: String,
        include: List<String> = emptyList(),
    ): ObehyTrip =
        client
            .get(tripUrl(tripId, serviceDate)) {
                if (include.isNotEmpty()) parameter("include", include.joinToString(","))
            }.body()

    /** The vehicles in [bbox] (`minlon,minlat,maxlon,maxlat`, at most [BBOX_MAX_DEG2] square degrees). */
    suspend fun vehicles(bbox: String): ObehyVehicles =
        client.get("${baseUrl}vehicles") { parameter("bbox", bbox) }.body()

    /**
     * Vehicles found by line, train number, headsign, fleet number or plate: an exact line or train
     * number first, then the number or plate, a line it starts, any match; nearest [near] (`lat,lon`) first in each.
     */
    suspend fun searchVehicles(
        query: String,
        near: String? = null,
        limit: Int = SEARCH_LIMIT,
    ): ObehyVehicles =
        client
            .get("${baseUrl}vehicles") {
                parameter("q", query)
                near?.let { parameter("near", it) }
                parameter("limit", limit)
            }.body()

    /** One vehicle: its state, registry entry and the source reports that are it. */
    suspend fun vehicle(vehicleId: String): ObehyVehicleDetail =
        client.get("${baseUrl}vehicles/${vehicleId.encodeURLPathPart()}").body()

    /** The stops in [bbox] (at most [STOPS_BBOX_MAX_DEG2] square degrees). */
    suspend fun stops(bbox: String): ObehyStops = client.get("${baseUrl}stops") { parameter("bbox", bbox) }.body()

    /** A stop place's next [limit] departures, or with [arrivals] its arrivals; from [cursor] on (a board's `next`). */
    suspend fun departures(
        locationId: String,
        limit: Int,
        arrivals: Boolean = false,
        cursor: String? = null,
    ): ObehyDepartures =
        client
            .get(
                "${baseUrl}stop-places/${locationId.encodeURLPathPart()}/" + if (arrivals) "arrivals" else "departures",
            ) {
                parameter("limit", limit)
                cursor?.let { parameter("cursor", it) }
            }.body()

    /**
     * A train's composition from the stop [from] (default: its next stop). The API asks its source
     * lazily, so the first answer is usually `pending`, with when to ask again.
     */
    suspend fun composition(
        tripId: String,
        serviceDate: String,
        from: String? = null,
    ): ObehyComposition =
        client
            .get("${tripUrl(tripId, serviceDate)}/composition") {
                from?.let { parameter("from", it) }
            }.body()

    /** A trip ID's `%` is part of the ID: it goes out encoded again, or the ID names another trip. */
    private fun tripUrl(
        tripId: String,
        serviceDate: String,
    ) = "${baseUrl}trips/${tripId.encodeURLPathPart()}/$serviceDate"

    companion object {
        const val DEFAULT_BASE_URL = "https://obehy.cz/api/v1/"

        /** The API's `enrich_legs_max` (src/obehy/data/api/api-v1.toml). */
        const val MAX_LEGS = 60

        /** The API's `vehicles.bbox_max_deg2`. */
        const val BBOX_MAX_DEG2 = 4.0

        /** The API's `vehicles.limit`: a box answering this many may be missing some. */
        const val VEHICLES_LIMIT = 3000

        /** The API's `vehicles.search_limit`. */
        const val SEARCH_LIMIT = 20

        /** The API's `stops.bbox_max_deg2`. */
        const val STOPS_BBOX_MAX_DEG2 = 0.5
    }
}
