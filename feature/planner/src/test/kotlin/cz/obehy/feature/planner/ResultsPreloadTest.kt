package cz.obehy.feature.planner

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsPreloadTest {
    @Test
    fun `what is on screen comes first, then below, then above`() {
        assertEquals(listOf(3, 4, 5, 6, 7, 2), resultsToPrepare(20, listOf(3, 4, 5)))
    }

    @Test
    fun `the top of the list has nothing above it`() {
        assertEquals(listOf(0, 1, 2, 3), resultsToPrepare(20, listOf(0, 1)))
    }

    @Test
    fun `the end of the list has nothing below it`() {
        assertEquals(listOf(8, 9, 7), resultsToPrepare(10, listOf(8, 9)))
    }

    @Test
    fun `with no result in view the first ones are next`() {
        assertEquals(listOf(0, 1), resultsToPrepare(10, emptyList()))
    }

    @Test
    fun `a tall screen asks for no more than the map keeps`() {
        assertEquals(6, resultsToPrepare(30, (0..9).toList()).size)
    }

    @Test
    fun `no results, nothing to prepare`() {
        assertEquals(emptyList<Int>(), resultsToPrepare(0, emptyList()))
    }

    @Test
    fun `a stale position past the end is dropped`() {
        assertEquals(listOf(1, 0), resultsToPrepare(2, listOf(5, 1)))
    }
}
