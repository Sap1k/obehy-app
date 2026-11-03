package cz.obehy.core.network.motis

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.time.Instant

/**
 * A MOTIS instance: Transitous or motis.obehy.cz. [baseUrl] is read for every request, so a
 * change of provider in the settings applies to the next search.
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
                    if (wheelchair) parameter("pedestrianProfile", "WHEELCHAIR")
                    if (routedTransfers) parameter("useRoutedTransfers", true)
                    transitModes?.let { parameter("transitModes", it.joinToString(",")) }
                    pageCursor?.let { parameter("pageCursor", it) }
                    language?.let { parameter("language", it) }
                }
                details(request.routedTransfers)
            }.body()

    /**
     * [id] (an itinerary's `id`) again, with the latest realtime data. MOTIS calls this
     * experimental, and it sometimes answers with rides stripped of their trip and stops: callers
     * check what comes back before trusting it.
     */
    suspend fun refreshItinerary(
        id: String,
        routedTransfers: Boolean = false,
        language: String? = null,
    ): MotisItinerary =
        client
            .get("${baseUrl()}api/v6/refresh-itinerary") {
                parameter("itineraryId", id)
                language?.let { parameter("language", it) }
                details(routedTransfers)
            }.body()

    /** Every train keeps its own number; rides come with their path for the map, walks only when routed. */
    private fun HttpRequestBuilder.details(routedTransfers: Boolean) {
        parameter("joinInterlinedLegs", false)
        parameter("detailedLegs", true)
        parameter("detailedTransfers", routedTransfers)
    }
}

/**
 * A journey search. Places are MOTIS stop IDs or `lat,lon`; [via] takes stop IDs only (at most
 * two). [time] is the departure, or the arrival with [arriveBy]; null means now. [transitModes]
 * are MOTIS mode names, null for all.
 */
data class MotisPlanRequest(
    val from: String,
    val to: String,
    val via: List<String> = emptyList(),
    val time: Instant? = null,
    val arriveBy: Boolean = false,
    val wheelchair: Boolean = false,
    val routedTransfers: Boolean = false,
    val transitModes: List<String>? = null,
    val pageCursor: String? = null,
    val language: String? = null,
)
