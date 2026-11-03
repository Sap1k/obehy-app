package cz.obehy.app

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DepartureBoard
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cz.obehy.core.model.AppMode
import cz.obehy.core.ui.ComingSoon
import cz.obehy.core.ui.SharedMotion
import cz.obehy.feature.onboarding.SettingsRoute
import cz.obehy.feature.onboarding.settingsScreen
import cz.obehy.feature.planner.PlannerRoute
import cz.obehy.feature.planner.plannerScreen
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
)

/** The top-level destinations per mode: the live map only in Enhanced mode. */
private fun destinations(mode: AppMode): List<TopLevel> =
    buildList {
        add(TopLevel(PlannerRoute, PlannerRoute::class, R.string.nav_journeys, Icons.Outlined.Directions))
        add(TopLevel(DeparturesRoute, DeparturesRoute::class, R.string.nav_departures, Icons.Outlined.DepartureBoard))
        if (mode == AppMode.Enhanced) {
            add(TopLevel(MapRoute, MapRoute::class, R.string.nav_map, Icons.Outlined.Map))
        }
        add(TopLevel(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Outlined.Settings))
    }

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
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationItems = {
            destinations(mode).forEach { top ->
                NavigationSuiteItem(
                    selected = current?.hierarchy?.any { it.hasRoute(top.routeClass) } == true,
                    onClick = {
                        nav.navigate(top.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(top.icon, contentDescription = null) },
                    label = { Text(stringResource(top.label)) },
                )
            }
        },
    ) {
        // Screens fade through each other; a journey's card grows into its detail on top of that
        // (SharedBounds), and the back gesture plays it in reverse as you drag.
        val fadeIn = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
        val fadeOut = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
        SharedMotion {
            NavHost(
                nav,
                startDestination = PlannerRoute,
                enterTransition = { fadeIn },
                exitTransition = { fadeOut },
                popEnterTransition = { fadeIn },
                popExitTransition = { fadeOut },
            ) {
                plannerScreen(nav)
                tripScreen()
                settingsScreen()
                composable<DeparturesRoute> { ComingSoon(stringResource(R.string.nav_departures)) }
                composable<MapRoute> { ComingSoon(stringResource(R.string.nav_map)) }
            }
        }
    }
}
