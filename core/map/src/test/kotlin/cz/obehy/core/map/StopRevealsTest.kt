package cz.obehy.core.map

import cz.obehy.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopRevealsTest {
    /** [count] stops along a street, about 400 m apart. */
    private fun street(count: Int) = List(count) { LatLon(50.0, 14.0 + it * 0.0056) }

    @Test
    fun `every stop shows by street level`() {
        assertTrue(stopReveals(street(40), emptyList()).all { it <= 14 })
    }

    @Test
    fun `fewer stops show the further out`() {
        val reveals = stopReveals(street(40), emptyList())
        val shown = (0..14).map { zoom -> reveals.count { it <= zoom } }
        assertEquals(shown.sorted(), shown)
        assertTrue(shown[9] < shown[14])
    }

    @Test
    fun `those showing stay apart at any zoom`() {
        val stops = street(40)
        val reveals = stopReveals(stops, emptyList())
        for (zoom in 9..13) {
            val shown = stops.filterIndexed { index, _ -> reveals[index] <= zoom }
            val gaps = shown.zipWithNext { a, b -> a.metresTo(b) }
            val metresPerDp = 40_075_016 * 0.643 / (512 * Math.pow(2.0, zoom.toDouble()))
            assertTrue("zoom $zoom", gaps.all { it / metresPerDp >= 13 })
        }
    }

    @Test
    fun `a stop beside one always shown waits for the zoom to part them`() {
        val fixed = LatLon(50.0, 14.0)
        val beside = LatLon(50.0, 14.0005)
        val far = LatLon(50.0, 15.0)
        val (near, away) = stopReveals(listOf(beside, far), listOf(fixed)).let { it[0] to it[1] }
        assertTrue(near > away)
    }

    @Test
    fun `of two stops in one place, the second waits for street level`() {
        val here = LatLon(50.0, 14.0)
        assertEquals(listOf(0, 14), stopReveals(listOf(here, here), emptyList()))
    }
}
