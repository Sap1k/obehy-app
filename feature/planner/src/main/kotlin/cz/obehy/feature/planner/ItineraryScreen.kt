package cz.obehy.feature.planner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cz.obehy.core.map.RouteMap
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.ui.ObehyHeroScreen
import cz.obehy.core.ui.ObehyScreen
import cz.obehy.core.ui.SharedBounds
import cz.obehy.core.ui.TitleBar
import cz.obehy.core.ui.components.clock
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** How often an open journey asks for fresh times while it is on screen. */
private val REFRESH_EVERY: Duration = Duration.ofMinutes(1)

@Composable
internal fun ItineraryDestination(
    viewModel: PlannerViewModel,
    onBack: () -> Unit,
    onMap: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = state.open
    if (open == null) {
        // The search was cleared (or the app restarted): nothing left to show.
        val back by rememberUpdatedState(onBack)
        LaunchedEffect(Unit) { back() }
        return
    }
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
    ItineraryScreen(open, state.query, onBack, onMap, onRefresh = { viewModel.act(PlannerAction.Refresh) })
}

/** One journey: its map at the head, collapsing into the bar as the timeline scrolls. */
@Composable
internal fun ItineraryScreen(
    open: OpenJourney,
    query: JourneyQuery,
    onBack: () -> Unit,
    onMap: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itinerary = open.itinerary
    SharedBounds(itinerary.key, modifier) {
        ObehyHeroScreen(
            title = stringResource(R.string.times, itinerary.start.expected.clock(), itinerary.end.expected.clock()),
            subtitle = journeySummary(open),
            onBack = onBack,
            hero = { RouteMap(itinerary.legs, Modifier.fillMaxSize()) },
            onHeroClick = onMap,
            refreshing = open.refreshing,
            onRefresh = onRefresh,
        ) {
            ItineraryTimeline(itinerary, query.from?.name, query.to?.name)
        }
    }
}

@Composable
internal fun JourneyMapDestination(
    viewModel: PlannerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = state.open ?: return
    ObehyScreen(stringResource(R.string.map_title), titleBar = TitleBar.Small, onBack = onBack, scrollable = false) {
        RouteMap(open.itinerary.legs, Modifier.fillMaxSize(), interactive = true)
    }
}

/** "3 h 19 min · 2 changes · 1 min ago", or how long ago a refresh last worked. */
@Composable
internal fun journeySummary(open: OpenJourney): String =
    listOf(
        open.itinerary.duration.spoken(),
        transfersText(open.itinerary.transfers),
        if (open.failed) stringResource(R.string.refresh_failed) else ago(open.fetchedAt),
    ).joinToString(" · ")

/** "Updated 3 min ago" for the results, ticking on its own. */
@Composable
internal fun updatedAgo(fetchedAt: Instant): String = stringResource(R.string.updated, ago(fetchedAt))

/** "3 min ago" / "just now", kept current every half minute. */
@Composable
private fun ago(at: Instant): String {
    val now by produceState(Instant.now(), at) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }
    val age = Duration.between(at, now)
    return if (age.toMinutes() < 1) stringResource(R.string.just_now) else stringResource(R.string.ago, age.spoken())
}
