package cz.obehy.core.map

import cz.obehy.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ApproachFramingTest {
    private val stop = LatLon(50.0, 14.0)

    /** About [km] kilometres north of the stop. */
    private fun north(km: Double) = LatLon(50.0 + km / 111, 14.0)

    private val focus = MapFraming.Focus(listOf(stop, LatLon(50.0, 14.02)))

    @Test
    fun `a nearby vehicle joins the frame`() {
        assertEquals(MapFraming.Focus(focus.points + north(5.0)), focus.takingIn(north(5.0), stop))
    }

    @Test
    fun `a far one doesn't`() {
        assertSame(focus, focus.takingIn(north(30.0), stop))
    }

    @Test
    fun `on a long ride further off is still near`() {
        val long = MapFraming.Focus(listOf(stop, north(50.0)))

        assertEquals(MapFraming.Focus(long.points + north(-30.0)), long.takingIn(north(-30.0), stop))
    }

    @Test
    fun `the whole journey stays the whole journey`() {
        assertSame(MapFraming.Whole, MapFraming.Whole.takingIn(north(1.0), stop))
    }

    @Test
    fun `no vehicle changes nothing`() {
        assertSame(focus, focus.takingIn(null, stop))
    }

    @Test
    fun `the place holds while the vehicle comes a little closer`() {
        assertEquals(north(6.0), heldVehiclePlace(north(6.0), north(4.0), stop))
    }

    @Test
    fun `the place moves once the vehicle is much closer`() {
        assertEquals(north(2.0), heldVehiclePlace(north(6.0), north(2.0), stop))
    }

    @Test
    fun `the place moves if the vehicle turns out further away`() {
        assertEquals(north(9.0), heldVehiclePlace(north(6.0), north(9.0), stop))
    }

    @Test
    fun `the first report sets the place, and no vehicle clears it`() {
        assertEquals(north(6.0), heldVehiclePlace(null, north(6.0), stop))
        assertEquals(null, heldVehiclePlace(north(6.0), null, stop))
    }
}
