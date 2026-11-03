package cz.obehy.core.model

/** What a found place is: a stop routes by its ID, anything else by its coordinates. */
enum class PlaceKind { Stop, Address, Poi }

/** A place to travel from, to or through, as the place search found it. */
data class Place(
    val id: String,
    val name: String,
    /** The district or town it is in ("Praha 8"), when that adds something to [name]. */
    val area: String?,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
    /** For stops: what serves it. */
    val modes: Set<Mode> = emptySet(),
) {
    /** How a journey search names this place: the stop's ID, otherwise `lat,lon`. */
    val routingRef: String get() = if (kind == PlaceKind.Stop) id else "$lat,$lon"
}
