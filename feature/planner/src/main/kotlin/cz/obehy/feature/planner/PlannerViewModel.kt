package cz.obehy.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.obehy.core.data.JourneyEnd
import cz.obehy.core.data.JourneyRepository
import cz.obehy.core.data.JourneySuggester
import cz.obehy.core.data.LocationRepository
import cz.obehy.core.data.PlaceRepository
import cz.obehy.core.data.SearchHistoryRepository
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.NearbyStop
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.model.Suggestion
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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

/** Whether nearby stops can be shown, and if not, what would make them appear. */
enum class NearbyStatus { Unknown, Loading, Ready, NeedsPermission, Off }

/** Nearby stops fetched ahead of the picker opening. */
private class PreloadedStops(
    val stops: List<NearbyStop>,
    val at: Instant,
)

/** The place search while a place is being picked. */
data class PlaceSearch(
    val target: PlaceTarget,
    val text: String = "",
    val results: List<Place> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** Shown while nothing has been typed. */
    val nearby: List<NearbyStop> = emptyList(),
    val nearbyStatus: NearbyStatus = NearbyStatus.Unknown,
    val recents: List<Place> = emptyList(),
)

/** The journeys found for the current search. */
sealed interface Results {
    data object Idle : Results

    data object Loading : Results

    data object Failed : Results

