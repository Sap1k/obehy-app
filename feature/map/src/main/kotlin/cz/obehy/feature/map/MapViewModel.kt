package cz.obehy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.obehy.core.data.LiveMapAvailability
import cz.obehy.core.data.LiveMapRepository
import cz.obehy.core.data.LocationRepository
import cz.obehy.core.data.TripRepository
import cz.obehy.core.data.cellsFor
import cz.obehy.core.map.LiveCamera
import cz.obehy.core.map.followZoom
import cz.obehy.core.model.BoardSide
import cz.obehy.core.model.Composition
import cz.obehy.core.model.CompositionStatus
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.LiveMapSource
import cz.obehy.core.model.LiveStop
import cz.obehy.core.model.LiveVehicle
import cz.obehy.core.model.MapBounds
import cz.obehy.core.model.MapMode
import cz.obehy.core.model.RailvizSegment
import cz.obehy.core.model.StopBoard
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripRef
import cz.obehy.core.model.VehicleFacts
import cz.obehy.core.model.VehicleHit
import cz.obehy.core.model.placeOf
import cz.obehy.core.model.vehiclesAt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** How far the sheet of a selected vehicle is pulled up. */
internal enum class SheetDetent { Peek, Open }

/** What's known of the vehicle or trip opened on the map; [vehicleId] is null for a trip opened from a board. */
internal data class Selection(
    val trip: TripRef?,
    val vehicleId: String?,
    val vehicle: LiveVehicle? = null,
    val detail: TripDetail? = null,
    val composition: Composition? = null,
    val facts: VehicleFacts? = null,
    /** Railviz only: the stop it should be near or waiting at. */
    val near: String? = null,
    /** Picked from a board: the stop it was picked at, where the ride starts. */
    val from: LatLon? = null,
    val failed: Boolean = false,
)

internal data class SearchState(
    val query: String = "",
    val hits: List<VehicleHit> = emptyList(),
    val searching: Boolean = false,
    val failed: Boolean = false,
)

/** A stop opened on the map: its board, which way it looks ([side]), and whether more rows are on their way. */
internal data class StopState(
    val stop: LiveStop,
    val board: StopBoard? = null,
    val failed: Boolean = false,
    val side: BoardSide = BoardSide.Departures,
    val loadingMore: Boolean = false,
)

internal data class MapUiState(
    val source: LiveMapSource = LiveMapSource.None,
    /** Every vehicle in view, whatever the filter. */
    val vehicles: List<LiveVehicle> = emptyList(),
    val modes: Set<MapMode> = MapMode.entries.toSet(),
    val stops: List<LiveStop> = emptyList(),
    val selection: Selection? = null,
    val sheet: SheetDetent = SheetDetent.Peek,
    val following: Boolean = false,
    val search: SearchState? = null,
    val stop: StopState? = null,
    val me: LatLon? = null,
    val camera: LiveCamera? = null,
    /** The last answer for the vehicles failed; what's shown is older. */
    val failed: Boolean = false,
    val explaining: Boolean = false,
    /** The stop whose board the open trip was picked from: going back returns to it. */
    val returnTo: StopState? = null,
) {
    /** The vehicles the filter lets through. */
    val shown: List<LiveVehicle> get() = vehicles.filter { it.mapMode in modes }
}

