package cz.obehy.core.data

import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Mode
import cz.obehy.core.model.NearbyStop
import cz.obehy.core.network.MotisJson
import cz.obehy.core.network.motis.MotisStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyStopsTest {
    private fun fixture(name: String): List<MotisStop> {
        val text = requireNotNull(javaClass.getResource("/motis/$name.json")) { "no fixture $name" }.readText()
        return MotisJson.decodeFromString(text)
    }

    private fun List<NearbyStop>.byName(part: String) = single { part in it.place.name }

    @Test
    fun `a station listed by thirteen feeds is one row, routed through the Czech one`() {
        val hlavni = LatLon(50.0831, 14.4353)

        val stops = groupNearbyStops(fixture("map-stops-praha-transitous"), hlavni)

        val station = stops.filter { "Praha hl" in it.place.name }
        assertEquals(station.map { it.place.id }.toString(), 1, station.size)
        assertTrue(
            station.single().place.id,
            station
                .single()
                .place.id
                .startsWith("cz-CZPTT_"),
        )
        assertTrue(Mode.RegionalRail in station.single().place.modes)
    }

    @Test
    fun `platforms of a stop without a parent become one row, nearest first`() {
        val stops =
            listOf(
                MotisStop("Muzeum", "cz-PID_U400Z1P", lat = 50.0800, lon = 14.4300, modes = listOf("SUBWAY")),
                MotisStop("Muzeum", "cz-PID_U400Z2P", lat = 50.0801, lon = 14.4301, modes = listOf("TRAM")),
                MotisStop("Bruselská", "cz-PID_U900Z1", lat = 50.0760, lon = 14.4300, modes = listOf("BUS")),
            )

        val nearby = groupNearbyStops(stops, LatLon(50.0770, 14.4300))

        assertEquals(listOf("Bruselská", "Muzeum"), nearby.map { it.place.name })
        assertEquals(setOf(Mode.Subway, Mode.Tram), nearby.last().place.modes)
        assertEquals(110, nearby.first().metres)
    }

    @Test
    fun `a stop with a parent routes from the parent`() {
        val nearby = groupNearbyStops(fixture("map-stops-rural-obehy"), LatLon(49.985, 15.175))

        assertTrue(nearby.isNotEmpty())
        assertEquals(nearby.size, nearby.map { it.place.id }.toSet().size)
        assertTrue(nearby.none { ":est:" in it.place.id || ":unspecified" in it.place.id })
        assertEquals(nearby.sortedBy { it.metres }, nearby)
    }

    @Test
    fun `rounding keeps a position within about a hundred metres`() {
        val at = LatLon(50.08314, 14.43586)

        assertEquals(LatLon(50.083, 14.436), at.rounded())
        assertTrue(at.metresTo(at.rounded()) < 100)
    }
}