    /** The search starts or ends at "My location", and the phone can't tell where that is. */
    data object NoLocation : Results

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
    /** Journeys to offer before a search. */
    val suggestions: List<Suggestion> = emptyList(),
) {
    /** Whether there is anything to clear with a new search. */
    val canReset: Boolean
        get() {
            val typed = query.copy(from = query.from?.takeUnless { it.kind == PlaceKind.MyLocation })
            return typed != JourneyQuery() || results != Results.Idle
        }
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

    /** Fills the search with this journey and searches. */
    data class UseSuggestion(
        val suggestion: Suggestion,
    ) : PlannerAction

    data class ForgetSuggestion(
        val suggestion: Suggestion,
    ) : PlannerAction

    data class ForgetPlace(
        val place: Place,
    ) : PlannerAction

    /** The app came back, or the permission was answered: the location's status may have changed. */
    data object CheckLocation : PlannerAction

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
        private val location: LocationRepository,
        private val history: SearchHistoryRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(PlannerState())
        private var searchJob: Job? = null
        private var refreshJob: Job? = null
        private var nearbyJob: Job? = null
        private var preloadJob: Job? = null
        private var preloaded: PreloadedStops? = null

        /** The clock; tests set their own. */
        internal var now: () -> Instant = Instant::now

        /** Bumped when the app comes back: the time, and perhaps the place, have moved on. */
        private val moment = MutableStateFlow(0)

        private val suggestions =
            combine(history.searches, moment) { searches, _ ->
                JourneySuggester.suggestions(searches, now().atZone(ZoneId.systemDefault()), location.lastKnown())
            }.onStart { emit(emptyList()) }

        val state: StateFlow<PlannerState> =
            combine(local, settings.searchOptions, suggestions) { state, options, suggested ->
                state.copy(options = options, suggestions = suggested)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerState())

        init {
            viewModelScope.launch { location.allowed.collect { startFromHere() } }
            viewModelScope.launch {
                // Stop IDs mean nothing to another provider.
                settings.settings.map { it.routingProvider }.distinctUntilChanged().drop(1).collect {
                    preloadJob?.cancel()
                    preloaded = null
                }
            }
            viewModelScope.launch {
                local
                    .map { state -> state.placeSearch?.let { it.target to it.text } }
                    .distinctUntilChanged()
                    .debounce(PLACE_SEARCH_DELAY_MS)
                    .collectLatest { typed ->
                        val (target, text) = typed ?: return@collectLatest
                        if (text.trim().length < MIN_PLACE_TEXT) return@collectLatest
                        val found = places.search(text, target.stopsOnly, location.lastKnown())
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
                    loadNearby()
                    loadRecents(action.target)
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

                is PlannerAction.UseSuggestion -> {
                    val (from, to) = action.suggestion
                    local.update { it.copy(query = it.query.copy(from = from, to = to, vias = emptyList())) }
                    search()
                }

                is PlannerAction.ForgetSuggestion -> {
                    viewModelScope.launch { history.forget(action.suggestion) }
                }

                is PlannerAction.ForgetPlace -> {
                    local.update { state ->
                        val search = state.placeSearch ?: return@update state
                        state.copy(placeSearch = search.copy(recents = search.recents - action.place))
                    }
                    viewModelScope.launch { history.forget(action.place) }
                }

                PlannerAction.CheckLocation -> {
                    location.recheck()
                    moment.update { it + 1 }
                    if (location.allowed.value && freshStops() == null) preloadStops()
                    val status = local.value.placeSearch?.nearbyStatus
                    if (status != null && status != NearbyStatus.Ready && status != NearbyStatus.Loading) loadNearby()
                }

                PlannerAction.ClosePlaceSearch -> {
                    nearbyJob?.cancel()
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
                    startFromHere()
                }
            }
        }

        /** A search with no start begins here, once the user has let the app know where that is. */
        private fun startFromHere() {
            if (!location.allowed.value) return
            local.update { state ->
                val query = state.query
                if (query.from == null && query.to?.kind != PlaceKind.MyLocation) {
                    state.copy(query = query.copy(from = Place.myLocation()))
                } else {
                    state
                }
            }
        }

        /** The stops around the phone, for picking a start before anything is typed. */
        private fun loadNearby() {
            nearbyJob?.cancel()
            val fromHere = location.allowed.value && local.value.placeSearch?.target == PlaceTarget.From
            val ready = freshStops()?.takeIf { fromHere }
            if (ready != null) {
                updateNearby(NearbyStatus.Ready, ready)
                return
            }
            nearbyJob =
                viewModelScope.launch {
                    val status =
                        when {
                            !location.allowed.value -> NearbyStatus.NeedsPermission
                            !location.isEnabled() -> NearbyStatus.Off
                            local.value.placeSearch?.target == PlaceTarget.From -> NearbyStatus.Loading
                            else -> NearbyStatus.Unknown
                        }
                    updateNearby(status)
                    if (status != NearbyStatus.Loading) return@launch
                    preloadStops().join()
                    val stops = freshStops()
                    updateNearby(if (stops != null) NearbyStatus.Ready else NearbyStatus.Unknown, stops.orEmpty())
                }
        }

        /** Gets the stops ready for the picker, joining a fetch already under way. */
        private fun preloadStops(): Job =
            preloadJob?.takeIf { it.isActive } ?: viewModelScope
                .launch {
                    val stops = location.current()?.let { places.nearby(it).getOrNull() }
                    preloaded = stops?.let { PreloadedStops(it, now()) }
                }.also { preloadJob = it }

        private fun freshStops(): List<NearbyStop>? =
            preloaded?.takeIf { Duration.between(it.at, now()) < STOPS_STAY_FRESH }?.stops

        private fun updateNearby(
            status: NearbyStatus,
            stops: List<NearbyStop> = emptyList(),
        ) {
            local.update { state ->
                state.copy(placeSearch = state.placeSearch?.copy(nearbyStatus = status, nearby = stops))
            }
        }

        /** The user's own places for the picker, those that go with the other end first. */
        private fun loadRecents(target: PlaceTarget) {
            viewModelScope.launch {
                val query = local.value.query
                val recents =
                    JourneySuggester.places(
                        history.searches.first(),
                        now().atZone(ZoneId.systemDefault()),
                        location.lastKnown(),
                        if (target == PlaceTarget.From) JourneyEnd.From else JourneyEnd.To,
                        when (target) {
                            PlaceTarget.From -> query.to
                            PlaceTarget.To -> query.from
                            is PlaceTarget.Via -> null
                        },
                        target.stopsOnly,
                    )
                local.update { state ->
                    val search = state.placeSearch?.takeIf { it.target == target } ?: return@update state
                    state.copy(placeSearch = search.copy(recents = recents))
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
            nearbyJob?.cancel()
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
                    val located = locate(query)
                    if (located == null) {
                        local.update { it.copy(results = Results.NoLocation) }
                        return@launch
                    }
                    local.update { it.copy(query = located) }
                    val page = journeys.search(located, settings.searchOptions.first())
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
                    val from = located.from
                    val to = located.to
                    if (from != null && to != null && !page.getOrNull()?.itineraries.isNullOrEmpty()) {
                        history.record(from, to, at)
                    }
                }
        }

        /** [query] with "My location" put on the map; null when the phone can't tell where it is. */
        private suspend fun locate(query: JourneyQuery): JourneyQuery? {
            if (query.from?.kind != PlaceKind.MyLocation && query.to?.kind != PlaceKind.MyLocation) return query
            val fix = location.current() ?: return null
            return query.copy(from = query.from?.located(fix), to = query.to?.located(fix))
        }

        private fun Place.located(fix: LatLon): Place =
            if (kind ==
                PlaceKind.MyLocation
            ) {
                Place.myLocation(fix)
            } else {
                this
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
            val STOPS_STAY_FRESH: Duration = Duration.ofMinutes(2)
        }
    }
