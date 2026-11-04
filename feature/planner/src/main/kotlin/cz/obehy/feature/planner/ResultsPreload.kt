package cz.obehy.feature.planner

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import cz.obehy.core.map.PreloadRouteMaps
import cz.obehy.core.model.Itinerary

/** Results to be ready just past the bottom of the screen, where scrolling usually goes. */
private const val AHEAD = 2

/** Results to be ready just past the top. */
private const val BEHIND = 1

/** The most maps wanted at once; fewer than the map keeps, so they don't push each other out. */
private const val MOST = 6

/**
 * Which results to have a map ready for, most likely to be opened first: those at [visible] (their
 * positions among the [count] results), then the next ones down, then the one above. With none in
 * view, as when the search form fills the screen, the first ones are next.
 */
internal fun resultsToPrepare(
    count: Int,
    visible: List<Int>,
): List<Int> {
    val shown = visible.sorted()
    val below = (shown.lastOrNull() ?: -1).let { last -> (last + 1)..(last + AHEAD) }
    val above = (shown.firstOrNull() ?: 0).let { first -> (first - 1) downTo (first - BEHIND) }
    return (shown + below + above).filter { it in 0 until count }.take(MOST)
}

/**
 * Keeps maps ready for the results around what's on screen in [list]. While it scrolls, or while
 * [paused], nothing is drawn: the list only follows along when it comes to rest.
 */
@Composable
internal fun PreloadResultMaps(
    itineraries: List<Itinerary>,
    list: LazyListState,
    paused: Boolean,
) {
    val isPaused by rememberUpdatedState(paused)
    val journeys by rememberUpdatedState(itineraries)
    val positions = remember(itineraries) { itineraries.withIndex().associate { (index, it) -> it.key to index } }
    // Scrolling changes the layout every frame; this only changes when another row comes into view.
    val prepare by remember(positions) {
        derivedStateOf {
            resultsToPrepare(positions.size, list.layoutInfo.visibleItemsInfo.mapNotNull { positions[it.key] })
        }
    }
    PreloadRouteMaps {
        if (isPaused || list.isScrollInProgress) emptyList() else prepare.mapNotNull { journeys.getOrNull(it)?.legs }
    }
}
