package cz.obehy.core.data

import cz.obehy.core.model.LatLon
import cz.obehy.core.model.LiveVehicle
import cz.obehy.core.model.MapBounds
import cz.obehy.core.model.Mode
import cz.obehy.core.model.VehiclePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// The web's grid.test.ts and queries.test.ts cases, so both clients ask for the same cells.
class VehicleGridTest {
    @Test
    fun `covers a city with the whole-degree cells it touches`() {
        val prague = MapBounds(14.2, 49.9, 14.8, 50.2)

        assertEquals(listOf("14,49,15,50", "14,50,15,51"), cellsFor(prague).map { it.key })
    }

    @Test
    fun `covers the whole area in at most 36 cells, clamped to it`() {
        val cells = cellsFor(MapBounds(0.0, 40.0, 30.0, 60.0))

        assertEquals(9 * 4, cells.size)
        assertEquals("11,48,12,49", cells.first().key)
        assertEquals("19,51,20,52", cells.last().key)
        assertEquals(emptyList<VehicleCell>(), cellsFor(MapBounds(0.0, 0.0, 5.0, 5.0)))
    }

    @Test
    fun `a viewport ending on a degree takes no extra cell`() {
        assertEquals(listOf("14,50,15,51"), cellsFor(MapBounds(14.0, 50.0, 15.0, 51.0)).map { it.key })
    }

    @Test
    fun `a split cell is replaced by the quarters inside the view, printed exactly`() {
        val split = setOf("14,50,15,51", "14,50,14.5,50.5")

        val keys = cellsFor(MapBounds(14.1, 50.1, 14.4, 50.4)) { it in split }.map { it.key }

        assertEquals(
            listOf("14,50,14.25,50.25", "14.25,50,14.5,50.25", "14,50.25,14.25,50.5", "14.25,50.25,14.5,50.5"),
            keys,
        )
    }

    @Test
    fun `splitting stops at a quarter degree`() {
        val smallest =
            cellsFor(MapBounds(14.1, 50.1, 14.2, 50.2)) { it in setOf("14,50,15,51", "14,50,14.5,50.5") }.first()

        assertTrue(smallest.quarters().isEmpty())
    }

    private fun vehicle(
        id: String,
        fixed: String,
        lon: Double = 15.0,
        delay: Long? = null,
    ) = LiveVehicle(
        id = id,
        at = VehiclePosition(LatLon(50.0, lon), seenAt = Instant.parse(fixed)),
        mode = Mode.Bus,
        line = "1",
        color = null,
        textColor = null,
        headsign = null,
        delayMinutes = delay,
        late = false,
        trip = null,
    )

    @Test
    fun `one row per vehicle across cells, the newer fix`() {
        val merged =
            mergeVehicles(
                listOf(
                    listOf(vehicle("a", "2026-10-22T14:00:00Z", 15.0), vehicle("b", "2026-10-22T14:00:00Z")),
                    listOf(vehicle("a", "2026-10-22T14:00:10Z", 15.01)),
                ),
            )

        assertEquals(listOf("a" to 15.01, "b" to 15.0), merged.map { it.id to it.at.position.lon })
    }

    @Test
    fun `a held vehicle is never drawn back to an older fix`() {
        val held = listOf(vehicle("a", "2026-10-22T14:00:10Z", 15.01))

        val again = mergeVehicles(listOf(listOf(vehicle("a", "2026-10-22T14:00:00Z", 15.0, delay = 1))), held)

        assertEquals(listOf(15.01 to 1L), again.map { it.at.position.lon to it.delayMinutes })
        val next = mergeVehicles(listOf(listOf(vehicle("a", "2026-10-22T14:00:20Z", 15.02))), again)
        assertEquals(listOf(15.02), next.map { it.at.position.lon })
    }

    @Test
    fun `a held vehicle no answer has any more is dropped`() {
        assertTrue(mergeVehicles(emptyList(), listOf(vehicle("a", "2026-10-22T14:00:00Z"))).isEmpty())
    }
}
