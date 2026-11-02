package cz.obehy.core.network.motis

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
    /** Stops and addresses matching [text], for the place search. */
    suspend fun geocode(text: String, language: String? = null): List<MotisMatch> =
        client.get("${baseUrl()}api/v1/geocode") {
            parameter("text", text)
            language?.let { parameter("language", it) }
        }.body()

    /**
     * Itineraries between two places: a MOTIS stop ID or `lat,lon`. [time] is the departure,
     * or the arrival with [arriveBy].
     */
    suspend fun plan(
        from: String,
        to: String,
        time: Instant? = null,
        arriveBy: Boolean = false,
        pageCursor: String? = null,
    ): MotisPlan =
        client.get("${baseUrl()}api/v5/plan") {
            parameter("fromPlace", from)
            parameter("toPlace", to)
            time?.let { parameter("time", it.toString()) }
            if (arriveBy) parameter("arriveBy", true)
            pageCursor?.let { parameter("pageCursor", it) }
        }.body()
}
