package cz.obehy.core.network.motis

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.time.Instant

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
