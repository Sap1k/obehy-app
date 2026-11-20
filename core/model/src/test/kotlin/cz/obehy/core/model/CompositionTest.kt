package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Stretches, cab ends, where the rider is and partial vehicles are the Oběhy API's
// (`api/formations.py`, tested in `tests/unit/test_api_formations.py`); here only what the app
// reads of them.
class CompositionTest {
    private val start = CompositionCall(1, "Praha")
    private val end = CompositionCall(9, "Brno")

    private fun unit(
        number: String? = null,
        classes: List<Int> = emptyList(),
    ) = CompositionUnit(UnitKind.Coach, null, number, classes, emptyList(), null, start, end)

    @Test
    fun `a segment's units are its vehicles in train order`() {
        val cab = CabEnds(front = false, rear = false)
        val segment =
            CompositionSegment(start, end, listOf(SegmentVehicle(1, cab, false), SegmentVehicle(0, cab, true)))
        val composition =
            Composition(
                CompositionStatus.Fresh,
                null,
                listOf(unit("1"), unit("2")),
                listOf(segment),
                listOf(CompositionStretch(start, end, listOf(0))),
                hereSegment = 0,
                hereStretch = 0,
                sources = listOf("ČD"),
            )

        assertEquals(listOf("2", "1"), composition.unitsOf(segment).map { it.coachNumber })
    }

    @Test
    fun `classes read as one label`() {
        assertEquals("1/2", unit(classes = listOf(2, 1)).classLabel)
        assertNull(unit().classLabel)
    }
}
