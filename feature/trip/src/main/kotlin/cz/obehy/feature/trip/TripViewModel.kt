package cz.obehy.feature.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import cz.obehy.core.data.TripRepository
import cz.obehy.core.data.TripRequest
import cz.obehy.core.model.Composition
import cz.obehy.core.model.CompositionStatus
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How many pending answers to wait through, about a minute at the API's pace, as the web does. */
private const val COMPOSITION_PATIENCE = 20
private const val COMPOSITION_DEFAULT_WAIT_S = 3L
private const val MILLIS_PER_SECOND = 1_000L

internal data class TripState(
    val motisTripId: String,
    val title: String,
    val trip: TripDetail? = null,
    val composition: Composition? = null,
    /** The user asked for fresh times and they're on their way. */
    val refreshing: Boolean = false,
    val failed: Boolean = false,
    /** The maps stay on the vehicle. */
    val following: Boolean = false,
)

@HiltViewModel
internal class TripViewModel
    @Inject
    constructor(
        saved: SavedStateHandle,
        private val trips: TripRepository,
    ) : ViewModel() {
        private val route = saved.toRoute<TripRoute>()
        private val request = TripRequest(route.motisTripId, route.fromStopId, route.toStopId)
        private val ahead = trips.fetchedAhead(request)
        private val local = MutableStateFlow(TripState(route.motisTripId, route.title, trip = ahead))
        private var composition: Job? = null
        private var updating = false

        val state: StateFlow<TripState> = local.asStateFlow()

        init {
            load()
        }

        /** The plain trip unless it's here already, then what's only known once a trip is opened. */
        fun load() {
            local.update { it.copy(failed = false) }
            viewModelScope.launch {
                val plain = trips.plain(request).getOrNull()
                if (plain == null) {
                    local.update { it.copy(failed = true) }
                    return@launch
                }
                local.update { it.copy(trip = plain) }
                val detailed = trips.withDetails(plain, request)
                local.update { it.copy(trip = detailed) }
                val info = detailed.info
                when {
                    info == null -> if (plain === ahead) refresh(asked = false)
                    info.hasComposition -> watchComposition(info)
                }
            }
        }

        /** Fresh times; [asked] when the user pulled for them, not the screen's own timer. */
        fun refresh(asked: Boolean = true) {
            val trip = local.value.trip ?: return
            if (updating) return
            updating = true
            local.update { it.copy(refreshing = asked) }
            viewModelScope.launch {
                val fresh = trips.refresh(trip, route.motisTripId).getOrNull()
                updating = false
                local.update { it.copy(trip = fresh ?: it.trip, refreshing = false) }
            }
        }

        /** Where the vehicles are, on its own: a map following one wants this more often than fresh times. */
        fun refreshPositions() {
            val trip = local.value.trip ?: return
            viewModelScope.launch {
                val fresh = trips.positions(trip)
                local.update { state -> state.copy(trip = state.trip?.copy(info = fresh.info)) }
            }
        }

        fun follow(following: Boolean) = local.update { it.copy(following = following) }

        /** The API asks its source lazily: an answer on its way is asked for again at its pace. */
        private fun watchComposition(info: TripInfo) {
            composition?.cancel()
            composition =
                viewModelScope.launch {
                    repeat(COMPOSITION_PATIENCE) {
                        val answer = trips.composition(info).getOrNull()
                        local.update { it.copy(composition = answer ?: it.composition) }
                        if (answer == null || !answer.checking) return@launch
                        delay((answer.retryAfterSeconds?.toLong() ?: COMPOSITION_DEFAULT_WAIT_S) * MILLIS_PER_SECOND)
                    }
                    // Given up: an older answer still shows, a mere placeholder doesn't.
                    local.update { state ->
                        state.copy(
                            composition = state.composition?.takeUnless { it.status == CompositionStatus.Pending },
                        )
                    }
                }
        }
    }
