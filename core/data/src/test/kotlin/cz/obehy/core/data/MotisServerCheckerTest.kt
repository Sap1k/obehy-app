package cz.obehy.core.data

import cz.obehy.core.network.MotisJson
import cz.obehy.core.network.apiClient
import cz.obehy.core.network.motis.MotisApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotisServerCheckerTest {
    private val answers =
        mutableMapOf(
            "/api/v1/map/initial" to """{"lat":49.0,"lon":15.0,"zoom":8,"serverConfig":{"motisVersion":"v2.11.4"}}""",
            "/api/v1/geocode" to
                """[{"type":"STOP","name":"A","id":"a","lat":49,"lon":15},
                    {"type":"STOP","name":"B","id":"b","lat":49.1,"lon":15}]""",
            "/api/v1/map/stops" to
                """[{"name":"A","stopId":"a","lat":49,"lon":15},{"name":"C","stopId":"c","lat":49,"lon":15}]""",
            "/api/v6/plan" to """{"itineraries":[$ITINERARY]}""",
            "/api/v6/refresh-itinerary" to ITINERARY,
        )
    private val asked = mutableListOf<String>()
    private val steps = mutableListOf<ServerCheckStep>()

    private val checker =
        MotisServerChecker(Dispatchers.Unconfined) { url ->
            val engine =
                MockEngine { request ->
                    asked += request.url.toString()
                    answers[request.url.encodedPath]?.let {
                        respond(it, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    } ?: respond("", HttpStatusCode.NotFound)
                }
            MotisApi(apiClient(engine, MotisJson, "test")) { url }
        }

    private suspend fun check() = checker.check("https://motis.example.org/") { steps += it }

    @Test
    fun `a working server passes every step`() =
        runTest {
            assertEquals(ServerCheckResult.Passed("v2.11.4"), check())
            assertEquals(ServerCheckStep.entries, steps)
            assertTrue(asked.any { "refresh-itinerary" in it })
            assertTrue(asked.all { it.startsWith("https://motis.example.org/") })
        }

    @Test
    fun `a web server that is not MOTIS fails straight away`() =
        runTest {
            answers.clear()

            assertEquals(ServerCheckResult.Failed(ServerCheckStep.Server), check())
        }

    @Test
    fun `an answer our models can't read fails that step`() =
        runTest {
            answers["/api/v6/plan"] = """{"itineraries":[{"legs":"none"}]}"""

            assertEquals(ServerCheckResult.Failed(ServerCheckStep.Journeys), check())
        }

    @Test
    fun `a server without stops fails on places`() =
        runTest {
            answers["/api/v1/geocode"] = "[]"

            assertEquals(ServerCheckResult.Failed(ServerCheckStep.Places), check())
        }

    @Test
    fun `an old server that can't refresh journeys fails`() =
        runTest {
            answers.remove("/api/v6/refresh-itinerary")

            assertEquals(ServerCheckResult.Failed(ServerCheckStep.Journeys), check())
        }

    @Test
    fun `no journey between the two stops still passes`() =
        runTest {
            answers["/api/v6/plan"] = """{"itineraries":[]}"""

            assertEquals(ServerCheckResult.Passed("v2.11.4"), check())
        }

    private companion object {
        const val ITINERARY =
            """{"id":"x","duration":60,"startTime":"2026-11-04T08:00:00Z","endTime":"2026-11-04T08:01:00Z",
                "transfers":0,"legs":[]}"""
    }
}
