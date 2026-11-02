package cz.obehy.core.model

/**
 * What Oběhy adds to one transit leg of a MOTIS itinerary. A leg is always usable from MOTIS
 * data alone; enrichment only ever adds to it.
 */
sealed interface LegEnrichment {
    /** Asked, not answered yet. */
    data object Pending : LegEnrichment

    /** Oběhy knows the trip: `/api/v1/trips/{tripId}/{serviceDate}`. */
    data class Resolved(val tripId: String, val serviceDate: String) : LegEnrichment

    /**
     * Oběhy has nothing for this leg (a foreign trip, a feed without keys, or no answer): the
     * leg shows MOTIS data with a quiet note.
     */
    data class Unavailable(val reason: Reason) : LegEnrichment

    enum class Reason { NotCovered, NotFound, Failed }
}
