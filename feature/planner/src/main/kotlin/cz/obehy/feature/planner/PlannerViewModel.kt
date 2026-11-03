package cz.obehy.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.obehy.core.data.JourneyRepository
import cz.obehy.core.data.PlaceRepository
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.Place
import cz.obehy.core.model.SearchOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Which place of the search is being picked. */
sealed interface PlaceTarget {
    data object From : PlaceTarget

    data object To : PlaceTarget

    /** The via at [index], or a new one at the end. */
    data class Via(
        val index: Int,
    ) : PlaceTarget

    val stopsOnly: Boolean get() = this is Via
}

/** The place search while a place is being picked. */
data class PlaceSearch(
    val target: PlaceTarget,
    val text: String = "",
    val results: List<Place> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** The journeys found for the current search. */
sealed interface Results {
    data object Idle : Results

    data object Loading : Results

    data object Failed : Results

    data class Loaded(
        val itineraries: List<Itinerary>,
        val earlierCursor: String?,
        val laterCursor: String?,
        /** When these journeys were asked for: how old their times are. */
        val fetchedAt: Instant,
        val loadingMore: Boolean = false,
        val refreshing: Boolean = false,
        /** The last refresh didn't work; the times are as of [fetchedAt]. */
        val failed: Boolean = false,
    ) : Results
}

/** The journey being looked at, and how fresh its times are. */
data class OpenJourney(
    val itinerary: Itinerary,
    val fetchedAt: Instant,
    val refreshing: Boolean = false,
    /** The last refresh didn't work; the times are as of [fetchedAt]. */
    val failed: Boolean = false,
)

data class PlannerState(
    val query: JourneyQuery = JourneyQuery(),
    val options: SearchOptions = SearchOptions(),
    val results: Results = Results.Idle,
    val placeSearch: PlaceSearch? = null,
    /** The journey open in the detail (beside the results on wide screens). */
    val open: OpenJourney? = null,
) {
    /** Whether there is anything to clear with a new search. */
    val canReset: Boolean get() = query != JourneyQuery() || results != Results.Idle
}

/** Everything the user can do on the planner. */
sealed interface PlannerAction {
    data class Pick(
        val target: PlaceTarget,
    ) : PlannerAction

    data class Type(
        val text: String,
    ) : PlannerAction

    data class Choose(
        val place: Place,
    ) : PlannerAction

    data object ClosePlaceSearch : PlannerAction

    data class RemoveVia(
        val index: Int,
    ) : PlannerAction

    data object Swap : PlannerAction

    /** [time] null means now. */
    data class SetTime(
        val time: Instant?,
        val arriveBy: Boolean,
    ) : PlannerAction

    data class SetOptions(
        val options: SearchOptions,
    ) : PlannerAction

    data object Search : PlannerAction

    data object Earlier : PlannerAction

    data object Later : PlannerAction

    data class Select(
        val itinerary: Itinerary,
    ) : PlannerAction

    /** The open journey's times again. */
    data object Refresh : PlannerAction

    /** Fresh times for the journeys found. */
    data object RefreshResults : PlannerAction

    /** A new, empty search; the remembered options stay. */
    data object Reset : PlannerAction
}

/** The journey search: the places and time, the remembered options, and the results. */
@OptIn(FlowPreview::class)
@HiltViewModel
class PlannerViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val places: PlaceRepository,
        private val journeys: JourneyRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(PlannerState())
        private var searchJob: Job? = null
        private var refreshJob: Job? = null

        /** The clock; tests set their own. */
        internal var now: () -> Instant = Instant::now

        val state: StateFlow<PlannerState> =
            combine(local, settings.searchOptions) { state, options -> state.copy(options = options) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerState())

        init {
            viewModelScope.launch {
                local
                    .map { state -> state.placeSearch?.let { it.target to it.text } }
                    .distinctUntilChanged()
                    .debounce(PLACE_SEARCH_DELAY_MS)
                    .collectLatest { typed ->
                        val (target, text) = typed ?: return@collectLatest
                        if (text.trim().length < MIN_PLACE_TEXT) return@collectLatest
                        val found = places.search(text, target.stopsOnly)
                        local.update {
                            val search = it.placeSearch
                            if (search?.text != text) {
                                it
                            } else {
                                it.copy(
                                    placeSearch =
                                        search.copy(
                                            results = found.getOrDefault(search.results),
                                            loading = false,
                                            failed = found.isFailure,
                                        ),
                                )
                            }
                        }
                    }
            }
        }

        fun act(action: PlannerAction) {
            when (action) {
                is PlannerAction.Pick -> {
                    local.update { it.copy(placeSearch = PlaceSearch(action.target)) }
                }

                is PlannerAction.Type -> {
                    local.update {
                        val search = it.placeSearch ?: return@update it
                        val searching = action.text.trim().length >= MIN_PLACE_TEXT
                        it.copy(
                            placeSearch =
                                search.copy(
                                    text = action.text,
                                    loading = searching,
                                    failed = false,
                                    results = if (searching) search.results else emptyList(),
                                ),
                        )
                    }
                }

                is PlannerAction.Choose -> {
                    choose(action.place)
                }

                PlannerAction.ClosePlaceSearch -> {
                    local.update { it.copy(placeSearch = null) }
                }

                is PlannerAction.RemoveVia -> {
                    changeQuery { it.copy(vias = it.vias.filterIndexed { i, _ -> i != action.index }) }
                }

                PlannerAction.Swap -> {
                    changeQuery { it.reversed() }
                }

                is PlannerAction.SetTime -> {
                    changeQuery { it.copy(time = action.time, arriveBy = action.arriveBy) }
                }

                is PlannerAction.SetOptions -> {
                    viewModelScope.launch {
                        settings.setSearchOptions(action.options)
                        researchIfShown()
                    }
                }

                PlannerAction.Search -> {
                    search()
                }

                PlannerAction.Earlier -> {
                    loadMore(earlier = true)
                }

                PlannerAction.Later -> {
                    loadMore(earlier = false)
                }

                is PlannerAction.Select -> {
                    val fetchedAt = (local.value.results as? Results.Loaded)?.fetchedAt ?: now()
                    local.update { it.copy(open = OpenJourney(action.itinerary, fetchedAt)) }
                }

                PlannerAction.Refresh -> {
                    refresh()
                }

                PlannerAction.RefreshResults -> {
                    refreshResults()
                }

                PlannerAction.Reset -> {
                    searchJob?.cancel()
                    refreshJob?.cancel()
                    local.update { PlannerState() }
                }
            }
        }

        /** The open journey's times again; it stays as it was when it can't be found again. */
        private fun refresh() {
            val open = local.value.open ?: return
            if (open.refreshing) return
            local.update { it.copy(open = open.copy(refreshing = true)) }
            refreshJob =
                viewModelScope.launch {
                    val fresh = journeys.refresh(listOf(open.itinerary), settings.searchOptions.first())
                    apply(fresh)
                }
        }

        /** Fresh times for every journey on screen. */
        private fun refreshResults() {
            val shown = local.value.results as? Results.Loaded ?: return
            if (shown.refreshing || shown.itineraries.isEmpty()) return
            local.update { it.copy(results = shown.copy(refreshing = true)) }
            refreshJob =
                viewModelScope.launch {
                    val fresh = journeys.refresh(shown.itineraries, settings.searchOptions.first())
                    apply(fresh)
                }
        }

        /** Puts [fresh] journeys in place of their stale selves, in the results and the detail. */
        private fun apply(fresh: Result<Map<String, Itinerary>>) {
            val at = now()
            val found = fresh.getOrNull().orEmpty()
            local.update { state ->
                val results =
                    (state.results as? Results.Loaded)?.let { loaded ->
                        if (!loaded.refreshing && found.isEmpty()) {
                            loaded
                        } else {
                            loaded.copy(
                                itineraries = loaded.itineraries.map { found[it.key] ?: it },
                                fetchedAt = if (loaded.refreshing && fresh.isSuccess) at else loaded.fetchedAt,
                                refreshing = false,
                                failed = loaded.refreshing && fresh.isFailure,
                            )
                        }
                    } ?: state.results
                val open =
                    state.open?.let { open ->
                        when {
                            found[open.itinerary.key] != null -> OpenJourney(found.getValue(open.itinerary.key), at)
                            open.refreshing -> open.copy(refreshing = false, failed = true)
                            else -> open
                        }
                    }
                state.copy(results = results, open = open)
            }
        }

        private fun choose(place: Place) {
            val target = local.value.placeSearch?.target ?: return
            local.update { it.copy(placeSearch = null) }
            changeQuery { query ->
                when (target) {
                    PlaceTarget.From -> {
                        query.copy(from = place)
                    }

                    PlaceTarget.To -> {
                        query.copy(to = place)
                    }

                    is PlaceTarget.Via -> {
                        val vias = query.vias.toMutableList()
                        if (target.index < vias.size) vias[target.index] = place else vias += place
                        query.copy(vias = vias.take(JourneyQuery.MAX_VIAS))
                    }
                }
            }
        }

        /** Changes the search; journeys already on screen are searched again to match. */
        private fun changeQuery(change: (JourneyQuery) -> JourneyQuery) {
            local.update { it.copy(query = change(it.query)) }
            researchIfShown()
        }

        private fun researchIfShown() {
            if (local.value.results != Results.Idle) search()
        }

        private fun search() {
            val query = local.value.query
            if (!query.isComplete) return
            searchJob?.cancel()
            refreshJob?.cancel()
            local.update { it.copy(results = Results.Loading, open = null) }
            searchJob =
                viewModelScope.launch {
                    val page = journeys.search(query, settings.searchOptions.first())
                    val at = now()
                    local.update { state ->
                        state.copy(
                            results =
                                page.fold(
                                    { Results.Loaded(it.itineraries, it.earlierCursor, it.laterCursor, at) },
                                    { Results.Failed },
                                ),
                        )
                    }
                }
        }

        private fun loadMore(earlier: Boolean) {
            val shown = local.value.results as? Results.Loaded ?: return
            val cursor = (if (earlier) shown.earlierCursor else shown.laterCursor) ?: return
            if (shown.loadingMore) return
            local.update { it.copy(results = shown.copy(loadingMore = true)) }
            searchJob =
                viewModelScope.launch {
                    val page = journeys.search(local.value.query, settings.searchOptions.first(), cursor)
                    local.update { state ->
                        val current = state.results as? Results.Loaded ?: return@update state
                        val more = page.getOrNull()
                        val merged =
                            when {
                                more == null -> {
                                    current.copy(loadingMore = false)
                                }

                                earlier -> {
                                    current.copy(
                                        itineraries = (more.itineraries + current.itineraries).distinctBy { it.key },
                                        earlierCursor = more.earlierCursor,
                                        loadingMore = false,
                                    )
                                }

                                else -> {
                                    current.copy(
                                        itineraries = (current.itineraries + more.itineraries).distinctBy { it.key },
                                        laterCursor = more.laterCursor,
                                        loadingMore = false,
                                    )
                                }
                            }
                        state.copy(results = merged)
                    }
                }
        }

        private companion object {
            const val PLACE_SEARCH_DELAY_MS = 250L
            const val MIN_PLACE_TEXT = 2
        }
    }
