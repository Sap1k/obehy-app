package cz.obehy.core.network

import cz.obehy.core.network.motis.MotisApi
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MotisApiTest {
    private val seen = mutableListOf<HttpRequestData>()

    private fun api(body: String) =
        MotisApi(mockClient(MotisJson, seen) { json(body) }) { "https://api.transitous.org/" }

    @Test
    fun `a recorded Transitous plan parses, foreign legs included`() =
        runTest {
            val plan =
                api(fixture("motis/plan-usti-dresden.json"))
                    .plan("50.6596,14.0447", "51.0405,13.7320", Instant.parse("2026-11-03T07:00:00Z"))

            val legs = plan.itineraries.single().legs
            assertEquals(
                listOf("WALK", "REGIONAL_RAIL", "WALK", "REGIONAL_RAIL", "WALK", "SUBURBAN", "WALK"),
                legs.map { it.mode },
            )
            val rail = legs[1]
            assertEquals(
                "20261103_06:47_cz-CZPTT_czptt:trip:Pa%3A0054%3AKT----11435A%3A00%3A2026:1",
                rail.tripId,
            )
            assertEquals("R20 (R 696)", rail.displayName)
            assertEquals("cz-CZPTT_czptt:stop:CZ:53179:platform:2", rail.from.stopId)
            assertEquals("2", rail.from.track)
            assertTrue(legs[5].tripId!!.contains("_de-DELFI_"))
            assertNull(legs[0].tripId)
        }

    @Test
    fun `plan asks the v5 endpoint of the current provider and names the app`() =
        runTest {
            api("""{"itineraries": []}""")
                .plan("a", "b", Instant.parse("2026-11-03T07:00:00Z"), arriveBy = true)

            val url = seen.single().url
            assertEquals("api.transitous.org", url.host)
            assertEquals("/api/v5/plan", url.encodedPath)
            assertEquals("2026-11-03T07:00:00Z", url.parameters["time"])
            assertEquals("true", url.parameters["arriveBy"])
            assertEquals("Obehy-Android/test", seen.single().headers[HttpHeaders.UserAgent])
        }

    @Test
    fun `geocode returns stops with their ids`() =
        runTest {
            val matches = api(fixture("motis/geocode-usti.json")).geocode("Usti nad Labem hl.n.")
            assertEquals("de-DELFI_000005400019", matches.first().id)
            assertEquals("STOP", matches.first().type)
            assertEquals("Usti nad Labem hl.n.", seen.single().url.parameters["text"])
        }
}
