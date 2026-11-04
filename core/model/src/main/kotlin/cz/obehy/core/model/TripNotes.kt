package cz.obehy.core.model

/** One code or note as the timetable states it for a trip. */
data class TripFeature(
    val kinds: Set<String>,
    val icon: String,
    val text: String,
    val scope: String,
    /** The code's system (`jdf`, `czptt_note`, `czptt_activity`) and the code, when it is one. */
    val system: String? = null,
    val code: String? = null,
    val noteKind: String? = null,
    val noteCode: String? = null,
    val noteText: String? = null,
)

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

private val MARKED_SYSTEMS = setOf("jdf", "czptt_note")

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

/** The API's own icon, unless it's the catch-all and the sign says more. */
private fun TripFeature.iconFor(key: String): String = if (icon == "note") SIGN_ICONS[key] ?: icon else icon

/** What a stop's own row already shows. */
private val CALL_FLAGS =
    setOf(
        "request_stop",
        "exit_only",
        "boarding_only",
        "not_stopping",
        "border_control_only",
        "on_request",
        "conditional",
    )

/**
 * [features] grouped as the web's `noteGroups` does. Codes repeat per range of stops and as plain
 * notes, so each is listed once.
 */
fun tripNotes(features: List<TripFeature>): TripNotes {
    val operates = linkedMapOf<String, MarkedNote>()
    val marked = linkedMapOf<String, MarkedNote>()
    val long = linkedSetOf<String>()
    for (feature in features) {
        val key = feature.system?.let { "$it:${feature.code}" }
        val atCalls = feature.scope == "call" || feature.scope == "call_range"
        when {
            key != null && "calendar_designation" in feature.kinds -> {
                operates.getOrPut(key) { MarkedNote(feature.text, feature.icon) }
            }

            key != null && feature.system in MARKED_SYSTEMS -> {
                // A stopping code of single stops is that stop's flag, not a note.
                if (!atCalls || !CALL_FLAGS.containsAll(feature.kinds)) {
                    marked.getOrPut(key) { MarkedNote(feature.text, feature.iconFor(key)) }
                }
            }

            feature.noteKind == "czptt_central_note" && feature.noteCode != null -> {
                // A central note of some stops comes as the note alone: its code is the key.
                val code = "czptt_note:${feature.noteCode}"
                feature.noteText?.let { text -> marked.getOrPut(code) { MarkedNote(text, feature.iconFor(code)) } }
            }

            key == null -> {
                feature.noteText?.let { long += it }
            }
        }
    }
    val days = operates.values.toList()
    val dayTexts = days.mapTo(mutableSetOf()) { it.text }
    return TripNotes(days, marked.values.toList(), long.filterNot { it in dayTexts })
}
