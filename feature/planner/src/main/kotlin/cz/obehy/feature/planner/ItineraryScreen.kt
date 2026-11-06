package cz.obehy.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cz.obehy.core.map.FullRouteMap
import cz.obehy.core.map.HeroFocus
import cz.obehy.core.map.MapFraming
import cz.obehy.core.map.MapVehicle
import cz.obehy.core.map.PreloadRouteMaps
import cz.obehy.core.map.RouteMap
import cz.obehy.core.map.WholeJourneyButton
import cz.obehy.core.map.heldVehiclePlace
import cz.obehy.core.map.heroFraming
import cz.obehy.core.map.mapVehicles
import cz.obehy.core.map.takingIn
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.RouteSurroundings
import cz.obehy.core.model.focusAt
import cz.obehy.core.ui.ObehyHeroScreen
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.SharedBounds
import cz.obehy.core.ui.components.clock
import cz.obehy.core.ui.components.rememberNow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** How often an open journey asks for fresh times while it is on screen. */
private val REFRESH_EVERY: Duration = Duration.ofMinutes(1)

/** How often its maps catch up with the clock. */
private const val VIEW_EVERY_MS = 10_000L

/** How often "3 min ago" is brought up to date. */
private const val AGO_EVERY_MS = 30_000L

@Composable
internal fun ItineraryDestination(
    viewModel: PlannerViewModel,
    covered: Boolean,
    onBack: () -> Unit,
    onMap: () -> Unit,
    onOpenTrip: (Leg.Transit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.restoring) return
    val open = state.open
    if (open == null) {
        // The search was cleared, or the app was killed and its journey is gone: nothing left to show.
        val back by rememberUpdatedState(onBack)
        LaunchedEffect(Unit) { back() }
        return
    }
    // The trips of its rides, so that opening one finds its map drawn.
    val tripMaps by rememberUpdatedState(state.tripMaps)
    PreloadRouteMaps(
        around = { legs -> tripMaps.firstOrNull { it.legs === legs }?.around },
    ) { tripMaps.map { it.legs } }
    // While the journey is on screen, its times are kept at most a minute old.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val fetchedAt by rememberUpdatedState(open.fetchedAt)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val age = Duration.between(fetchedAt, Instant.now())
                if (age >= REFRESH_EVERY) viewModel.act(PlannerAction.Refresh)
                delay((REFRESH_EVERY - age).coerceIn(Duration.ofSeconds(5), REFRESH_EVERY).toMillis())
            }
        }
    }
    ItineraryScreen(
        open,
        state.query,
        journeyView(viewModel, state, open),
        covered,
        onBack,
        onMap,
        onRefresh = { viewModel.act(PlannerAction.Refresh) },
        onWhole = { viewModel.act(PlannerAction.ShowWholeJourney(it)) },
        onOpenTrip = onOpenTrip,
        onReplan = { viewModel.act(PlannerAction.Replan) },
    )
}

/** How a journey's maps look right now: where they frame, and what's on its way to you. */
internal data class JourneyView(
    val framing: MapFraming = MapFraming.Whole,
    /** There's a part of the journey that matters more now, whether it's shown or not. */
    val focused: Boolean = false,
    val around: RouteSurroundings? = null,
    val vehicles: List<MapVehicle> = emptyList(),
)

/** What [open]'s maps show as time goes by, watching the ride that matters while on screen. */
@Composable
private fun journeyView(
    viewModel: PlannerViewModel,
    state: PlannerState,
    open: OpenJourney,
): JourneyView {
    val legs = open.itinerary.legs
    val now by rememberNow(VIEW_EVERY_MS)
    val focus = remember(legs, now) { legs.focusAt(now) }
    val ride = focus?.ride
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val watching by rememberUpdatedState(ride)
    LaunchedEffect(lifecycle, ride?.tripId) {
        val wanted = watching ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.watchRide(wanted) }
    }
    val base =
        remember(legs, focus, state.wholeJourney) {
            if (state.wholeJourney) MapFraming.Whole else heroFraming(legs, now)
        }
    val trip = state.watched?.takeIf { ride != null && it.ride.tripId == ride.tripId }
    val waiting = trip != null && ride != null && now < ride.start.expected
    val around =
        remember(trip?.map, waiting) {
            trip?.map?.around?.takeIf { waiting && it.approach.size >= 2 }?.let {
                RouteSurroundings(approach = it.approach, approachColor = it.approachColor)
            }
        }
    val vehicles = remember(trip, now) { trip?.mapVehicles(now).orEmpty() }
    // While you wait, what you're waiting for is worth seeing, if that doesn't lose the rest.
    val boarding = ride?.from?.position
    val coming =
        vehicles
            .firstOrNull()
            ?.takeIf { waiting }
            ?.at
            ?.position
    val held = remember(ride?.tripId) { arrayOfNulls<LatLon>(1) }
    val place = remember(held, coming) { heldVehiclePlace(held[0], coming, boarding).also { held[0] = it } }
    val framing = remember(base, place, boarding) { base.takingIn(place, boarding) }
    val partOfIt = focus != null && focus.legs.size < legs.size
    return JourneyView(framing, focused = partOfIt, around, vehicles)
}

