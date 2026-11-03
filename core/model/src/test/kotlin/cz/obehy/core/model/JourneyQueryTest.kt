package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyQueryTest {
    private fun stop(name: String) = Place(name, name, null, PlaceKind.Stop, 0.0, 0.0)

    @Test
    fun `reversing swaps the ends and walks the vias backwards`() {
        val query = JourneyQuery(stop("A"), stop("D"), listOf(stop("B"), stop("C")), arriveBy = true)

        val back = query.reversed()

        assertEquals("D", back.from?.name)
        assertEquals("A", back.to?.name)
        assertEquals(listOf("C", "B"), back.vias.map { it.name })
        assertTrue(back.arriveBy)
        assertEquals(query, back.reversed())
    }

    @Test
    fun `stops route by id, everything else by coordinates`() {
        assertEquals("cz-PID_U689S1", stop("cz-PID_U689S1").routingRef)
        assertEquals("50.1,14.4", Place("x", "Doma", null, PlaceKind.Address, 50.1, 14.4).routingRef)
    }

    @Test
    fun `all transport types allowed means no restriction`() {
        assertNull(SearchOptions().allowedModes)
        assertEquals(0, SearchOptions().changedCount)
    }

    @Test
    fun `leaving a type out keeps ferries and funiculars`() {
        val options = SearchOptions(types = TransportType.entries.toSet() - TransportType.LongDistanceRail)

        val modes = options.allowedModes!!

        assertTrue(Mode.Ferry in modes && Mode.Funicular in modes && Mode.Bus in modes)
        assertTrue(Mode.LongDistanceRail !in modes && Mode.HighSpeedRail !in modes)
        assertEquals(1, options.changedCount)
    }

    @Test
    fun `low floor has its own chip and does not count towards the options badge`() {
        assertEquals(1, SearchOptions(lowFloor = true, routedTransfers = true).changedCount)
    }
}
