package cz.obehy.core.map

import cz.obehy.core.model.Leg
import cz.obehy.core.model.RouteSurroundings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StyleKeyTest {
    private val look = MapLook(dark = false, primary = "#000000", walk = "#888888")
    private val legs = emptyList<Leg>()

    @Test
    fun `the same legs with surroundings arriving later are another style`() {
        assertNotEquals(StyleKey(look, legs, null), StyleKey(look, legs, RouteSurroundings()))
    }

    @Test
    fun `surroundings count by which object they are`() {
        val around = RouteSurroundings()

        assertEquals(StyleKey(look, legs, around), StyleKey(look, legs, around))
        assertNotEquals(StyleKey(look, legs, around), StyleKey(look, legs, RouteSurroundings()))
    }
}
