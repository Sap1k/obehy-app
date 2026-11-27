package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RailvizTest {
    private val a = LatLon(50.0, 14.0)
    private val b = LatLon(50.0, 14.1)
    private val c = LatLon(50.1, 14.1)

    private fun at(time: String) = Instant.parse("2026-11-20T$time:00Z")

    private fun stretch(
        from: LatLon,
        to: LatLon,
        leaves: String,
        arrives: String,
        delay: Long = 0,
        trip: String = "t1",
    ) = RailvizSegment(
        tripId = trip,
        line = "S4",
        mode = Mode.SuburbanRail,
        color = "1D7DC5",
        fromName = "A",
        toName = "B",
        departure = TimePoint(at(leaves), at(leaves).plusSeconds(delay * 60), realtime = delay != 0L),
        arrival = TimePoint(at(arrives), at(arrives).plusSeconds(delay * 60), realtime = delay != 0L),
        path = listOf(from, to),
    )

    @Test
    fun `half way in time is half way along`() {
        val place = listOf(stretch(a, b, "10:00", "10:10")).placeAt(at("10:05"))!!

        assertEquals(14.05, place.position.position.lon, 1e-6)
        assertTrue(place.position.estimated)
        assertEquals(90.0, place.position.bearing!!, 1.0)
        assertFalse(place.waiting)
    }

    @Test
    fun `between two stretches it waits at the stop`() {
        val trip = listOf(stretch(a, b, "10:00", "10:10"), stretch(b, c, "10:12", "10:20"))

        val place = trip.placeAt(at("10:11"))!!

        assertEquals(b, place.position.position)
        assertTrue(place.waiting)
        assertEquals(at("10:12"), place.segment.departure.expected)
    }

    @Test
    fun `nothing is guessed outside the window`() {
        val trip = listOf(stretch(a, b, "10:00", "10:10"))

        assertNull(trip.placeAt(at("09:59")))
        assertNull(trip.placeAt(at("10:10")))
    }

    @Test
    fun `a delay moves it by the expected times and marks it late`() {
        val trip = listOf(stretch(a, b, "10:00", "10:10", delay = 5))

        val vehicle = trip.vehiclesAt(at("10:10")).single()

        assertEquals(14.05, vehicle.at.position.lon, 1e-6)
        assertEquals(5L, vehicle.delayMinutes)
        assertTrue(vehicle.late)
        assertEquals(TripRef.Motis("t1"), vehicle.trip)
    }

    @Test
    fun `past midnight is just later`() {
        val trip =
            listOf(
                stretch(a, b, "23:58", "23:59"),
                stretch(b, c, "23:59", "23:59").copy(
                    arrival = TimePoint(Instant.parse("2026-11-21T00:04:00Z")),
                ),
            )

        assertNotNull(trip.placeAt(Instant.parse("2026-11-21T00:02:00Z")))
    }

    @Test
    fun `one vehicle per trip`() {
        val stretches =
            listOf(
                stretch(a, b, "10:00", "10:10"),
                stretch(b, c, "10:10", "10:20"),
                stretch(a, c, "10:00", "10:20", trip = "t2"),
            )

        assertEquals(setOf("t1", "t2"), stretches.vehiclesAt(at("10:05")).map { it.id }.toSet())
    }
}
