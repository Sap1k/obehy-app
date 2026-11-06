package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class JourneyFocusTest {
    private val start = Instant.parse("2026-11-06T10:00:00Z")

    private fun at(minutes: Long) = start.plus(Duration.ofMinutes(minutes))

    private fun time(minutes: Long) = TimePoint(at(minutes))

    private fun stop(minutes: Long) = StopCall("S$minutes", null, time(minutes), time(minutes), null, null)

    private fun walk(
        from: Long,
        to: Long,
    ) = Leg.Walk(stop(from), stop(to), time(from), time(to), null)

    private fun ride(
        from: Long,
        to: Long,
    ) = Leg.Transit(
        mode = Mode.Bus,
        from = stop(from),
        to = stop(to),
        start = time(from),
        end = time(to),
        name = "R$from",
        line = null,
        headsign = null,
        agency = null,
        color = null,
        textColor = null,
        accessible = null,
        cancelled = false,
        tripId = "t$from",
    )

    // Walk 0-5, ride 10-30, walk 30-35, ride 40-60, walk 60-65.
    private val first = ride(10, 30)
    private val second = ride(40, 60)
    private val legs = listOf(walk(0, 5), first, walk(30, 35), second, walk(60, 65))

    @Test
    fun `a journey more than an hour off is shown whole`() {
        assertNull(legs.focusAt(at(-61)))
    }

    @Test
    fun `within the hour it's the walk to the first ride and the ride`() {
        val focus = legs.focusAt(at(-59))
        assertEquals(legs.subList(0, 2), focus?.legs)
        assertEquals(first, focus?.ride)
    }

    @Test
    fun `waiting at the stop it's the ride`() {
        assertEquals(listOf<Leg>(first), legs.focusAt(at(7))?.legs)
    }

    @Test
    fun `on a ride it's that ride`() {
        assertEquals(listOf<Leg>(first), legs.focusAt(at(20))?.legs)
    }

    @Test
    fun `in a transfer it's the walk and the next ride`() {
        val focus = legs.focusAt(at(32))
        assertEquals(legs.subList(2, 4), focus?.legs)
        assertEquals(second, focus?.ride)
    }

    @Test
    fun `the last walk has no ride`() {
        val focus = legs.focusAt(at(62))
        assertEquals(legs.subList(4, 5), focus?.legs)
        assertNull(focus?.ride)
    }

    @Test
    fun `after arriving it's shown whole`() {
        assertNull(legs.focusAt(at(65)))
    }

    @Test
    fun `a single ride is still the ride to watch`() {
        val focus = listOf<Leg>(first).focusAt(at(5))
        assertEquals(listOf<Leg>(first), focus?.legs)
        assertEquals(first, focus?.ride)
    }
}
