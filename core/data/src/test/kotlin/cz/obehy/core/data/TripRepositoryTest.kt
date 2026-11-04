package cz.obehy.core.data

import cz.obehy.core.model.AppMode
import cz.obehy.core.network.MotisJson
import cz.obehy.core.network.ObehyJson
import cz.obehy.core.network.apiClient
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.obehy.ObehyApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val JSON = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun place(
    id: String,
    lon: Double,
) = """{"name":"$id","stopId":"$id","lat":50.0,"lon":$lon,"departure":"2026-11-04T20:00:00Z",
    "scheduledDeparture":"2026-11-04T20:00:00Z","arrival":"2026-11-04T20:00:00Z",
    "scheduledArrival":"2026-11-04T20:00:00Z"}"""

private val MOTIS_TRIP =
    """{"duration":600,"startTime":"2026-11-04T20:00:00Z","endTime":"2026-11-04T20:10:00Z","transfers":0,
    "legs":[{"mode":"REGIONAL_RAIL","tripId":"m","displayName":"Os 1","duration":600,
    "startTime":"2026-11-04T20:00:00Z","endTime":"2026-11-04T20:10:00Z",
    "scheduledStartTime":"2026-11-04T20:00:00Z","scheduledEndTime":"2026-11-04T20:10:00Z",
    "realTime":false,"from":${place("a", 14.0)},"to":${place("d", 14.3)},
    "intermediateStops":[${place("b", 14.1)},${place("c", 14.2)}]}]}"""

private fun call(
    sequence: Int,
    name: String,
) = """{"sequence":$sequence,"location_id":"o$name","name":"$name","status":"scheduled",
    "scheduled_departure":"2026-11-04T21:00:00+01:00"}"""

private val ENRICHED =
    """{"release_id":"r","generated_at":"2026-11-04T21:00:00+01:00","legs":[{"motis_trip_id":"m",
    "outcome":"resolved","trip":{"trip_id":"o","service_date":"2026-11-04"},"leg_calls":true,
    "calls":[${call(20, "b")},${call(30, "c")}]}]}"""

private val OBEHY_TRIP =
    """{"trip_id":"o","service_date":"2026-11-04","composition":true,
    "calls":[${call(10, "a")},${call(20, "b")},${call(30, "c")},${call(40, "d")}]}"""

class TripRepositoryTest {
    private val prefs = MemoryPreferences()
    private val settings = SettingsRepository(prefs)
    private val asked = mutableListOf<String>()
    private var obehyWorks = true

    private val repository =
        TripRepository(
            MotisApi(
                apiClient(
                    MockEngine { request ->
                        asked += request.url.encodedPath
                        respond(MOTIS_TRIP, HttpStatusCode.OK, JSON)
                    },
                    MotisJson,
                    "test",
                ),
            ) { "https://motis.test/" },
            ObehyApi(
                apiClient(
                    MockEngine { request ->
                        asked += request.url.encodedPath
                        when {
                            !obehyWorks -> respond("{}", HttpStatusCode.InternalServerError, JSON)
                            request.url.encodedPath.endsWith("enrich") -> respond(ENRICHED, HttpStatusCode.OK, JSON)
                            else -> respond(OBEHY_TRIP, HttpStatusCode.OK, JSON)
                        }
                    },
                    ObehyJson,
                    "test",
                ),
            ),
            settings,
            Dispatchers.Default,
        )

    private suspend fun load(
        from: String?,
        to: String?,
    ): cz.obehy.core.model.TripDetail {
        val request = TripRequest("m", from, to)
        return repository.withDetails(repository.plain(request).getOrThrow(), request)
    }

    @Test
    fun `a plain trip fetched ahead is there at once, and asked for only once`() =
        runTest {
            settings.setMode(AppMode.Obehy)
            val request = TripRequest("m", "b", "c")
            assertNull(repository.fetchedAhead(request))

            repository.plain(request).getOrThrow()
            repository.plain(request).getOrThrow()

            assertEquals(1, asked.count { "trip" in it })
            assertEquals(1..2, repository.fetchedAhead(request)!!.boarded)
        }

    @Test
    fun `without Oběhy the trip comes from MOTIS alone, with the stops ridden found by their IDs`() =
        runTest {
            settings.setMode(AppMode.MotisOnly)

            val trip = load("b", "d")

            assertEquals(listOf("a", "b", "c", "d"), trip.stops.map { it.call.name })
            assertEquals(1..3, trip.boarded)
            assertNull(trip.info)
            assertTrue(asked.none { "enrich" in it || "trips" in it })
        }

    @Test
    fun `with Oběhy the stops stay MOTIS's, with its places in the timetable added`() =
        runTest {
            settings.setMode(AppMode.Obehy)

            val trip = load("b", "c")

            assertEquals(20, trip.stops[trip.boarded!!.first].sequence)
            assertEquals(1..2, trip.boarded)
            assertEquals("ob", trip.info!!.boardingLocationId)
            assertTrue(trip.info!!.hasComposition)
        }

    @Test
    fun `when Oběhy fails the trip still shows`() =
        runTest {
            settings.setMode(AppMode.Obehy)
            obehyWorks = false

            val trip = load("b", "c")

            assertNull(trip.info)
            assertEquals(1..2, trip.boarded)
        }

    @Test
    fun `a refresh keeps the journey's stretch and where it boards`() =
        runTest {
            settings.setMode(AppMode.Obehy)
            val trip = load("b", "c")
            asked.clear()

            val fresh = repository.refresh(trip, "m").getOrThrow()

            assertEquals(1..2, fresh.boarded)
            assertEquals("ob", fresh.info!!.boardingLocationId)
            assertFalse(asked.any { it.endsWith("enrich") })
        }
}
