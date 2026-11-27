package cz.obehy.feature.map

import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.Mode
import cz.obehy.core.model.StopCall
import cz.obehy.core.model.TimePoint
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FramingTest {
    private val now = Instant.now()

    /** Stops [step] degrees of longitude apart (0.01° is about 700 m here), each a minute after the last; the first [passed] gone by. */
    private fun trip(
        stops: Int,
        step: Double,
        passed: Int,
    ): TripDetail {
        val calls =
            (0 until stops).map { index ->
                val time = TimePoint(now.plus(Duration.ofMinutes((index - passed).toLong() + 1)))
                TripStop(
                    StopCall(
                        "s$index",
                        "s$index",
                        time,
                        time,
                        null,
                        null,
                        position = LatLon(50.0, 14.0 + index * step),
                    ),
                    passed = index < passed,
                )
            }
        val ride =
            Leg.Transit(
                Mode.Tram,
                calls.first().call,
                calls.last().call,
                calls.first().call.departure!!,
                calls.last().call.arrival!!,
                name = "22",
                line = "22",
                headsign = null,
                agency = null,
                color = null,
                textColor = null,
                accessible = null,
                cancelled = false,
                tripId = "t",
            )
        return TripDetail(ride, calls, boarded = null)
    }

    @Test
    fun `a vehicle is framed with the stops ahead of it, not the ones it has passed`() {
        val vehicle = LatLon(50.0, 14.025)
        val frame = trip(stops = 12, step = 0.01, passed = 3).aheadFrame(vehicle)
        // It and six stops ahead, from the first not passed.
        assertEquals(listOf(vehicle) + (3 until 9).map { LatLon(50.0, 14.0 + it * 0.01) }, frame)
    }

    @Test
    fun `stops too far ahead to see the vehicle with are left out`() {
        val vehicle = LatLon(50.0, 14.0)
        // 0.05° is about 3.6 km: only the first is within reach.
        val frame = trip(stops = 6, step = 0.05, passed = 1).aheadFrame(vehicle)
        assertEquals(listOf(vehicle, LatLon(50.0, 14.05)), frame)
    }

    @Test
    fun `nothing is framed when the next stop is far off or nearly reached`() {
        assertNull(trip(stops = 4, step = 0.2, passed = 1).aheadFrame(LatLon(50.0, 14.0)))
        assertNull(trip(stops = 2, step = 0.003, passed = 1).aheadFrame(LatLon(50.0, 14.0)))
    }
}
