package cz.obehy.core.model

/** A journey the user will probably want now, and the past [searches] that say so. */
data class Suggestion(
    val from: Place,
    val to: Place,
    val searches: Set<Long>,
)
