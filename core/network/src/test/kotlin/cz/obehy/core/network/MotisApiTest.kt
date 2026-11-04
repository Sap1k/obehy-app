package cz.obehy.core.network

import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.Mode
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.model.TransportType
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.MotisPlanRequest
import cz.obehy.core.network.motis.decodePolyline
import cz.obehy.core.network.motis.motisPlanRequest
import cz.obehy.core.network.motis.toDomain
import cz.obehy.core.network.motis.toJourneyPage
import cz.obehy.core.network.motis.toPlace
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MotisApiTest {
    private val seen = mutableListOf<HttpRequestData>()

    private fun api(body: String) =
        MotisApi(mockClient(MotisJson, seen) { json(body) }) { "https://api.transitous.org/" }

    private fun stop(id: String) = Place(id, id, null, PlaceKind.Stop, 0.0, 0.0)

    @Test
    fun `a recorded Transitous plan parses, foreign legs included`() =
        runTest {
            val plan = api(fixture("motis/plan-usti-dresden.json")).plan(MotisPlanRequest("a", "b"))

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
            assertTrue(legs[5].tripId!!.contains("_de-DELFI_"))
            assertNull(legs[0].tripId)
        }

    @Test
    fun `plan asks the v6 endpoint of the current provider with every option`() =
        runTest {
            api("""{"itineraries": []}""").plan(
                MotisPlanRequest(
                    from = "a",
                    to = "50.1,14.4",
                    via = listOf("v1", "v2"),
                    time = Instant.parse("2026-11-03T07:00:00Z"),
                    arriveBy = true,
                    wheelchair = true,
                    routedTransfers = true,
                    transitModes = listOf("BUS", "TRAM"),
                    language = "cs",
                ),
            )

            val url = seen.single().url
            assertEquals("api.transitous.org", url.host)
            assertEquals("/api/v6/plan", url.encodedPath)
            assertEquals("v1,v2", url.parameters["via"])
            assertEquals("2026-11-03T07:00:00Z", url.parameters["time"])
            assertEquals("true", url.parameters["arriveBy"])
            assertEquals("WHEELCHAIR", url.parameters["pedestrianProfile"])
            assertEquals("true", url.parameters["useRoutedTransfers"])
            assertEquals("BUS,TRAM", url.parameters["transitModes"])
            assertEquals("false", url.parameters["joinInterlinedLegs"])
            assertEquals("Obehy-Android/test", seen.single().headers[HttpHeaders.UserAgent])
        }

    @Test
    fun `a plain search sends no options`() =
        runTest {
            api("""{"itineraries": []}""").plan(MotisPlanRequest("a", "b"))

            val parameters = seen.single().url.parameters
            listOf("via", "time", "arriveBy", "pedestrianProfile", "useRoutedTransfers", "transitModes").forEach {
                assertNull(it, parameters[it])
            }
        }

    @Test
    fun `the request follows the query and the options`() {
        val query = JourneyQuery(stop("A"), Place("x", "Doma", null, PlaceKind.Address, 50.1, 14.4), listOf(stop("V")))

        assertNull(motisPlanRequest(query, SearchOptions(), "cs").transitModes)

        val noTrains = SearchOptions(types = setOf(TransportType.Tram, TransportType.RegionalBus))
        val request = motisPlanRequest(query, noTrains, "cs")
        assertEquals("A", request.from)
        assertEquals("50.1,14.4", request.to)
        assertEquals(listOf("V"), request.via)
        assertEquals(
            listOf("AERIAL_LIFT", "BUS", "FERRY", "FUNICULAR", "ODM", "OTHER", "TRAM"),
            request.transitModes,
        )
    }

    @Test
    fun `a v6 plan with a via maps to itineraries with names, platforms and accessibility`() =
        runTest {
            val page =
                api(fixture("motis/plan-v6-praha-brno-via-kolin.json"))
                    .plan(MotisPlanRequest("a", "b"))
                    .toJourneyPage()

            assertTrue(page.laterCursor != null && page.earlierCursor != null)
            val change = page.itineraries[1]
            assertEquals(1, change.transfers)
            val (first, second) = change.rides
            assertEquals("LE 1219", first.name)
            assertNull(first.line)
            assertEquals(Mode.LongDistanceRail, first.mode)
            assertEquals("9b", first.from.track)
            assertEquals("Ex3 (rj 255)", second.name)
            assertEquals("Ex3", second.line)
            assertEquals(false, second.accessible)
            assertEquals(Instant.parse("2026-11-03T13:23:00Z"), change.start.scheduled)
            assertEquals(175L, change.duration.toMinutes())
            assertTrue(change.legs.last() is Leg.Walk)
            assertTrue(first.intermediateStops.isNotEmpty())
        }

    @Test
    fun `a train that goes on under another number is one ride, not a change`() =
        runTest {
            val journey =
                api(fixture("motis/plan-v6-interlined.json"))
                    .plan(MotisPlanRequest("a", "b"))
                    .toJourneyPage()
                    .itineraries
                    .first()

            val rides = journey.rides
            assertEquals(listOf("R18 (R 883)", "Sp 1755", "S22 (Sp 1755)", "S2 (Sp 2053)", "4"), rides.map { it.name })
            assertEquals(listOf(false, false, true, false, false), rides.map { it.staySeated })
            assertEquals(3, journey.transfers)
        }

    @Test
    fun `polylines decode at the precision MOTIS gives`() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)

        assertEquals(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), points)
    }

    @Test
    fun `rides come with their path and stops with their position`() =
        runTest {
            val ride =
                api(fixture("motis/plan-v6-geometry.json"))
                    .plan(MotisPlanRequest("a", "b"))
                    .toJourneyPage()
                    .itineraries
                    .first()
                    .rides
                    .first()

            assertEquals("true", seen.single().url.parameters["detailedLegs"])
            val (start, end) = ride.path.first() to ride.path.last()
            assertEquals(50.08, start.lat, 0.02)
            assertEquals(14.43, start.lon, 0.02)
            assertEquals(49.19, end.lat, 0.02)
            assertEquals(16.61, end.lon, 0.02)
            assertEquals(50.08, ride.from.position!!.lat, 0.02)
        }

    @Test
    fun `a journey is refreshed by its id, and a stripped answer is not the same journey`() =
        runTest {
            val good =
                api(
                    fixture("motis/refresh-itinerary.json"),
                ).refreshItinerary("CugB==", language = "cs").toDomain()
            val stripped = api(fixture("motis/refresh-stripped.json")).refreshItinerary("CugB==").toDomain()

            val url = seen.first().url
            assertEquals("/api/v6/refresh-itinerary", url.encodedPath)
            assertEquals("CugB==", url.parameters["itineraryId"])
            assertTrue(good.rides.all { it.tripId != null && it.intermediateStops.isNotEmpty() })
            assertTrue(good.isRefreshedBy(good))
            // MOTIS sometimes answers with the ride's trip, stops and realtime gone.
            assertNull(stripped.rides.first().tripId)
            assertFalse(good.isRefreshedBy(stripped))
        }

    @Test
    fun `a blank headsign counts as none`() =
        runTest {
            val blank =
                fixture("motis/refresh-itinerary.json")
                    .replace(Regex(""""headsign"\s*:\s*"[^"]*""""), """"headsign":" """")

            val ride =
                api(blank)
                    .refreshItinerary("CugB==")
                    .toDomain()
                    .rides
                    .first()

            assertNull(ride.headsign)
        }

    @Test
    fun `a low-floor plan has only accessible rides`() =
        runTest {
            val page =
                api(fixture("motis/plan-v6-praha-wheelchair.json"))
                    .plan(MotisPlanRequest("a", "b"))
                    .toJourneyPage()

            assertTrue(page.itineraries.isNotEmpty())
            page.itineraries.forEach { assertEquals(true, it.accessible) }
            assertEquals(
                Mode.Tram,
                page.itineraries
                    .first()
                    .rides
                    .first()
                    .mode,
            )
        }

    @Test
    fun `geocode asks for stops near the bias and maps areas and modes`() =
        runTest {
            val places =
                api(fixture("motis/geocode-florenc.json"))
                    .geocode("Florenc", language = "cs", stopsOnly = true, bias = "49.8,15.5")
                    .map { it.toPlace() }

            val parameters = seen.single().url.parameters
            assertEquals("STOP", parameters["type"])
            assertEquals("49.8,15.5", parameters["placeBias"])
            val florenc = places.first()
            assertEquals("cz-PID_U689S1", florenc.id)
            assertEquals(PlaceKind.Stop, florenc.kind)
            assertEquals("Praha 8", florenc.area)
            assertEquals(setOf(Mode.Subway, Mode.Tram, Mode.Bus), florenc.modes)
            assertFalse(places.any { it.kind == PlaceKind.Address })
        }
}
