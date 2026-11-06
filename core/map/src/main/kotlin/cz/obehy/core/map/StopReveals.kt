package cz.obehy.core.map

import cz.obehy.core.model.LatLon
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log2

/** How far apart, in dp, stops have to be on the map for both to be worth showing. */
private const val STOP_GAP_DP = 14.0

/** Every stop shows from here, however close together: at this zoom a street's stops are apart. */
private const val ALL_STOPS_ZOOM = 14

private const val EARTH_CIRCUMFERENCE_M = 40_075_016.0

/** MapLibre's world is 512 dp wide at zoom 0. */
private const val WORLD_DP = 512.0

/**
 * For each of [stops], in order along a route, the whole zoom level from which it's shown, so that
 * those showing at any zoom stay at least [STOP_GAP_DP] apart and away from the always-shown
 * [fixed] stops. Whole, because a map only evaluates zoom in a filter at whole levels. Stops are
 * placed middle first, then the middles of what's left, so those showing are spread evenly along
 * the route rather than bunched at its start.
 */
internal fun stopReveals(
    stops: List<LatLon>,
    fixed: List<LatLon>,
): List<Int> {
    val reveals = MutableList(stops.size) { ALL_STOPS_ZOOM }
    val placed = fixed.toMutableList()
    for (index in stops.indices.bisected()) {
        val stop = stops[index]
        val nearest = placed.minOfOrNull { it.metresTo(stop) }
        reveals[index] = if (nearest == null) 0 else zoomToSeparate(nearest, stop.lat)
        placed += stop
    }
    return reveals
}

/** The first whole zoom at which [metres] on the ground at [lat] are [STOP_GAP_DP] on the map. */
private fun zoomToSeparate(
    metres: Double,
    lat: Double,
): Int {
    if (metres <= 0) return ALL_STOPS_ZOOM
    val metresAtZoomZero = EARTH_CIRCUMFERENCE_M * cos(lat * PI / 180) / WORLD_DP
    return ceil(log2(STOP_GAP_DP * metresAtZoomZero / metres)).toInt().coerceIn(0, ALL_STOPS_ZOOM)
}

/** These indices middle first, then the middle of each half, and so on, breadth first. */
private fun IntRange.bisected(): List<Int> {
    val spans = ArrayDeque(listOf(this))
    return buildList {
        while (spans.isNotEmpty()) {
            val span = spans.removeFirst()
            if (span.isEmpty()) continue
            val middle = (span.first + span.last) / 2
            add(middle)
            spans += span.first until middle
            spans += middle + 1..span.last
        }
    }
}
