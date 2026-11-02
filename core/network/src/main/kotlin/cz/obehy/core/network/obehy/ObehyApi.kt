package cz.obehy.core.network.obehy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

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
        return client.post("${baseUrl}journeys/enrich") {
            contentType(ContentType.Application.Json)
            setBody(EnrichRequest(legs))
        }.body()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://obehy.cz/api/v1/"

        /** The API's `enrich_legs_max` (src/obehy/data/api/api-v1.toml). */
        const val MAX_LEGS = 60
    }
}
