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

    /** A trip on a service date, whole, with its calls, notes and what's live on it. */
    suspend fun trip(
        tripId: String,
        serviceDate: String,
    ): ObehyTrip = client.get(tripUrl(tripId, serviceDate)).body()

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
    }
}
