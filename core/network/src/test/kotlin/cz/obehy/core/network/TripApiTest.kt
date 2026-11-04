package cz.obehy.core.network

import cz.obehy.core.model.CompositionStatus
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.UnitKind
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.toTrip
import cz.obehy.core.network.obehy.ObehyApi
import cz.obehy.core.network.obehy.addedTo
import cz.obehy.core.network.obehy.toDomain
import cz.obehy.core.network.obehy.toTripInfo
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripApiTest {
    private val seen = mutableListOf<HttpRequestData>()
    private val tripId = "czptt:trip:Pa%3A0054%3AKT----22530A%3A00%3A2026:1"
    private val motisTripId = "20261104_21:58_czptt_$tripId"

    private fun obehy(
        file: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = ObehyApi(mockClient(ObehyJson, seen) { json(fixture(file), status) })

    private fun motis() =
        MotisApi(mockClient(MotisJson, seen) { json(fixture("motis/trip-czptt.json")) }) { "https://motis.test/" }

    @Test
    fun `a trip ID's percent signs go out encoded again`() =
        runTest {
            obehy("obehy/trip-czptt.json").trip(tripId, "2026-11-04")

            assertTrue(seen.single().url.encodedPath, "%253A0054%253A" in seen.single().url.encodedPath)
            assertTrue(
                seen
                    .single()
                    .url.encodedPath
                    .endsWith("/2026-11-04"),
            )
        }

    @Test
    fun `a MOTIS trip is asked for by its ID, leg by leg`() =
        runTest {
            val itinerary = motis().trip(motisTripId, "cs")

            val parameters = seen.single().url.parameters
            assertEquals(motisTripId, parameters["tripId"])
            assertEquals("false", parameters["joinInterlinedLegs"])
            val ride = itinerary.toTrip(motisTripId)!!
            assertEquals(motisTripId, ride.tripId)
            assertEquals(7, ride.intermediateStops.size)
            assertTrue(ride.path.isNotEmpty())
        }

    @Test
    fun `Oběhy adds its places in the timetable to the stops MOTIS gives`() =
        runTest {
            val ride = motis().trip(motisTripId, "cs").toTrip(motisTripId)!!
            val stops = TripDetail.stopsOf(ride)

            val added = obehy("obehy/trip-czptt.json").trip(tripId, "2026-11-04").addedTo(stops)

            assertEquals(stops.map { it.call }, added.map { it.call })
            assertEquals(5, added.first().sequence)
            assertEquals(115, added.last().sequence)
        }

    @Test
    fun `a MOTIS ride says what it can itself`() =
        runTest {
            val ride = motis().trip(motisTripId, "cs").toTrip(motisTripId)!!

            assertEquals(true, ride.bikesAllowed != null)
            assertFalse(ride.from.noBoarding)
        }

    @Test
    fun `each note of a trip is listed once`() =
        runTest {
            val info = obehy("obehy/trip-czptt.json").trip(tripId, "2026-11-04").toTripInfo("cs")

            val texts = info.notes.marked.map { it.text }
            assertTrue(texts.isNotEmpty())
            assertEquals(texts.distinct(), texts)
            assertTrue(info.hasComposition)
        }

    @Test
    fun `a composition comes with its units in order and what they are`() =
        runTest {
            val composition = obehy("obehy/composition-cd.json").composition(tripId, "2026-11-04").toDomain("en")

            assertEquals(CompositionStatus.Fresh, composition.status)
            assertEquals(10, composition.units.size)
            assertEquals(UnitKind.Locomotive, composition.units.first().kind)
            val sleeper = composition.units[1]
            assertEquals("364", sleeper.coachNumber)
            assertEquals("Budapest-Nyugati pu", sleeper.destination)
            assertEquals("Couchette car", sleeper.features.first().text)
            assertEquals(listOf("České dráhy"), composition.sources)
            assertEquals(1, composition.stretches().size)
        }

    @Test
    fun `an answer on its way says when to ask again`() =
        runTest {
            val body = """{"status":"pending","retry_after_s":3,"units":[],"segments":[]}"""
            val api = ObehyApi(mockClient(ObehyJson, seen) { json(body, HttpStatusCode.Accepted) })

            val pending = api.composition(tripId, "2026-11-04", from = "czptt:stop:CZ:57076").toDomain("cs")

            assertEquals("czptt:stop:CZ:57076", seen.single().url.parameters["from"])
            assertTrue(
                seen
                    .single()
                    .url.encodedPath
                    .endsWith("/composition"),
            )
            assertEquals(CompositionStatus.Pending, pending.status)
            assertEquals(3, pending.retryAfterSeconds)
            assertTrue(pending.checking)
            assertNull(pending.units.firstOrNull())
        }
}