@HiltViewModel
internal class MapViewModel
    @Inject
    constructor(
        availability: LiveMapAvailability,
        private val live: LiveMapRepository,
        private val trips: TripRepository,
        private val location: LocationRepository,
    ) : ViewModel(),
        MapActions {
        private val local = MutableStateFlow(MapUiState())
        val state: StateFlow<MapUiState> = local.asStateFlow()

        private val source: StateFlow<LiveMapSource> =
            availability.source.stateIn(viewModelScope, SharingStarted.Eagerly, LiveMapSource.None)

        private var viewport: MapBounds? = null
        private var zoom = 0.0
        private val viewportChanged = MutableStateFlow(0)
        private var railviz = RailvizWindow()
        private var tripJob: Job? = null
        private var searchJob: Job? = null
        private var stopsJob: Job? = null
        private var stopsArea: MapBounds? = null

        /** The grid cells the vehicles shown were last asked for in. */
        private var fetchedCells: Set<String> = emptySet()

        private fun cellsOf(bounds: MapBounds): Set<String> =
            cellsFor(bounds.grown(VIEW_MARGIN)).mapTo(mutableSetOf()) {
                it.key
            }

        private var cameraAsks = 0

        init {
            viewModelScope.launch {
                source.collect { s ->
                    // Another source's stops and vehicles go by other IDs.
                    stopsArea = null
                    update { it.copy(source = s, vehicles = emptyList(), stops = emptyList()) }
                }
            }
            viewModelScope.launch { update { it.copy(me = location.lastKnown()) } }
        }

        /** Keeps the vehicles current while the map is on screen; cancelled when it isn't. */
        suspend fun keepLive() {
            coroutineScope {
                launch { keepBoardLive() }
                source.collectLatest { s ->
                    when (s) {
                        LiveMapSource.Obehy -> pollObehy()
                        LiveMapSource.Railviz -> runRailviz()
                        LiveMapSource.None -> Unit
                    }
                }
            }
        }

        /** An open board, asked again every little while: as many rows as it shows, from now. */
        private suspend fun keepBoardLive() {
            local
                .map { state -> state.stop?.let { it.stop to it.side } }
                .distinctUntilChanged()
                .collectLatest { open ->
                    val (stop, side) = open ?: return@collectLatest
                    while (true) {
                        delay(BOARD_REFRESH_MS)
                        val rows =
                            local.value.stop
                                ?.board
                                ?.departures
                                ?.size ?: 0
                        val board =
                            runCatchingNotCancelled {
                                live.board(
                                    stop,
                                    source.value,
                                    side,
                                    rows = maxOf(rows, BOARD_ROWS),
                                )
                            }
                        board.getOrNull()?.let { fresh ->
                            update { state ->
                                val was =
                                    state.stop?.takeIf { it.stop.id == stop.id && it.side == side }
                                        ?: return@update state
                                state.copy(stop = was.copy(board = fresh, failed = false))
                            }
                        }
                    }
                }
        }

        override fun onViewport(
            bounds: MapBounds,
            zoom: Double,
        ) {
            viewport = bounds
            this.zoom = zoom
            // Asked again at once only when the view reaches cells not yet asked for: a pan within
            // them waits for the next tick, so it doesn't redraw every vehicle.
            if (!fetchedCells.containsAll(cellsOf(bounds))) viewportChanged.update { it + 1 }
            loadStops(bounds, zoom)
        }

        /** The user moved the map by hand: following goes on while the vehicle is still in view (a zoom), not after a pan away. */
        override fun onGesture(bounds: MapBounds) {
            val state = local.value
            val at =
                state.selection
                    ?.vehicle
                    ?.at
                    ?.position
            if (state.following &&
                (at == null || at !in bounds)
            ) {
                update { it.copy(following = false, camera = null) }
            }
        }

        override fun toggleMode(mode: MapMode) {
            update { state ->
                val modes = if (mode in state.modes) state.modes - mode else state.modes + mode
                state.copy(modes = modes.ifEmpty { MapMode.entries.toSet() })
            }
        }

        override fun selectVehicle(id: String) {
            val vehicle = local.value.vehicles.firstOrNull { it.id == id } ?: return
            update { it.copy(returnTo = null) }
            open(Selection(vehicle.trip, id, vehicle))
        }

        override fun selectTrip(trip: TripRef) {
            val vehicle = local.value.vehicles.firstOrNull { it.trip == trip }
            val board = local.value.stop
            // Picked from a board: back goes back to it, and the ride starts at its stop.
            update { it.copy(returnTo = board) }
            open(Selection(trip, vehicle?.id, vehicle, from = board?.stop?.position))
        }

        override fun selectHit(hit: VehicleHit) {
            update { state ->
                val found = hit.vehicle
                val vehicles = if (state.vehicles.any { it.id == found.id }) state.vehicles else state.vehicles + found
                state.copy(vehicles = vehicles, search = null, returnTo = null)
            }
            open(Selection(hit.vehicle.trip, hit.vehicle.id, hit.vehicle))
        }

        override fun setSheet(detent: SheetDetent) = update { it.copy(sheet = detent) }

        /** Closes the trip; one picked from a board goes back to that board. */
        override fun close() {
            tripJob?.cancel()
            val back = local.value.returnTo
            update {
                it.copy(
                    selection = null,
                    following = false,
                    camera = null,
                    sheet = SheetDetent.Peek,
                    stop = back ?: it.stop,
                    returnTo = null,
                )
            }
            back?.stop?.let(::loadBoard)
        }

        /** Closes whatever the sheet shows, and anything it would go back to. */
        override fun closeAll() {
            tripJob?.cancel()
            update {
                it.copy(
                    selection = null,
                    following = false,
                    camera = null,
                    sheet = SheetDetent.Peek,
                    stop = null,
                    returnTo = null,
                )
            }
        }

        override fun toggleFollow() {
            update { state ->
                val vehicle = state.selection?.vehicle
                if (state.following || vehicle == null) {
                    state.copy(following = false, camera = null)
                } else {
                    state.copy(
                        following = true,
                        sheet = SheetDetent.Peek,
                        camera = LiveCamera.Follow(vehicle.at.position, followZoom(vehicle.mode)),
                    )
                }
            }
        }

        override fun locate() {
            viewModelScope.launch {
                val here = location.current() ?: return@launch
                update { it.copy(me = here, following = false, camera = show(here, LOCATE_ZOOM)) }
            }
        }

        override fun openSearch() = update { it.copy(search = SearchState()) }

        override fun closeSearch() {
            searchJob?.cancel()
            update { it.copy(search = null) }
        }

        override fun search(query: String) {
            update { it.copy(search = (it.search ?: SearchState()).copy(query = query, failed = false)) }
            searchJob?.cancel()
            if (query.isBlank()) {
                update { it.copy(search = it.search?.copy(hits = emptyList(), searching = false)) }
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_PAUSE_MS)
                    update { it.copy(search = it.search?.copy(searching = true)) }
                    val near = local.value.me ?: viewport?.center
                    val found = runCatchingNotCancelled { live.search(query.trim(), near) }
                    update { state ->
                        state.copy(
                            search =
                                state.search?.copy(
                                    hits = found.getOrDefault(state.search.hits),
                                    searching = false,
                                    failed = found.isFailure,
                                ),
                        )
                    }
                }
        }

        override fun selectStop(id: String) {
            val stop = local.value.stops.firstOrNull { it.id == id } ?: return
            // Ringed on the map, and brought into view above the sheet.
            update { it.copy(stop = StopState(stop)) }
            loadBoard(stop)
        }

        override fun closeStop() = update { it.copy(stop = null) }

        override fun loadBoard() {
            local.value.stop
                ?.stop
                ?.let(::loadBoard)
        }

        private fun loadBoard(stop: LiveStop) {
            val side = local.value.stop?.side ?: BoardSide.Departures
            viewModelScope.launch {
                val board = runCatchingNotCancelled { live.board(stop, source.value, side) }
                update { state ->
                    val was = state.stop?.takeIf { it.stop.id == stop.id && it.side == side } ?: return@update state
                    state.copy(stop = was.copy(board = board.getOrNull() ?: was.board, failed = board.isFailure))
                }
            }
        }

        override fun showBoard(side: BoardSide) {
            val open = local.value.stop?.takeIf { it.side != side } ?: return
            update { it.copy(stop = open.copy(board = null, failed = false, side = side, loadingMore = false)) }
            loadBoard(open.stop)
        }

        override fun moreBoard() {
            val open = local.value.stop ?: return
            val board = open.board ?: return
            val cursor = board.next ?: return
            if (open.loadingMore) return
            update { it.copy(stop = open.copy(loadingMore = true)) }
            viewModelScope.launch {
                val more =
                    runCatchingNotCancelled {
                        live.board(
                            open.stop,
                            source.value,
                            open.side,
                            cursor,
                        )
                    }.getOrNull()
                update { state ->
                    val was =
                        state.stop?.takeIf { it.stop.id == open.stop.id && it.side == open.side } ?: return@update state
                    val shown = was.board ?: return@update state.copy(stop = was.copy(loadingMore = false))
                    val known = shown.departures.mapTo(mutableSetOf()) { it.trip }
                    val joined =
                        more?.let {
                            shown.copy(
                                departures =
                                    shown.departures + it.departures.filter { row -> row.trip !in known },
                                next = it.next,
                            )
                        }
                    state.copy(stop = was.copy(board = joined ?: shown, loadingMore = false))
                }
            }
        }

        override fun setExplaining(open: Boolean) = update { it.copy(explaining = open) }

        override fun retryTrip() {
            local.value.selection?.let { open(it.copy(failed = false)) }
        }

        // The vehicles.

        /** Oběhy's vehicles, asked for on the wall clock's 10 s (after the worker's tick) and when the view moves. */
        private suspend fun pollObehy() {
            var seen = viewportChanged.value
            while (true) {
                fetchObehy()
                val wait = TICK_MS - System.currentTimeMillis() % TICK_MS + TICK_LAG_MS
                val until = System.currentTimeMillis() + wait
                // A moved map is asked about at once (after a short pause), not at the next tick.
                while (System.currentTimeMillis() < until && viewportChanged.value == seen) delay(WATCH_MS)
                if (viewportChanged.value != seen) delay(VIEWPORT_PAUSE_MS)
                seen = viewportChanged.value
            }
        }

        private suspend fun fetchObehy() {
            val bounds = viewport ?: return
            val held = local.value.vehicles
            val fresh = runCatchingNotCancelled { live.obehyVehicles(bounds.grown(VIEW_MARGIN), held) }
            fresh.onSuccess { vehicles ->
                fetchedCells = cellsOf(bounds)
                updateVehicles(vehicles)
            }
            update { it.copy(failed = fresh.isFailure) }
        }

        /** Railviz: stretches for the view and the next minutes, each vehicle placed every second. */
        private suspend fun runRailviz() {
            while (true) {
                val bounds = viewport
                val now = Instant.now()
                when {
                    bounds == null -> {
                        Unit
                    }

                    // Zoomed out further, railviz answers whole countries of long paths: megabytes.
                    zoom < RAILVIZ_MIN_ZOOM -> {
                        railviz = RailvizWindow()
                    }

                    !railviz.serves(bounds, zoom, now) -> {
                        val area = bounds.grown(RAILVIZ_MARGIN)
                        val zoomLevel = zoom.toInt()
                        val fetched =
                            runCatchingNotCancelled { live.railviz(area, zoomLevel, now, now.plus(RAILVIZ_WINDOW)) }
                        railviz =
                            fetched.fold(
                                onSuccess = {
                                    RailvizWindow(
                                        area,
                                        zoomLevel,
                                        now.plus(RAILVIZ_WINDOW).minus(RAILVIZ_REFETCH),
                                        it,
                                    )
                                },
                                // Kept as it was, and asked again a little later, not every second.
                                onFailure = {
                                    railviz.copy(
                                        area = area,
                                        zoom = zoomLevel,
                                        until = now.plus(RAILVIZ_RETRY),
                                    )
                                },
                            )
                        update { it.copy(failed = fetched.isFailure) }
                    }
                }
                updateVehicles(railviz.segments.vehiclesAt(Instant.now()))
                // Dots far out barely move on screen: placed less often there.
                delay(if (zoom < PILLS_ZOOM) RAILVIZ_FAR_STEP_MS else RAILVIZ_STEP_MS)
            }
        }

        /** New positions for the map, the selected one's sheet and a following camera. */
        private fun updateVehicles(vehicles: List<LiveVehicle>) {
            update { state ->
                val selection = state.selection
                val selected = selection?.vehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }
                val near =
                    if (state.source == LiveMapSource.Railviz && selection?.trip is TripRef.Motis) {
                        railviz.segments.placeOf(selection.trip.tripId, Instant.now())?.let {
                            if (it.waiting) it.segment.fromName else it.segment.toName
                        }
                    } else {
                        selection?.near
                    }
                state.copy(
                    vehicles = vehicles,
                    selection = selection?.copy(vehicle = selected ?: selection.vehicle, near = near),
                    camera =
                        if (state.following && selected != null) {
                            LiveCamera.Follow(selected.at.position, followZoom(selected.mode))
                        } else {
                            state.camera
                        },
                )
            }
        }

        // The selected vehicle's trip.

        private fun open(selection: Selection) {
            tripJob?.cancel()
            update { it.copy(selection = selection, following = false, sheet = SheetDetent.Peek, stop = null) }
            val trip = selection.trip ?: return
            tripJob =
                viewModelScope.launch {
                    selection.vehicleId
                        ?.takeIf { source.value == LiveMapSource.Obehy }
                        ?.let { id -> launch { loadFacts(id) } }
                    var compositionAsked = false
                    while (true) {
                        val detail = trips.opened(trip)
                        update { state ->
                            val now = state.selection?.takeIf { it.trip == trip } ?: return@update state
                            val boarded = detail.getOrNull()?.boardingAt(now.from)
                            // Ringed at the trip's own platform rather than the middle of the stop.
                            val start = boarded?.boarded?.first?.let { boarded.stops[it].call.position }
                            state.copy(
                                selection =
                                    now.copy(
                                        detail = boarded ?: now.detail,
                                        from = start ?: now.from,
                                        failed = detail.isFailure && now.detail == null,
                                    ),
                            )
                        }
                        val info = detail.getOrNull()?.info
                        if (!compositionAsked && info?.hasComposition == true) {
                            compositionAsked = true
                            launch { watchComposition(trip, info) }
                        }
                        delay(TRIP_REFRESH_MS)
                    }
                }
        }

        private suspend fun loadFacts(vehicleId: String) {
            val facts = runCatchingNotCancelled { live.facts(vehicleId) }.getOrNull() ?: return
            update { state ->
                val now = state.selection?.takeIf { it.vehicleId == vehicleId } ?: return@update state
                state.copy(selection = now.copy(facts = facts))
            }
        }

        /** The train's composition: the API asks its source lazily, so a pending answer is asked again. */
        private suspend fun watchComposition(
            trip: TripRef,
            info: cz.obehy.core.model.TripInfo,
        ) {
            repeat(COMPOSITION_PATIENCE) {
                val composition = trips.composition(info).getOrNull() ?: return
                update { state ->
                    val now = state.selection?.takeIf { it.trip == trip } ?: return@update state
                    state.copy(selection = now.copy(composition = composition))
                }
                if (composition.status != CompositionStatus.Pending) return
                delay((composition.retryAfterSeconds?.toLong() ?: COMPOSITION_WAIT_S) * MILLIS_PER_SECOND)
            }
        }

        // Stops.

        /**
         * The stops around the view, once zoomed in close (the map hides them further out). They're
         * asked for with a margin, kept as the map moves, and asked again only when it leaves that.
         */
        private fun loadStops(
            bounds: MapBounds,
            zoom: Double,
        ) {
            if (zoom < STOPS_ZOOM || stopsArea?.covers(bounds) == true) return
            stopsJob?.cancel()
            stopsJob =
                viewModelScope.launch {
                    delay(VIEWPORT_PAUSE_MS)
                    val area = bounds.grown(STOPS_MARGIN)
                    runCatchingNotCancelled { live.stops(area, source.value) }
                        .onSuccess { found ->
                            stopsArea = area
                            update { state ->
                                val known = (state.stops + found).associateBy { it.id }.values
                                // Kept as the map moves, but not without end.
                                val stops = if (known.size > STOPS_KEPT) found else known.toList()
                                state.copy(stops = stops)
                            }
                        }
                }
        }

        private fun show(
            at: LatLon,
            zoom: Double,
        ): LiveCamera = LiveCamera.Show(at, zoom, ++cameraAsks)

        /**
         * Every change of the map's state comes through here. When what the map is about changes
         * (a stop opened, a vehicle, a ride from a board, back to the board), the camera is framed
         * on it from the new state, so no way into or out of a sheet can forget to move it.
         * Following moves the camera on its own; leaving everything keeps the view as it is.
         */
        private inline fun update(transform: (MapUiState) -> MapUiState) {
            local.update { old ->
                val new = transform(old)
                val focus = new.focus()
                if (focus != null && focus != old.focus() && !new.following) new.copy(camera = framing(new)) else new
            }
        }

        /** Where the camera goes for what [state] is about. */
        private fun framing(state: MapUiState): LiveCamera? {
            val selection = state.selection
            val stop = state.stop
            return when {
                selection != null -> {
                    val vehicle = selection.vehicle
                    val detail = selection.detail
                    val from = selection.from
                    when {
                        // Picked from a board: the ride from where it starts (and the vehicle on
                        // its way), once its stops are in; until then, where it starts.
                        from != null && detail?.boarded != null -> {
                            LiveCamera.Fit(detail.rideFrame(vehicle?.at?.position), ++cameraAsks)
                        }

                        from != null -> {
                            show(from, maxOf(zoom, BOARD_ZOOM))
                        }

                        // A vehicle with its stops in: it and where it goes next. Until then, or with
                        // nothing ahead worth framing, the vehicle in the middle.
                        vehicle != null -> {
                            detail?.aheadFrame(vehicle.at.position)?.let { LiveCamera.Fit(it, ++cameraAsks) }
                                ?: show(vehicle.at.position, maxOf(zoom, followZoom(vehicle.mode)))
                        }

                        else -> {
                            state.camera
                        }
                    }
                }

                stop != null -> {
                    show(stop.stop.position, maxOf(zoom, STOP_ZOOM))
                }

                else -> {
                    state.camera
                }
            }
        }

        private companion object {
            const val TICK_MS = 10_000L
            const val TICK_LAG_MS = 1_500L
            const val WATCH_MS = 200L
            const val VIEWPORT_PAUSE_MS = 400L
            const val SEARCH_PAUSE_MS = 300L
            const val TRIP_REFRESH_MS = 30_000L
            const val BOARD_REFRESH_MS = 30_000L
            const val BOARD_ROWS = 15
            const val RAILVIZ_STEP_MS = 1_000L
            const val RAILVIZ_FAR_STEP_MS = 5_000L

            /** Where the map turns vehicles from dots into pills (LiveMap's own threshold). */
            const val PILLS_ZOOM = 11.0
            const val LOCATE_ZOOM = 15.0

            // Asked for a step before the map shows them, so they're there when it does.
            const val STOPS_ZOOM = 13.0
            const val BOARD_ZOOM = 15.0
            const val STOP_ZOOM = 16.0

            /** Stops are asked for a little past the view (the API answers at most half a square degree). */
            const val STOPS_MARGIN = 0.5
            const val STOPS_KEPT = 3_000

            /** The cells reach a little past the view, so a small pan finds its vehicles there already. */
            const val VIEW_MARGIN = 0.1

            /** Railviz is asked for well past the view and for minutes ahead: its answers are heavy. */
            const val RAILVIZ_MARGIN = 0.25
            const val RAILVIZ_MIN_ZOOM = 6.0
            val RAILVIZ_WINDOW: Duration = Duration.ofMinutes(3)
            val RAILVIZ_REFETCH: Duration = Duration.ofSeconds(30)
            val RAILVIZ_RETRY: Duration = Duration.ofSeconds(15)

            const val COMPOSITION_PATIENCE = 20
            const val COMPOSITION_WAIT_S = 3L
            const val MILLIS_PER_SECOND = 1_000L
        }
    }

