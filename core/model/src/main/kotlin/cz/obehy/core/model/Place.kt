package cz.obehy.core.model

import kotlinx.serialization.Serializable

/** What a found place is: a stop routes by its ID, anything else by its coordinates. */
enum class PlaceKind { Stop, Address, Poi, MyLocation }

/** A place to travel from, to or through, as the place search found it. */
@Serializable
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

    val position: LatLon get() = LatLon(lat, lon)

    companion object {
        /**
         * Wherever the phone is. Its coordinates are unknown (NaN) until a search asks for a fix
         * and fills them in, and the name comes from the screen showing it.
         */
        fun myLocation(fix: LatLon? = null): Place =
            Place("my-location", "", null, PlaceKind.MyLocation, fix?.lat ?: Double.NaN, fix?.lon ?: Double.NaN)
    }
}

/** A stop near the user, [metres] away in a straight line. */
data class NearbyStop(
    val place: Place,
    val metres: Int,
)
