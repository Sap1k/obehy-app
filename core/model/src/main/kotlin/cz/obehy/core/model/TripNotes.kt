package cz.obehy.core.model

/** A note with its [icon], one of the API's names. */
data class MarkedNote(
    val text: String,
    val icon: String,
)

/** A trip's notes for reading: when it runs, the marked codes, and the longer texts. */
data class TripNotes(
    val operates: List<MarkedNote> = emptyList(),
    val marked: List<MarkedNote> = emptyList(),
    val long: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = operates.isEmpty() && marked.isEmpty() && long.isEmpty()
}

/** The icon of a timetable sign, for the codes the API has no icon of its own for. */
private val SIGN_ICONS =
    buildMap {
        fun sign(
            system: String,
            icon: String,
            vararg codes: String,
        ) = codes.forEach { put("$system:$it", icon) }

        sign("czptt_note", "class", "10", "11", "12")
        sign("czptt_note", "sleeper", "13")
        sign("czptt_note", "couchette", "14")
        sign("czptt_note", "coach", "15")
        sign("czptt_note", "car", "16")
        sign("czptt_note", "wheelchair", "17")
        sign("czptt_note", "restaurant", "18")
        sign("czptt_note", "reservation", "19")
        sign("czptt_note", "reservation_required", "20")
        sign("czptt_note", "luggage", "21", "26", "27")
        sign("czptt_note", "bicycle", "22")
        sign("czptt_note", "interchange", "23")
        sign("czptt_note", "refreshments", "24", "25")
        sign("czptt_note", "bicycle_reservation", "28", "29")
        sign("czptt_note", "power_socket", "30")
        sign("czptt_note", "bus", "32")
        sign("czptt_note", "family", "33")
        sign("czptt_note", "wheelchair_booking", "34")
        sign("czptt_note", "ticket", "35")
        sign("czptt_note", "bicycle_none", "36")
        sign("czptt_note", "steam", "37")
        sign("czptt_note", "ladies", "38")
        sign("czptt_note", "quiet", "39")
        sign("czptt_note", "wifi", "40")
        sign("czptt_note", "portal", "41")
        sign("czptt_note", "cinema", "42")
        sign("czptt_note", "no_discount", "43")
        sign("czptt_note", "integrated", "44")
        sign("czptt_note", "queue", "45")
        sign("czptt_note", "no_toilet", "46")
        sign("jdf", "reservation", "R")
        sign("jdf", "reservation_required", "#")
        sign("jdf", "wheelchair", "@")
        sign("jdf", "refreshments", "%")
        sign("jdf", "wheelchair_partly", "{")
        sign("jdf", "luggage", "[")
        sign("jdf", "bicycle", "O")
        sign("jdf", "on_request", "T")
        sign("jdf", "conditional", "!")
        sign("jdf", "diversion", "<")
        sign("jdf", "integrated", "I")
        sign("jdf", "ticket", "s")
    }

/** The icon of a timetable sign: the API's [icon], unless it's the catch-all and the sign says more. */
fun signIcon(
    key: String,
    icon: String,
): String = if (icon == "note") SIGN_ICONS[key] ?: icon else icon