/** This trip ridden from its stop nearest [from] (a board's stop), when it calls within reach of it. */
private fun TripDetail.boardingAt(from: LatLon?): TripDetail {
    if (from == null) return this
    val nearest =
        stops.indices.minByOrNull { stops[it].call.position?.metresTo(from) ?: Double.MAX_VALUE } ?: return this
    val metres = stops[nearest].call.position?.metresTo(from) ?: return this
    return if (metres <= BOARD_REACH_M) copy(boarded = nearest..stops.lastIndex) else this
}

/** What to frame of a ride from a board: its first stop and the next few, and the vehicle if it's on its way. */
private fun TripDetail.rideFrame(vehicle: LatLon?): List<LatLon> {
    val from = boarded?.first ?: return emptyList()
    val ahead = stops.subList(from, (from + FRAME_STOPS).coerceAtMost(stops.size)).mapNotNull { it.call.position }
    return ahead + listOfNotNull(vehicle)
}

/**
 * A vehicle and where it's going next: the stops ahead of it within reach, so the map shows its
 * way on rather than the vehicle alone in the middle, half the view empty (the other vehicles are
 * hidden while a trip is open). Null when there's nothing ahead worth framing: its next stop is
 * far off, or it's about to reach its last.
 */
internal fun TripDetail.aheadFrame(vehicle: LatLon): List<LatLon>? {
    val now = Instant.now()
    val next =
        stops.indexOfFirst { stop ->
            val passed = stop.passed ?: ((stop.call.departure ?: stop.call.arrival)?.expected?.isBefore(now) == true)
            !passed
        }
    if (next < 0) return null
    val ahead =
        stops
            .drop(next)
            .take(FRAME_STOPS)
            .mapNotNull { it.call.position }
            .takeWhile { it.metresTo(vehicle) <= FRAME_REACH_M }
    val far = ahead.maxOfOrNull { it.metresTo(vehicle) } ?: return null
    return if (far < FRAME_LEAST_M) null else listOf(vehicle) + ahead
}

