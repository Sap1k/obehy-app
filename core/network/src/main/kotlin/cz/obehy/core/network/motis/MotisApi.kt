package cz.obehy.core.network.motis

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A MOTIS instance: Transitous, motis.obehy.cz or the user's own. [baseUrl] is read for every
 * request, so a change in the settings applies to the next search.
 */
class MotisApi(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
) {
    /**
     * Stops, addresses and places matching [text], for the place search. [stopsOnly] for vias,
     * which MOTIS accepts only as stops; [bias] (`lat,lon`) ranks nearby matches first.
     */
    suspend fun geocode(
        text: String,
        language: String? = null,
        stopsOnly: Boolean = false,
        bias: String? = null,
    ): List<MotisMatch> =
        client
            .get("${baseUrl()}api/v1/geocode") {
                parameter("text", text)
                language?.let { parameter("language", it) }
                if (stopsOnly) parameter("type", "STOP")
                bias?.let { parameter("placeBias", it) }
            }.body()

    /** Every stop and platform in the box from [min] to [max], both `lat,lon`. */
    suspend fun mapStops(
        min: String,
        max: String,
    ): List<MotisStop> =
        client
            .get("${baseUrl()}api/v1/map/stops") {
                parameter("min", min)
                parameter("max", max)
            }.body()

    /**
     * Every trip stretch in the box from [min] to [max] (both `lat,lon`) under way between [start]
     * and [end] (railviz). MOTIS picks what [zoom] shows: long-distance only when zoomed out. Servers
     * without railviz answer an error.
     */
    suspend fun mapTrips(
        min: String,
        max: String,
        zoom: Int,
        start: Instant,
        end: Instant,
    ): List<MotisMapTrip> =
        client
            .get("${baseUrl()}api/v1/map/trips") {
                parameter("min", min)
                parameter("max", max)
                parameter("zoom", zoom)
                // Whole seconds: given a fraction, MOTIS answers with no trips at all.
                parameter("startTime", start.truncatedTo(ChronoUnit.SECONDS).toString())
                parameter("endTime", end.truncatedTo(ChronoUnit.SECONDS).toString())
                parameter("precision", RAILVIZ_PRECISION)
            }.body()

    /**
     * The next [n] departures from [stopId] (a stop or a station), or with [arriveBy] arrivals at
     * it, with realtime; from [pageCursor] on (a previous answer's `nextPageCursor`).
     */
    suspend fun stopTimes(
        stopId: String,
        n: Int,
        language: String? = null,
        arriveBy: Boolean = false,
        pageCursor: String? = null,
    ): MotisStopTimes =
        client
            .get("${baseUrl()}api/v6/stoptimes") {
                parameter("stopId", stopId)
                parameter("n", n)
                if (arriveBy) parameter("arriveBy", true)
                pageCursor?.let { parameter("pageCursor", it) }
                language?.let { parameter("language", it) }
            }.body()

    /** Where the server's coverage is, and what it is. */
    suspend fun initial(): MotisInitial = client.get("${baseUrl()}api/v1/map/initial").body()

    /** Itineraries for [request]. */
    suspend fun plan(request: MotisPlanRequest): MotisPlan =
        client
            .get("${baseUrl()}api/v6/plan") {
                with(request) {
                    parameter("fromPlace", from)
                    parameter("toPlace", to)
                    if (via.isNotEmpty()) parameter("via", via.joinToString(","))
                    time?.let { parameter("time", it.toString()) }
                    if (arriveBy) parameter("arriveBy", true)
                    pageCursor?.let { parameter("pageCursor", it) }
                    language?.let { parameter("language", it) }
                }
                routing(request.routing)
            }.body()

    /**
     * [id] (an itinerary's `id`) again, with the latest realtime data. MOTIS calls this
     * experimental, and it sometimes answers with rides stripped of their trip and stops: callers
     * check what comes back before trusting it. [routing] has to be what the journey was found
     * with: MOTIS doesn't remember it, and rebuilds routed walks as estimates or without a path.
     */
    suspend fun refreshItinerary(
        id: String,
        routing: MotisRouting = MotisRouting(),
        language: String? = null,
    ): MotisItinerary =
        client
            .get("${baseUrl()}api/v6/refresh-itinerary") {
                parameter("itineraryId", id)
                language?.let { parameter("language", it) }
                routing(routing)
            }.body()

    /**
     * The trip [tripId], whole. A vehicle going on as another line comes back as a leg for each of
     * its lines, so pick the one asked for.
     */
    suspend fun trip(
        tripId: String,
        language: String? = null,
    ): MotisItinerary =
        client
            .get("${baseUrl()}api/v6/trip") {
                parameter("tripId", tripId)
                parameter("joinInterlinedLegs", false)
                language?.let { parameter("language", it) }
            }.body()

    /** Every train keeps its own number; rides come with their path for the map, walks only when routed. */
    private fun HttpRequestBuilder.routing(routing: MotisRouting) {
        with(routing) {
            if (wheelchair) parameter("pedestrianProfile", "WHEELCHAIR")
            if (routedTransfers) parameter("useRoutedTransfers", true)
            transitModes?.let { parameter("transitModes", it.joinToString(",")) }
            parameter("detailedTransfers", routedTransfers)
        }
        parameter("joinInterlinedLegs", false)
        parameter("detailedLegs", true)
    }
}

/**
 * A journey search. Places are MOTIS stop IDs or `lat,lon`; [via] takes stop IDs only (at most
 * two). [time] is the departure, or the arrival with [arriveBy]; null means now.
 */
data class MotisPlanRequest(
    val from: String,
    val to: String,
    val via: List<String> = emptyList(),
    val time: Instant? = null,
    val arriveBy: Boolean = false,
    val routing: MotisRouting = MotisRouting(),
    val pageCursor: String? = null,
    val language: String? = null,
)

/** How a journey is routed, for a search and for looking it up again. [transitModes] are MOTIS mode names, null for all. */
data class MotisRouting(
    val wheelchair: Boolean = false,
    val routedTransfers: Boolean = false,
    val transitModes: List<String>? = null,
)
