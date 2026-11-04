package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class TripDetailTest {
    private val time = TimePoint(Instant.parse("2026-11-04T20:00:00Z"))

    private fun stop(
        name: String,
        at: LatLon,
        delay: Long = 0,
    ): StopCall {
        val point = TimePoint(time.scheduled, time.scheduled.plusSeconds(delay * 60), realtime = delay != 0L)
        return StopCall(name, name, point, point, null, null, position = at)
    }

    private val a = LatLon(50.0, 14.0)
    private val b = LatLon(50.0, 14.1)
    private val c = LatLon(50.0, 14.2)
    private val d = LatLon(50.0, 14.3)

    private val ride =
        Leg.Transit(
            mode = Mode.RegionalRail,
            from = stop("A", a),
            to = stop("D", d),
            start = time,
            end = time,
            name = "Os 1",
            line = null,
            headsign = null,
            agency = null,
            color = null,
            textColor = null,
            accessible = null,
            cancelled = false,
            tripId = "t",
            intermediateStops = listOf(stop("B", b), stop("C", c)),
            path = listOf(a, b, LatLon(50.0, 14.15), c, d),
        )

    @Test
    fun `a whole ride's stops are its ends and everything between`() {
        assertEquals(listOf("A", "B", "C", "D"), TripDetail.stopsOf(ride).map { it.call.name })
    }

    @Test
    fun `the map draws the stretch ridden, with the rest of the trip around it`() {
        val map = TripDetail(ride, TripDetail.stopsOf(ride), 1..2).map

        val leg = map.legs.single() as Leg.Transit
        assertEquals("B", leg.from.name)
        assertEquals("C", leg.to.name)
        assertEquals(listOf(b, LatLon(50.0, 14.15), c), leg.path)
        assertEquals(ride.path, map.around.path)
        assertEquals(listOf(a, d), map.around.stops)
    }

    @Test
    fun `without a stretch the map draws the whole trip, its stops around it`() {
        val map = TripDetail(ride, TripDetail.stopsOf(ride), null).map

        assertSame(ride, map.legs.single())
        assertEquals(emptyList<LatLon>(), map.around.path)
        assertEquals(listOf(b, c), map.around.stops)
    }

    @Test
    fun `a trip keeps its map when its times are refreshed`() {
        val trip = TripDetail(ride, TripDetail.stopsOf(ride), 1..2)

        assertSame(trip.map, trip.copy(stops = trip.stops.reversed()).map)
    }

    @Test
    fun `who may get on and off comes from the ride itself`() {
        val closed = ride.copy(intermediateStops = listOf(stop("B", b).copy(noBoarding = true)))

        assertEquals(setOf(CallFlag.ExitOnly), TripDetail.stopsOf(closed)[1].flags)
    }

    @Test
    fun `a trip's attributes come from the feed before any notes do`() {
        val plain = TripDetail(ride.copy(accessible = true, bikesAllowed = true), TripDetail.stopsOf(ride), null)

        assertEquals(listOf("wheelchair", "bicycle"), plain.attributes.map { it.icon })
    }

    @Test
    fun `a reservation comes from the feed until the notes say otherwise`() {
        val booked = ride.copy(reservation = Reservation.Required)
        val plain = TripDetail(booked, TripDetail.stopsOf(ride), null)
        val notes = TripNotes(marked = listOf(MarkedNote("Seats can be booked", "reservation")))
        val info = TripInfo("t", "2026-11-04", false, null, emptyList(), notes, emptyList(), emptyList(), false)

        assertEquals(listOf("reservation_required"), plain.attributes.map { it.icon })
        assertEquals(listOf("reservation"), plain.copy(info = info).attributes.map { it.icon })
    }

    private fun call(
        name: String,
        at: LatLon,
        minute: Long,
        leavesAfter: Long = 0,
    ): StopCall {
        val arrival = TimePoint(time.scheduled.plusSeconds(minute * 60))
        val departure = TimePoint(arrival.scheduled.plusSeconds(leavesAfter * 60))
        return StopCall(name, name, arrival, departure, null, null, position = at)
    }

    @Test
    fun `an estimate follows the path by the expected times, and waits at stops`() {
        val stops =
            listOf(TripStop(call("A", a, 0)), TripStop(call("B", b, 10, leavesAfter = 2)), TripStop(call("D", d, 22)))
        val trip = TripDetail(ride.copy(path = listOf(a, b, c, d)), stops, null)

        fun at(minute: Long) = trip.estimatedPosition(time.scheduled.plusSeconds(minute * 60))

        assertNull(at(-1))
        assertEquals(14.05, at(5)!!.position.lon, 0.001)
        assertEquals(b, at(11)!!.position)
        assertEquals(14.2, at(17)!!.position.lon, 0.001)
        assertEquals(true, at(17)!!.estimated)
        assertNull(at(23))
    }

    @Test
    fun `a cancelled trip is nowhere`() {
        val stops = listOf(TripStop(call("A", a, 0)), TripStop(call("D", d, 10)))
        val trip = TripDetail(ride.copy(cancelled = true), stops, null)

        assertNull(trip.estimatedPosition(time.scheduled.plusSeconds(300)))
    }

    @Test
    fun `a path that does not reach the stops stays whole`() {
        val path = listOf(a, b)

        assertSame(path, path.between(b, a))
        assertEquals(emptyList<LatLon>(), emptyList<LatLon>().between(a, b))
    }

    @Test
    fun `the delay is the live trip's, else the first live stop's`() {
        val stops = listOf(TripStop(stop("A", a)), TripStop(stop("B", b, delay = 4)))
        val plain = TripDetail(ride, stops, null)
        val info = TripInfo("t", "2026-11-04", false, 7, emptyList(), TripNotes(), emptyList(), emptyList(), false)

        assertEquals(4L, plain.delayMinutes)
        assertEquals(7L, plain.copy(info = info).delayMinutes)
        assertNull(TripDetail(ride, listOf(TripStop(stop("A", a))), null).delayMinutes)
    }
}
