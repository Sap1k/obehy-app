package cz.obehy.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripNotesTest {
    private fun feature(
        text: String,
        system: String? = null,
        code: String? = null,
        kinds: Set<String> = emptySet(),
        scope: String = "trip",
        noteKind: String? = null,
        noteCode: String? = null,
        noteText: String? = null,
    ) = TripFeature(kinds, "note", text, scope, system, code, noteKind, noteCode, noteText)

    @Test
    fun `a central note and its code are one note`() {
        val notes =
            tripNotes(
                listOf(
                    feature("Quiet coach", "czptt_note", "7"),
                    feature("Quiet coach again", noteKind = "czptt_central_note", noteCode = "7", noteText = "tichý"),
                ),
            )

        assertEquals(listOf("Quiet coach"), notes.marked.map { it.text })
        assertTrue(notes.long.isEmpty())
    }

    @Test
    fun `a sign the API has no icon for gets its own`() {
        val notes = tripNotes(listOf(feature("Wi-Fi", "czptt_note", "40"), feature("Odd", "czptt_note", "99")))

        assertEquals(listOf("wifi", "note"), notes.marked.map { it.icon })
    }

    @Test
    fun `when it runs comes apart from the marked codes`() {
        val notes =
            tripNotes(
                listOf(
                    feature("Weekdays", "jdf", "X", kinds = setOf("calendar_designation")),
                    feature("Bikes", "jdf", "B"),
                ),
            )

        assertEquals(listOf("Weekdays"), notes.operates.map { it.text })
        assertEquals(listOf("Bikes"), notes.marked.map { it.text })
    }

    @Test
    fun `a stopping code of single stops is the stop's flag, not a note`() {
        val atStop = feature("Request stop", "jdf", "R", kinds = setOf("request_stop"), scope = "call")

        assertTrue(tripNotes(listOf(atStop)).isEmpty)
    }

    @Test
    fun `free text is listed once and never repeats when it runs`() {
        val notes =
            tripNotes(
                listOf(
                    feature("Weekdays", "jdf", "X", kinds = setOf("calendar_designation")),
                    feature("", noteKind = "free", noteText = "Long words"),
                    feature("", noteKind = "free", noteText = "Long words"),
                    feature("", noteKind = "free", noteText = "Weekdays"),
                ),
            )

        assertEquals(listOf("Long words"), notes.long)
    }
}
