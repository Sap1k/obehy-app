package cz.obehy.feature.planner

import cz.obehy.core.model.JourneyQuery
import kotlinx.serialization.Serializable

/**
 * Where the user was in the planner, saved so that it survives Android killing the app. The
 * screens on the back stack come back by themselves but know nothing of the journey, so the view
 * model reads this when they do. Closing the app for good starts afresh, and the file goes.
 * The place search's results and a search under way are redone rather than saved.
 */
@Serializable
internal data class PlannerSnapshot(
    val query: JourneyQuery,
    val loaded: Results.Loaded? = null,
    val open: OpenJourney? = null,
    /** No journeys were on screen for this search, and one is due. */
    val searchAgain: Boolean = false,
    val picking: PlaceTarget? = null,
    val pickingText: String = "",
)

/** Nothing that was under way is still under way after the app was killed. */
internal fun PlannerState.toSnapshot() =
    PlannerSnapshot(
        query = query,
        loaded = (results as? Results.Loaded)?.copy(loadingMore = false, refreshing = false),
        open = open?.copy(refreshing = false),
        searchAgain = results is Results.Loading || results is Results.Failed || results is Results.NoLocation,
        picking = placeSearch?.target,
        pickingText = placeSearch?.text.orEmpty(),
    )
