package cz.obehy.feature.trip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cz.obehy.core.map.FollowButton
import cz.obehy.core.map.FullRouteMap
import cz.obehy.core.map.GLIDE_MS
import cz.obehy.core.map.HeroFocus
import cz.obehy.core.map.MapFraming
import cz.obehy.core.map.MapVehicle
import cz.obehy.core.map.RouteMap
import cz.obehy.core.map.followZoom
import cz.obehy.core.map.mapVehicles
import cz.obehy.core.model.MarkedNote
import cz.obehy.core.model.Mode
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripLinks
import cz.obehy.core.ui.ObehyHeroScreen
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.SharedBounds
import cz.obehy.core.ui.components.DelayLabel
import cz.obehy.core.ui.components.LineBadge
import cz.obehy.core.ui.components.NoticeCard
import cz.obehy.core.ui.components.StateMessage
import cz.obehy.core.ui.components.featureIcon
import cz.obehy.core.ui.components.rememberNow
import cz.obehy.core.ui.icon
import cz.obehy.core.ui.rideMorphKey
import kotlinx.coroutines.delay
import java.time.Instant

private val TRAINS =
    setOf(Mode.RegionalRail, Mode.SuburbanRail, Mode.LongDistanceRail, Mode.HighSpeedRail, Mode.NightRail)

/** How often the estimate of where a vehicle is moves on; it slides from one to the next without a stop. */
private const val ESTIMATE_EVERY_MS = 2_000L

/** How often an open trip asks for fresh times while it is on screen. */
private const val REFRESH_EVERY_MS = 30_000L

/** How often a map following a vehicle asks where it is. */
private const val FOLLOW_EVERY_MS = 10_000L

/** While it's on screen, a trip is kept fresh; closer still when a map is [following] its vehicle. */
@Composable
private fun KeepFresh(
    following: Boolean,
    onRefresh: () -> Unit,
    onRefreshPositions: () -> Unit,
) {
    val refresh by rememberUpdatedState(onRefresh)
    val refreshPositions by rememberUpdatedState(onRefreshPositions)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(REFRESH_EVERY_MS)
                refresh()
            }
        }
    }
    LaunchedEffect(lifecycle, following) {
        if (!following) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(FOLLOW_EVERY_MS)
                refreshPositions()
            }
        }
    }
}

/** What a trip's maps show right now. */
private data class TripView(
    val vehicles: List<MapVehicle> = emptyList(),
    val framing: MapFraming = MapFraming.Whole,
    val glideMillis: Int = GLIDE_MS,
)

/** [TripView]s as the clock moves the estimate of where a vehicle is on, and as [trip] changes. */
@Composable
private fun tripView(
    trip: TripDetail?,
    following: Boolean,
): State<TripView> {
    val now = rememberNow(ESTIMATE_EVERY_MS)
    return remember(trip, following) {
        derivedStateOf {
            val vehicles = trip?.mapVehicles(now.value).orEmpty()
            val glide = if (vehicles.firstOrNull()?.at?.estimated == true) ESTIMATE_EVERY_MS.toInt() else GLIDE_MS
            val followed =
                vehicles.firstOrNull()?.takeIf { following }
                    ?: return@derivedStateOf TripView(vehicles, glideMillis = glide)
            TripView(vehicles, MapFraming.Follow(followed.at.position, followZoom(followed.ride.mode)), glide)
        }
    }
}

@Composable
internal fun TripDestination(
    onBack: () -> Unit,
    onMap: () -> Unit,
    viewModel: TripViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    KeepFresh(state.following, { viewModel.refresh(asked = false) }, viewModel::refreshPositions)
    TripScreen(
        state,
        onBack,
        onMap,
        onFollow = viewModel::follow,
        onRefresh = { viewModel.refresh() },
        onRetry = viewModel::load,
    )
}

/** The trip on a map of its own. */
@Composable
internal fun TripMapDestination(
    viewModel: TripViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    KeepFresh(state.following, { viewModel.refresh(asked = false) }, viewModel::refreshPositions)
    val trip = state.trip
    val view by tripView(trip, state.following)
    if (trip == null) {
        val back by rememberUpdatedState(onBack)
        LaunchedEffect(Unit) { back() }
        return
    }
    SharedBounds(mapKey(state), Modifier.fillMaxSize()) {
        FullRouteMap(
            trip.map.legs,
            onBack,
            around = trip.map.around,
            vehicles = view.vehicles,
            glideMillis = view.glideMillis,
            framing = view.framing,
            onGesture = { viewModel.follow(false) },
        ) {
            if (view.vehicles.isNotEmpty()) FollowButton(state.following, viewModel::follow)
        }
    }
}

/** The header map and the full-screen map are one thing, morphing between the two screens. */
private fun mapKey(state: TripState): String = "map:trip:" + state.motisTripId

