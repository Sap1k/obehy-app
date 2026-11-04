package cz.obehy.core.data

import cz.obehy.core.model.Mode
import cz.obehy.core.network.motis.MotisMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceRepositoryTest {
    private fun stop(
        id: String,
        name: String,
        lat: Double = 50.0831,
        lon: Double = 14.4353,
        modes: List<String> = listOf("LONG_DISTANCE"),
    ) = MotisMatch("STOP", name, id, lat, lon, country = "CZ", modes = modes)

    @Test
    fun `one station listed by several feeds becomes one, from its own country's feed`() {
        val places =
            mergeDuplicateStops(
                listOf(
                    stop("at-Railway_cz:0:5457076", "Praha hl.n.", modes = listOf("HIGHSPEED_RAIL")),
                    stop("ch-swiss_Parent5457076", "Praha hl. n.", lat = 50.0835),
                    stop("cz-CZPTT_czptt:stop:CZ:57076", "Praha, hlavní nádraží", modes = listOf("REGIONAL_RAIL")),
                    stop("cz-PID_U142S1", "Hlavní nádraží", modes = listOf("SUBWAY")),
                ),
            )

        assertEquals(listOf("cz-CZPTT_czptt:stop:CZ:57076", "cz-PID_U142S1"), places.map { it.id })
        assertEquals(setOf(Mode.HighSpeedRail, Mode.LongDistanceRail, Mode.RegionalRail), places.first().modes)
    }

    @Test
    fun `same-named stops in different towns stay apart`() {
        val places =
            mergeDuplicateStops(
                listOf(
                    stop("cz-CZPTT_1", "Nádraží", lat = 50.08),
                    stop("cz-CZPTT_2", "Nádraží", lat = 49.19),
                ),
            )

        assertEquals(2, places.size)
    }

    @Test
    fun `addresses without an id still get one each, as the results list needs`() {
        val places =
            mergeDuplicateStops(
                listOf(
                    MotisMatch("ADDRESS", "Husova 1", "", 49.95, 14.07),
                    MotisMatch("ADDRESS", "Husova 1", "", 49.39, 15.58),
                    MotisMatch("ADDRESS", "Husova 1", "", 49.39, 15.58),
                ),
            )

        assertEquals(listOf("49.95,14.07", "49.39,15.58"), places.map { it.id })
    }
}