/** One journey: its map at the head, collapsing into the bar as the timeline scrolls. */
@Composable
internal fun ItineraryScreen(
    open: OpenJourney,
    query: JourneyQuery,
    view: JourneyView,
    covered: Boolean,
    onBack: () -> Unit,
    onMap: () -> Unit,
    onRefresh: () -> Unit,
    onWhole: (Boolean) -> Unit,
    onOpenTrip: (Leg.Transit) -> Unit,
    onReplan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itinerary = open.itinerary
    val title = stringResource(R.string.times, itinerary.start.expected.clock(), itinerary.end.expected.clock())
    SharedBounds(itinerary.key, modifier, holdOnExit = covered) {
        ObehyHeroScreen(
            title = title,
            onBack = onBack,
            hero = { shrunk ->
                SharedBounds(mapKey(open), Modifier.fillMaxSize()) {
                    RouteMap(
                        itinerary.legs,
                        Modifier.fillMaxSize(),
                        around = view.around,
                        vehicles = view.vehicles,
                        framing = view.framing,
                        shrunk = shrunk,
                        focus = HeroFocus.Leave,
                    )
                }
            },
            onHeroClick = onMap,
            refreshing = open.refreshing,
            onRefresh = onRefresh,
            actions = {
                if (view.focused) WholeJourneyButton(view.framing == MapFraming.Whole, onWhole)
            },
            header = { JourneyHeader(open, title, query.to?.label(), onReplan) },
        ) {
            ItineraryTimeline(itinerary, query.from?.label(), query.to?.label(), onOpenTrip)
        }
    }
}

/** The header map and the full-screen map are one thing, morphing between the two screens. */
private fun mapKey(open: OpenJourney): String = "map:" + open.itinerary.key

@Composable
internal fun JourneyMapDestination(
    viewModel: PlannerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.restoring) return
    val open = state.open
    if (open == null) {
        val back by rememberUpdatedState(onBack)
        LaunchedEffect(Unit) { back() }
        return
    }
    val view = journeyView(viewModel, state, open)
    // Edge to edge, so it leaves ObehyScreen's frame.
    SharedBounds(mapKey(open), Modifier.fillMaxSize()) {
        FullRouteMap(
            open.itinerary.legs,
            onBack,
            around = view.around,
            vehicles = view.vehicles,
            framing = view.framing,
        ) {
            if (view.focused) {
                WholeJourneyButton(
                    view.framing == MapFraming.Whole,
                    { viewModel.act(PlannerAction.ShowWholeJourney(it)) },
                )
            }
        }
    }
}

/** How long a journey takes, its changes, and at the far end how fresh this all is, if it could be refreshed. */
@Composable
internal fun JourneySummary(
    open: OpenJourney,
    modifier: Modifier = Modifier,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(open.itinerary.duration.spoken(), style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
        Text(
            transfersText(open.itinerary.transfers),
            style = MaterialTheme.typography.bodySmall,
            color = quiet,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (open.failed) stringResource(R.string.refresh_failed) else ago(open.fetchedAt),
            style = MaterialTheme.typography.bodySmall,
            color = if (open.failed) MaterialTheme.colorScheme.error else quiet,
            maxLines = 1,
        )
    }
}

/** "Updated 3 min ago" for the results, ticking on its own. */
@Composable
internal fun updatedAgo(fetchedAt: Instant): String = stringResource(R.string.updated, ago(fetchedAt))

/** "3 min ago" / "just now", kept current every half minute. */
@Composable
private fun ago(at: Instant): String {
    val now by rememberNow(AGO_EVERY_MS)
    val age = Duration.between(at, now)
    return if (age.toMinutes() < 1) stringResource(R.string.just_now) else stringResource(R.string.ago, age.spoken())
}
