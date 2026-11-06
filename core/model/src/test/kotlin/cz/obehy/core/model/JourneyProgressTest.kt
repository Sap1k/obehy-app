package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class JourneyProgressTest {
    private val start = Instant.parse("2026-11-06T10:00:00Z")

    private fun at(minutes: Long) = start.plus(Duration.ofMinutes(minutes))

    private fun time(
        minutes: Long,
        late: Long = 0,
    ) = TimePoint(at(minutes), at(minutes + late), realtime = late != 0L)

    private fun stop(minutes: Long) =
        StopCall("S$minutes", "id$minutes", time(minutes), time(minutes), null, null, position = LatLon(50.0, 14.0))

    private fun walk(
        from: Long,
        to: Long,
    ) = Leg.Walk(stop(from), stop(to), time(from), time(to), null)

    private fun ride(
        from: Long,
        to: Long,
        late: Long = 0,
        cancelled: Boolean = false,
        staySeated: Boolean = false,
        passing: List<StopCall> = emptyList(),
    ) = Leg.Transit(
        mode = Mode.Bus,
        from = stop(from),
        to = stop(to),
        start = time(from, late),
        end = time(to, late),
        name = "R$from",
        line = null,
        headsign = null,
        agency = null,
        color = null,
        textColor = null,
        accessible = null,
        cancelled = cancelled,
        tripId = "t$from",
        staySeated = staySeated,
        intermediateStops = passing,
    )

    private fun journey(vararg legs: Leg) = Itinerary(legs.toList(), transfers = 0)

    @Test
    fun `spans meet halfway between rows`() {
        val spans = listOf(at(0), at(10), at(30)).rowSpans()

        assertEquals(RowSpan(at(0), at(0), at(5)), spans[0])
        assertEquals(RowSpan(at(5), at(10), at(20)), spans[1])
        assertEquals(RowSpan(at(20), at(30), at(30)), spans[2])
    }

    @Test
    fun `a row without a time goes with the one above, and an early one is held back`() {
        assertEquals(listOf(at(5), at(5), at(5)), listOf(at(5), null, at(2)).rowSpans().map { it.at })
    }

    @Test
    fun `no times at all make no spans`() {
        assertTrue(listOf<Instant?>(null, null).rowSpans().isEmpty())
    }

    @Test
    fun `a row fills down to its mark and on to the bottom`() {
        val span = RowSpan(at(0), at(10), at(30))

        assertEquals(0f, span.reached(at(-1), mark = 20f, height = 100f), 0.01f)
        assertEquals(10f, span.reached(at(5), mark = 20f, height = 100f), 0.01f)
        assertEquals(60f, span.reached(at(20), mark = 20f, height = 100f), 0.01f)
        assertEquals(100f, span.reached(at(30), mark = 20f, height = 100f), 0.01f)
    }

    @Test
    fun `a row with no time before its mark is past it at once`() {
        assertEquals(20f, RowSpan(at(0), at(0), at(5)).reached(at(0), mark = 20f, height = 100f), 0.01f)
    }

    @Test
    fun `one span for each row of the timeline`() {
        val legs =
            listOf(
                walk(0, 5),
                ride(10, 30, passing = listOf(stop(20))),
                ride(30, 40, staySeated = true),
                walk(40, 45),
            )

        // Start, walk; board, ride, alight; ride, alight; walk, end.
        assertEquals(9, legs.timelineSpans().size)
        // The same with the stop passed on the first ride showing.
        assertEquals(10, legs.timelineSpans(unfolded = setOf(1)).size)
    }

    @Test
    fun `an unfolded ride's card comes before its first stop`() {
        val times = listOf(ride(10, 30, passing = listOf(stop(20)))).timelineSpans(setOf(0)).map { it.at }

        assertEquals(listOf(at(10), at(15), at(20), at(30)), times)
    }

    @Test
    fun `the stage goes by the clock`() {
        val first = walk(0, 5)
        val second = ride(10, 30)
        val legs = listOf(first, second)

        assertNull(legs.stageAt(at(-1)))
        assertEquals(JourneyStage.Walking(first), legs.stageAt(at(2)))
        assertEquals(JourneyStage.Waiting(second), legs.stageAt(at(7)))
        assertEquals(JourneyStage.Riding(second), legs.stageAt(at(20)))
        assertNull(legs.stageAt(at(30)))
    }

    @Test
    fun `a journey that holds has no trouble`() {
        assertNull(journey(ride(0, 10), walk(10, 15), ride(20, 30)).troubleAt(at(0)))
    }

    @Test
    fun `a late ride misses the change, counting the walk`() {
        val arriving = ride(0, 10, late = 8)
        val departing = ride(20, 30)
        val trouble = journey(arriving, walk(10, 15), departing).troubleAt(at(0))

        assertEquals(JourneyTrouble.MissedChange(arriving, departing, Duration.ofMinutes(3)), trouble)
    }

    @Test
    fun `staying on board is no change to miss`() {
        assertNull(journey(ride(0, 10, late = 5), ride(10, 20, staySeated = true)).troubleAt(at(0)))
    }

    @Test
    fun `a cancelled ride is trouble`() {
        val cancelled = ride(20, 30, cancelled = true)

        assertEquals(JourneyTrouble.Cancelled(cancelled), journey(ride(0, 10), cancelled).troubleAt(at(0)))
    }

    @Test
    fun `a ride that no longer calls where you get off is trouble`() {
        val ride = ride(0, 10).let { it.copy(to = it.to.copy(cancelled = true)) }

        assertEquals(JourneyTrouble.Cancelled(ride), journey(ride).troubleAt(at(0)))
    }

    @Test
    fun `trouble that's behind you is over`() {
        assertNull(journey(ride(0, 10, late = 15), ride(20, 30)).troubleAt(at(31)))
        assertNull(journey(ride(0, 10, cancelled = true), ride(20, 30)).troubleAt(at(11)))
    }

    @Test
    fun `a missed change leaves you where you get off, when you get there`() {
        val arriving = ride(0, 10, late = 15)
        val itinerary = journey(arriving, ride(20, 30))
        val trouble = requireNotNull(itinerary.troubleAt(at(0)))

        assertEquals(ReplanStart(arriving.to, at(25)), itinerary.replanStart(trouble, at(0)))
    }

    @Test
    fun `replanning late starts from now`() {
        val itinerary = journey(ride(0, 10, late = 15), ride(20, 30))
        val trouble = requireNotNull(itinerary.troubleAt(at(27)))

        assertEquals(at(27), itinerary.replanStart(trouble, at(27))?.at)
    }

    @Test
    fun `a cancelled first ride leaves you at the start`() {
        val itinerary = journey(walk(0, 5), ride(10, 20, cancelled = true))

        assertNull(itinerary.replanStart(requireNotNull(itinerary.troubleAt(at(0))), at(0)))
    }

    @Test
    fun `a cancelled later ride leaves you where it would have left from`() {
        val cancelled = ride(20, 30, cancelled = true)
        val itinerary = journey(ride(0, 10), cancelled)

        assertEquals(
            ReplanStart(cancelled.from, at(10)),
            itinerary.replanStart(requireNotNull(itinerary.troubleAt(at(0))), at(0)),
        )
    }

    @Test
    fun `a stop becomes a place to search from, by its id or else where it is`() {
        val known = requireNotNull(stop(0).toPlace())
        assertEquals(PlaceKind.Stop, known.kind)
        assertEquals("id0", known.routingRef)

        val unnamed = requireNotNull(stop(0).copy(stopId = null).toPlace())
        assertEquals(PlaceKind.Address, unnamed.kind)
        assertEquals("50.0,14.0", unnamed.routingRef)

        assertNull(stop(0).copy(position = null).toPlace())
    }
}
