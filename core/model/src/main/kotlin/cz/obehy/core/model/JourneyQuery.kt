package cz.obehy.core.model

import java.time.Instant

/** The user's search options, remembered between searches. */
data class SearchOptions(
    /** Only low-floor (wheelchair-accessible) vehicles and step-free walks. */
    val lowFloor: Boolean = false,
    /** Walk transfers along real paths instead of estimated straight lines. */
    val routedTransfers: Boolean = false,
    val types: Set<TransportType> = TransportType.entries.toSet(),
) {
    /** Transport types left out of the search; null while every type is allowed. */
    val allowedModes: Set<Mode>?
        get() =
            if (types.containsAll(TransportType.entries)) {
                null
            } else {
                types.flatMapTo(mutableSetOf()) { it.modes } + TransportType.alwaysAllowed
            }

    /** How many options differ from the defaults, for the badge on the options button. */
    val changedCount: Int
        get() = (if (routedTransfers) 1 else 0) + (TransportType.entries.size - types.size)
}

/** Where and when to travel. [time] null means now. */
data class JourneyQuery(
    val from: Place? = null,
    val to: Place? = null,
    val vias: List<Place> = emptyList(),
    val time: Instant? = null,
    val arriveBy: Boolean = false,
) {
    val isComplete: Boolean get() = from != null && to != null

    /** The way back: the ends swapped and the stops in between in reverse. */
    fun reversed(): JourneyQuery = copy(from = to, to = from, vias = vias.reversed())

    companion object {
        /** Stops in between a search can go through (the routing engine's limit). */
        const val MAX_VIAS = 2
    }
}