/** One trip whole, grown out of its ride: where it runs, then its stops, the train, and the timetable's notes. */
@Composable
internal fun TripScreen(
    state: TripState,
    onBack: () -> Unit,
    onMap: () -> Unit,
    onFollow: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip
    val view = tripView(trip, state.following)
    ObehyHeroScreen(
        title = state.title,
        onBack = onBack,
        hero = { shrunk ->
            SharedBounds(mapKey(state), Modifier.fillMaxSize()) { TripMap(trip, view.value, shrunk) }
        },
        modifier = modifier,
        onHeroClick = onMap.takeIf { trip != null },
        header = trip?.let { { TripHeader(it) } },
        refreshing = state.refreshing,
        onRefresh = if (trip != null) onRefresh else null,
        contentMorphKey = rideMorphKey(state.motisTripId),
        actions = {
            if (view.value.vehicles.isNotEmpty()) FollowButton(state.following, onFollow)
            trip?.ride?.links?.let { TripLinksMenu(it) }
        },
    ) {
        when {
            trip != null -> {
                TripContent(state, trip)
            }

            state.failed -> {
                Failed(onRetry)
            }

            else -> {
                Box(Modifier.fillMaxWidth().padding(ObehyTheme.spacing.xl), Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}

@Composable
private fun TripMap(
    trip: TripDetail?,
    view: TripView,
    shrunk: () -> Float,
    modifier: Modifier = Modifier,
) {
    if (trip == null) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
        return
    }
    RouteMap(
        trip.map.legs,
        modifier.fillMaxSize(),
        around = trip.map.around,
        vehicles = view.vehicles,
        glideMillis = view.glideMillis,
        framing = view.framing,
        shrunk = shrunk,
        focus = HeroFocus.Arrive,
    )
}

@Composable
private fun TripContent(
    state: TripState,
    trip: TripDetail,
    modifier: Modifier = Modifier,
) {
    val info = trip.info
    Column(modifier.fillMaxWidth()) {
        Arriving(trip.alerts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s)) {
                trip.alerts.forEach { NoticeCard(it.title, text = it.text, severe = it.severe) }
            }
        }
        Schedule(trip, Modifier.padding(top = ObehyTheme.spacing.s))
        Arriving(hasTrainSection(trip, state.composition)) { TrainSection(trip, state.composition) }
        Arriving(info?.notes?.isEmpty == false) { info?.let { NotesSection(it.notes) } }
        Arriving(info?.connections?.isNotEmpty() == true) { info?.let { ConnectionsSection(it.connections) } }
    }
}

/** A section that comes later than the rest of the screen, unfolding when it does. */
@Composable
private fun ColumnScope.Arriving(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible,
        modifier,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
    ) {
        Box(Modifier.padding(top = ObehyTheme.spacing.l)) { content() }
    }
}

/** On the sheet over the map, so it stays in sight: which trip this is, where it goes, and what it offers. */
@Composable
private fun TripHeader(
    trip: TripDetail,
    modifier: Modifier = Modifier,
) {
    val ride = trip.ride
    val spacing = ObehyTheme.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(ride)
            DelayLabel(trip.delayMinutes)
        }
        ride.headsign?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A train's number means something to riders; a bus's is just line and trip (`100195 1224`).
            val number = ride.tripShortName?.takeIf { ride.mode in TRAINS && it !in ride.name }
            ride.agency?.let {
                Text(
                    it,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            number?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            trip.attributes.forEach { attribute ->
                featureIcon(attribute.icon)?.let {
                    Icon(
                        it,
                        contentDescription = attribute.text.ifEmpty { null } ?: attribute.fallbackText(),
                        modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (trip.cancelled) {
            Text(
                stringResource(R.string.trip_cancelled),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The feed's links for a trip, tucked away on the map: only there at all when it has any. */
@Composable
private fun TripLinksMenu(
    links: TripLinks,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty) return
    var open by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    @Composable
    fun Link(
        url: String?,
        text: Int,
    ) {
        url ?: return
        DropdownMenuItem(
            text = { Text(stringResource(text)) },
            onClick = {
                open = false
                uri.openUri(url)
            },
        )
    }
    Box(modifier) {
        FilledTonalIconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.trip_links))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Link(links.tickets, R.string.trip_link_tickets)
            Link(links.line, R.string.trip_link_line)
            Link(links.operator, R.string.trip_link_operator)
        }
    }
}

/** What MOTIS says of a trip comes without a text of its own. */
@Composable
private fun MarkedNote.fallbackText(): String =
    stringResource(
        when (icon) {
            "bicycle" -> R.string.trip_bikes
            "reservation_required" -> R.string.trip_reservation_required
            "reservation" -> R.string.trip_reservation
            else -> R.string.vehicle_low_floor
        },
    )

@Composable
private fun Failed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StateMessage(
        Icons.Rounded.CloudOff,
        stringResource(R.string.trip_failed),
        modifier,
        action = stringResource(R.string.trip_retry),
        onAction = onRetry,
    )
}
