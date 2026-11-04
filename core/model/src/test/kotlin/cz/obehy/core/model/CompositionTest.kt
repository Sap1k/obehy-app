package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionTest {
    private val start = CompositionCall(1, "Praha")
    private val middle = CompositionCall(5, "Kolín")
    private val end = CompositionCall(9, "Brno")

    private fun unit(
        kind: UnitKind = UnitKind.Coach,
        number: String? = null,
        classes: List<Int> = emptyList(),
        cab: String? = null,
        from: CompositionCall = start,
        to: CompositionCall = end,
    ) = CompositionUnit(kind, null, number, classes, emptyList(), null, from, to, cab = cab)

    private fun composition(
        units: List<CompositionUnit>,
        vararg segments: CompositionSegment,
    ) = Composition(CompositionStatus.Fresh, null, units, segments.toList(), listOf("ČD"))

    @Test
    fun `a stop between two segments is in the later one, and one outside them in the nearest`() {
        val two =
            composition(
                listOf(unit()),
                CompositionSegment(start, middle, listOf(0)),
                CompositionSegment(middle, end, listOf(0)),
            )

        assertEquals(0, two.segmentAt(null))
        assertEquals(0, two.segmentAt(4))
        assertEquals(1, two.segmentAt(5))
        assertEquals(0, two.segmentAt(0))
        assertEquals(1, two.segmentAt(20))
        assertEquals(-1, composition(emptyList()).segmentAt(3))
    }

    @Test
    fun `segments running the same train are one stretch, even reversed`() {
        val a = unit(number = "1")
        val b = unit(number = "2")
        val same =
            composition(
                listOf(a, b),
                CompositionSegment(start, middle, listOf(0, 1)),
                CompositionSegment(middle, end, listOf(1, 0)),
            )
        val different =
            composition(
                listOf(a, b),
                CompositionSegment(start, middle, listOf(0, 1)),
                CompositionSegment(middle, end, listOf(0)),
            )

        assertEquals(listOf(listOf(0, 1)), same.stretches().map { it.segments })
        assertEquals(end, same.stretches().single().toCall)
        assertEquals(listOf(listOf(0), listOf(1)), different.stretches().map { it.segments })
    }

    @Test
    fun `a unit joining partway only runs part of its segment`() {
        val segment = CompositionSegment(start, end, listOf(0))

        assertFalse(runsPartOf(unit(), segment))
        assertTrue(runsPartOf(unit(from = middle), segment))
    }

    @Test
    fun `classes read as one label`() {
        assertEquals("1/2", unit(classes = listOf(2, 1)).classLabel)
        assertNull(unit().classLabel)
    }

    @Test
    fun `a locomotive has its cab at its end of the train`() {
        val ends = cabEnds(listOf(unit(UnitKind.Locomotive), unit(), unit(UnitKind.Locomotive)))

        assertEquals(CabEnds(front = true, rear = false), ends[0])
        assertEquals(CabEnds(front = false, rear = false), ends[1])
        assertEquals(CabEnds(front = false, rear = true), ends[2])
    }

    @Test
    fun `a multiple unit opens with one cab and closes with the next`() {
        val ends =
            cabEnds(
                listOf(
                    unit(UnitKind.MultipleUnit, cab = "true"),
                    unit(UnitKind.MultipleUnit),
                    unit(UnitKind.MultipleUnit, cab = "true"),
                ),
            )

        assertEquals(
            listOf(
                CabEnds(front = true, rear = false),
                CabEnds(front = false, rear = false),
                CabEnds(front = false, rear = true),
            ),
            ends,
        )
    }

    @Test
    fun `a control car faces away from its locomotive`() {
        val ends = cabEnds(listOf(unit(cab = "true"), unit(), unit(UnitKind.Locomotive)))

        assertEquals(CabEnds(front = true, rear = false), ends.first())
    }

    @Test
    fun `a cab side the source names is taken as it is`() {
        assertEquals(CabEnds(front = false, rear = true), cabEnds(listOf(unit(cab = "rear"))).single())
        assertEquals(CabEnds(front = true, rear = true), cabEnds(listOf(unit(cab = "both"))).single())
    }
}
