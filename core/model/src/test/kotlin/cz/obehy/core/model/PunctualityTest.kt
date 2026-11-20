package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PunctualityTest {
    @Test
    fun `the bands are the Oběhy API's`() {
        assertEquals(
            listOf(Punctuality.Early, Punctuality.OnTime, Punctuality.OnTime, Punctuality.Late, Punctuality.VeryLate),
            listOf(-2L, -1L, 2L, 3L, 10L).map(Punctuality::of),
        )
        assertFalse(Punctuality.isLate(2))
        assertTrue(Punctuality.isLate(3))
        assertFalse(Punctuality.isLate(null))
    }

    @Test
    fun `a delay is the shown minutes, each time cut to its minute`() {
        val scheduled = Instant.parse("2026-11-20T07:00:00Z")
        assertEquals(2L, TimePoint(scheduled, Instant.parse("2026-11-20T07:02:59Z"), realtime = true).delayMinutes)
        assertEquals(
            -1L,
            TimePoint(Instant.parse("2026-11-20T07:00:30Z"), Instant.parse("2026-11-20T06:59:59Z"), true).delayMinutes,
        )
    }
}
