package cz.obehy.core.network

import cz.obehy.core.network.obehy.EnrichLegRequest
import cz.obehy.core.network.obehy.EnrichOutcome
import cz.obehy.core.network.obehy.ObehyApi
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ObehyApiTest {
    private val seen = mutableListOf<HttpRequestData>()
    private val api = ObehyApi(mockClient(ObehyJson, seen) { json(fixture("obehy/enrich-usti-dresden.json")) })

    @Test
    fun `enrich posts the MOTIS ids verbatim in snake case`() =
        runTest {
            api.enrich(
                listOf(
                    EnrichLegRequest(
                        tripId = "20261103_06:47_cz-CZPTT_czptt:trip:Pa%3A0054%3AKT----11435A%3A00%3A2026:1",
                        fromStopId = "cz-CZPTT_czptt:stop:CZ:53179:platform:2",
                    ),
                ),
            )

            val request = seen.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://obehy.cz/api/v1/journeys/enrich", request.url.toString())
            val body =
                Json.parseToJsonElement(
                    (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                ) as JsonObject
            val leg = body["legs"]!!.jsonArray.single().jsonObject
            // %3A stays as it is: decoding it would name another trip.
            assertEquals(
                "20261103_06:47_cz-CZPTT_czptt:trip:Pa%3A0054%3AKT----11435A%3A00%3A2026:1",
                leg["trip_id"]!!.jsonPrimitive.content,
            )
            assertEquals("cz-CZPTT_czptt:stop:CZ:53179:platform:2", leg["from_stop_id"]!!.jsonPrimitive.content)
            assertFalse("to_stop_id" in leg)
        }

    @Test
    fun `a resolved leg carries the trip and its leg calls, a foreign one only why not`() =
        runTest {
            val (rail, foreign) = api.enrich(listOf(EnrichLegRequest("x"), EnrichLegRequest("y"))).legs

            assertEquals(EnrichOutcome.Resolved, rail.outcome)
            assertEquals("2026-11-03", rail.trip!!.serviceDate)
            assertEquals(120, rail.realtime!!.delayS)
            assertEquals(
                "471 016",
                rail.vehicles
                    .single()
                    .traits.fleetNumber,
            )
            assertTrue(rail.legCalls)
            assertEquals(listOf(30, 33), rail.calls.map { it.sequence })

            assertEquals(EnrichOutcome.UnknownDataset, foreign.outcome)
            assertEquals(emptyList<Any>(), foreign.calls)
        }

    @Test
    fun `more legs than the API takes are refused before asking`() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { api.enrich(List(ObehyApi.MAX_LEGS + 1) { EnrichLegRequest("x") }) }
        }
        assertTrue(seen.isEmpty())
    }
}
