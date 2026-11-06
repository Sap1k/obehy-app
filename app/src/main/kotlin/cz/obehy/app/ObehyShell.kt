package cz.obehy.app

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DepartureBoard
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.DepartureBoard
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cz.obehy.core.model.AppMode
import cz.obehy.core.ui.BACK_MS
import cz.obehy.core.ui.BackEasing
import cz.obehy.core.ui.ComingSoon
import cz.obehy.core.ui.HoldDuringMorph
import cz.obehy.core.ui.SharedMotion
import cz.obehy.feature.onboarding.SettingsRoute
import cz.obehy.feature.onboarding.settingsScreen
import cz.obehy.feature.planner.JourneyMapRoute
import cz.obehy.feature.planner.PlannerRoute
import cz.obehy.feature.planner.plannerScreen
import cz.obehy.feature.trip.TripMapRoute
import cz.obehy.feature.trip.TripRoute
import cz.obehy.feature.trip.navigateToTrip
import cz.obehy.feature.trip.tripScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data object DeparturesRoute

@Serializable
data object MapRoute

private data class TopLevel(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/** The top-level destinations per mode: the live map comes from Oběhy. */
private fun destinations(mode: AppMode): List<TopLevel> =
    buildList {
        add(
            TopLevel(
                PlannerRoute,
                PlannerRoute::class,
                R.string.nav_journeys,
                Icons.Outlined.Directions,
                Icons.Rounded.Directions,
            ),
        )
        add(
            TopLevel(
                DeparturesRoute,
                DeparturesRoute::class,
                R.string.nav_departures,
                Icons.Outlined.DepartureBoard,
                Icons.Rounded.DepartureBoard,
            ),
        )
        if (mode.usesObehy) {
            add(TopLevel(MapRoute, MapRoute::class, R.string.nav_map, Icons.Outlined.Map, Icons.Rounded.Map))
        }
        add(
            TopLevel(
                SettingsRoute,
                SettingsRoute::class,
                R.string.nav_settings,
                Icons.Outlined.Settings,
                Icons.Rounded.Settings,
            ),
        )
    }

/** A screen that grows out of something on the one before, which stays put under it. */
private val NavDestination.morphsIn: Boolean
    get() = isFullMap || hasRoute(TripRoute::class)

/** A map filling the window, the navigation bar out of its way. */
private val NavDestination.isFullMap: Boolean
    get() = hasRoute(JourneyMapRoute::class) || hasRoute(TripMapRoute::class)

/**
 * The app's frame in Material 3 Expressive: the short bottom bar on phones, the wide navigation
 * rail on tablets and other wide windows (NavigationSuiteScaffold picks by window size).
 */
@Composable
fun ObehyShell(
    mode: AppMode,
    modifier: Modifier = Modifier,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    val onMap = current?.isFullMap == true
    val bar = rememberNavigationSuiteScaffoldState()
    // Lime like anything else that's selected. A label lies on it only beside its icon.
    val tabColors =
        ShortNavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
            selectedTextColorStartIconPosition = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
        )
    // The bar slides away for the map, and back along with its shrinking into the journey.
    LaunchedEffect(onMap) { if (onMap) bar.hide() else bar.show() }
    NavigationSuiteScaffold(
        modifier = modifier,
        state = bar,
        navigationItems = {
            destinations(mode).forEach { top ->
                // A trip is opened from a journey, so the journeys tab stays lit under it.
                val selected =
                    current?.hierarchy?.any { it.hasRoute(top.routeClass) } == true ||
                        (top.routeClass == PlannerRoute::class && current?.hasRoute(TripRoute::class) == true)
                NavigationSuiteItem(
                    colors = tabColors,
                    selected = selected,
                    onClick = {
                        nav.navigate(top.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                    label = { Text(stringResource(top.label)) },
                )
            }
        },
    ) {
        // Screens fade through each other. A journey's card grows into its detail and the header
        // map into the full map on top of that (SharedBounds), and the back gesture plays those
        // morphs in reverse as you drag, so it has no animation of its own to add.
        val fadeIn = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
        val fadeOut = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
        SharedMotion {
            NavHost(
                nav,
                startDestination = PlannerRoute,
                enterTransition = {
                    if (targetState.destination.isFullMap) {
                        EnterTransition.None
                    } else if (targetState.destination.hasRoute(TripRoute::class)) {
                        fadeIn(tween(BACK_MS, easing = BackEasing))
                    } else {
                        fadeIn
                    }
                },
                // The journey stays behind its map as it grows to fill the screen.
                exitTransition = {
                    if (targetState.destination.morphsIn) {
                        HoldDuringMorph
                    } else {
                        fadeOut
                    }
                },
                popEnterTransition = { fadeIn },
                popExitTransition = { fadeOut },
                predictivePopEnterTransition = { EnterTransition.None },
                predictivePopExitTransition = { fadeOut(tween(BACK_MS, easing = BackEasing)) },
            ) {
                plannerScreen(nav) { ride ->
                    ride.tripId?.let { nav.navigateToTrip(TripRoute(it, ride.name, ride.from.stopId, ride.to.stopId)) }
                }
                tripScreen(nav)
                settingsScreen()
                composable<DeparturesRoute> { ComingSoon(stringResource(R.string.nav_departures)) }
                composable<MapRoute> { ComingSoon(stringResource(R.string.nav_map)) }
            }
            // Tabs are peers, with nothing to morph into: leaving one back to the start is the
            // same fade as switching tabs, once the gesture is done, with no preview to drag.
            val inOtherTab =
                destinations(mode).any { top ->
                    top.routeClass != PlannerRoute::class && current?.hasRoute(top.routeClass) == true
                }
            BackHandler(enabled = inOtherTab) { nav.popBackStack() }
        }
    }
}