/** How many stops of a ride from a board the map takes in at first. */
private const val FRAME_STOPS = 6

/** How far ahead of a vehicle its stops are framed; past this, the map would zoom out too far to see it. */
private const val FRAME_REACH_M = 6_000.0

/** Less than this ahead isn't worth framing: the map would zoom right in. */
private const val FRAME_LEAST_M = 500.0

/** How far a board's stop may be from the trip's own call there (platforms, a stop place's middle). */
private const val BOARD_REACH_M = 400.0

/** What the map is about, as far as framing it goes: a change of it moves the camera. */
private sealed interface Focus {
    data class Stop(
        val id: String,
    ) : Focus

    /** A vehicle; [framed] once its trip's stops are in, and where it's going can be shown. */
    data class Vehicle(
        val id: String,
        val framed: Boolean,
    ) : Focus

    /** A ride from a board; [framed] once its stops are in and it can be shown whole. */
    data class Ride(
        val trip: TripRef?,
        val framed: Boolean,
    ) : Focus
}

private fun MapUiState.focus(): Focus? {
    val selection = selection
    return when {
        selection?.from != null -> Focus.Ride(selection.trip, selection.detail?.boarded != null)
        selection?.vehicleId != null -> Focus.Vehicle(selection.vehicleId, selection.detail != null)
        selection != null -> Focus.Ride(selection.trip, framed = false)
        stop != null -> Focus.Stop(stop.stop.id)
        else -> null
    }
}

/** What railviz was asked for: [area] at [zoom], good [until]. */
private data class RailvizWindow(
    val area: MapBounds? = null,
    val zoom: Int = -1,
    val until: Instant = Instant.EPOCH,
    val segments: List<RailvizSegment> = emptyList(),
) {
    fun serves(
        view: MapBounds,
        viewZoom: Double,
        now: Instant,
    ): Boolean = area != null && area.covers(view) && zoom == viewZoom.toInt() && now.isBefore(until)
}

private inline fun <T> runCatchingNotCancelled(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
