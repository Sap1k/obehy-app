package cz.obehy.core.data

import cz.obehy.core.model.AppMode
import cz.obehy.core.model.LiveMapSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveMapAvailabilityTest {
    private val transitous = "https://api.transitous.org/"

    @Test
    fun `the modes with Oběhy always have Oběhy's map`() {
        assertEquals(LiveMapSource.Obehy, sourceOf(AppMode.Obehy, "https://motis.obehy.cz/", emptySet()))
        assertEquals(LiveMapSource.Obehy, sourceOf(AppMode.TransitousEnriched, transitous, emptySet()))
    }

    @Test
    fun `without Oběhy, a map only where the server answered railviz`() {
        assertEquals(LiveMapSource.Railviz, sourceOf(AppMode.MotisOnly, transitous, setOf(transitous)))
        assertEquals(LiveMapSource.None, sourceOf(AppMode.MotisOnly, "https://own.example/", setOf(transitous)))
    }

    @Test
    fun `no map before a mode is picked`() {
        assertEquals(LiveMapSource.None, sourceOf(null, transitous, setOf(transitous)))
    }
}
