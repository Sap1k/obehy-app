package cz.obehy.core.network

import cz.obehy.core.model.HitKind
import cz.obehy.core.model.Mode
import cz.obehy.core.model.TripRef
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.toDepartures
import cz.obehy.core.network.motis.toSegment
import cz.obehy.core.network.obehy.ObehyApi
import cz.obehy.core.network.obehy.toBoard
import cz.obehy.core.network.obehy.toHits
import cz.obehy.core.network.obehy.toLiveStops
import cz.obehy.core.network.obehy.toLiveVehicle
import cz.obehy.core.network.obehy.toTripDetail
import cz.obehy.core.network.obehy.toTripInfo
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Fixtures recorded from obehy.cz, Transitous and motis.obehy.cz on 2026-11-20 and brought to the
// API contract of obehy 7eb27e8 (delay_min + punctuality, run, shape, per-call bands).
class LiveMapApiTest {
    private val seen = mutableListOf<HttpRequestData>()

    private fun obehy(file: String) = ObehyApi(mockClient(ObehyJson, seen) { json(fixture(file)) })

    private fun motis(file: String) =
        MotisApi(
            mockClient(MotisJson, seen) {
                json(fixture(file))
            },
        ) { "https://motis.test/" }

    @Test
    fun `vehicles come with the API's band, never one of the app's`() =
        runTest {
            val answer = obehy("obehy/vehicles-praha.json").vehicles("14.25,50,14.5,50.25")

            assertEquals("14.25,50,14.5,50.25", seen.single().url.parameters["bbox"])
            val vehicles = answer.vehicles.mapNotNull { it.toLiveVehicle() }
            assertEquals(14, vehicles.size)
            val train = vehicles.single { it.id == "train:52" }
            assertEquals("Ex3", train.line)
            assertEquals("rj 52", train.tripShortName)
            assertEquals(Mode.RegionalRail, train.mode)
            assertEquals(16L, train.delayMinutes)
            assertTrue(train.late)
            assertTrue(train.trip is TripRef.Obehy)
            val tram = vehicles.single { it.id == "pid:0:8432" }
            assertFalse(tram.late)
            assertEquals("7A0603", tram.color)
            assertEquals(Mode.Bus, vehicles.single { it.id == "pid:11:3999" }.mode)
        }

    @Test
    fun `an unknown band shows no delay`() =
        runTest {
            val metro =
                obehy("obehy/vehicles-praha.json")
                    .vehicles("x")
                    .vehicles
                    .single { it.vehicleId == "train:579" }
                    .toLiveVehicle()!!

            assertNull(metro.delayMinutes)
            assertFalse(metro.late)
        }

    @Test
    fun `a search says what each vehicle matched`() =
        runTest {
            val hits = obehy("obehy/vehicles-search-22.json").searchVehicles("22", near = "50.08,14.42").toHits("22")

            val parameters = seen.single().url.parameters
            assertEquals("22", parameters["q"])
            assertEquals("50.08,14.42", parameters["near"])
            assertEquals(6, hits.size)
            assertEquals(HitKind.Line, hits.first().matched)
            assertEquals(317, hits.first().metres)
            assertTrue(hits.all { it.matched == HitKind.Line })
        }

    @Test
    fun `stops and a board come with Oběhy's trips`() =
        runTest {
            assertEquals(6, obehy("obehy/stops-praha.json").stops("x").toLiveStops().size)

            val board = obehy("obehy/departures-tram.json").departures("jdf:stop:24325", limit = 8).toBoard("cs")

            assertTrue(
                seen
                    .last()
                    .url.encodedPath
                    .endsWith("/stop-places/jdf:stop:24325/departures"),
            )
            assertEquals("8", seen.last().url.parameters["limit"])
            assertEquals(8, board.departures.size)
            val first = board.departures.first()
            assertEquals("26", first.line)
            assertEquals(4L, first.time.delayMinutes)
            assertTrue(first.late)
            assertTrue(first.trip is TripRef.Obehy)
            assertFalse(board.departures[2].late)
        }

    @Test
    fun `a trip opened from a vehicle stands on Oběhy alone`() =
        runTest {
            val api = obehy("obehy/trip-train-run.json")
            val trip =
                api.trip(
                    "czptt:trip:Pa%3A0054%3AKT----21347A%3A00%3A2026:1",
                    "2026-11-20",
                    listOf("shape", "run"),
                )

            assertEquals("shape,run", seen.single().url.parameters["include"])
            val detail = trip.toTripDetail("cs")
            assertEquals(10, detail.stops.size)
            assertEquals("Ex3", detail.ride.line)
            assertEquals("rj 52", detail.ride.tripShortName)
            assertTrue(detail.ride.path.size >= 2)
            assertEquals(9, detail.stops.indexOfFirst { it.passed == false })
            assertEquals(
                16L,
                detail.stops[9]
                    .call.arrival
                    ?.delayMinutes,
            )
            assertEquals("czptt:trip:Pa%3A0054%3AKT----21347A%3A00%3A2026:1", detail.info?.tripId)
        }

    @Test
    fun `the overlay trip still reads as before`() =
        runTest {
            val trip = obehy("obehy/trip-czptt.json").trip("t", "2026-11-04")

            assertNull(trip.run)
            assertNull(trip.shape)
            assertEquals(trip.tripId, trip.toTripInfo("cs").tripId)
        }

    @Test
    fun `railviz stretches decode from both servers`() =
        runTest {
            val api = motis("motis/map-trips-transitous.json")
            val start = Instant.parse("2026-11-20T22:47:00Z")
            val stretches = api.mapTrips("49.9,14.2", "50.2,14.7", 10, start, start.plusSeconds(120))

            val parameters = seen.single().url.parameters
            assertEquals("49.9,14.2", parameters["min"])
            assertEquals("10", parameters["zoom"])
            assertEquals("5", parameters["precision"])
            assertEquals("2026-11-20T22:47:00Z", parameters["startTime"])
            val segments = stretches.mapNotNull { it.toSegment() }
            assertEquals(10, segments.size)
            assertTrue(segments.all { it.path.size >= 2 })
            assertEquals(1, segments.count { it.color == null })
            assertEquals(5, segments.count { it.tripId == "20261120_23:45_cz-PID_240_3400_261103" })

            val obehySegments =
                motis("motis/map-trips-obehy.json")
                    .mapTrips("a", "b", 10, start, start)
                    .mapNotNull { it.toSegment() }
            assertTrue(obehySegments.isNotEmpty())
            assertTrue(obehySegments.all { it.path.first().lat in 49.0..51.5 })
        }

    @Test
    fun `railviz is asked in whole seconds, which is all MOTIS answers to`() =
        runTest {
            val start = Instant.parse("2026-11-21T00:28:40.989094123Z")

            motis("motis/map-trips-obehy.json").mapTrips("a", "b", 13, start, start.plusSeconds(180))

            val parameters = seen.single().url.parameters
            assertEquals("2026-11-21T00:28:40Z", parameters["startTime"])
            assertEquals("2026-11-21T00:31:40Z", parameters["endTime"])
        }

    @Test
    fun `stop times read as a board`() =
        runTest {
            val rows = motis("motis/stoptimes-tram.json").stopTimes("cz-PID_U12Z3P", 5).toDepartures()

            assertEquals("cz-PID_U12Z3P", seen.single().url.parameters["stopId"])
            assertEquals(5, rows.size)
            assertEquals("17", rows.first().line)
            assertEquals(Mode.Tram, rows.first().mode)
            assertEquals(1L, rows[1].time.delayMinutes)
            assertTrue(rows.first().trip is TripRef.Motis)
        }
}
