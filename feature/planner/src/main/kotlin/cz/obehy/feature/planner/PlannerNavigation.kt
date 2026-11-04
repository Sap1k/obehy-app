package cz.obehy.feature.planner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import cz.obehy.core.ui.AnimatedDestination
import kotlinx.serialization.Serializable

/** The journey planner: the top-level destination the app starts on. */
@Serializable
data object PlannerRoute

@Serializable
internal data object SearchRoute

/** The open journey ([PlannerState.open]). */
@Serializable
internal data object ItineraryRoute

/** The open journey on a full-screen map; the app's navigation bar makes way for it. */
@Serializable
data object JourneyMapRoute

/**
 * Journey search, results, a journey's detail and its map (PLAN.md, milestone 1). The screens
 * share one view model, scoped to the planner graph, so the detail shows the journey picked.
 */
fun NavGraphBuilder.plannerScreen(nav: NavController) {
    navigation<PlannerRoute>(startDestination = SearchRoute) {
        composable<SearchRoute> { entry ->
            val graph = remember(entry) { nav.getBackStackEntry<PlannerRoute>() }
            AnimatedDestination(this) {
                PlannerDestination(hiltViewModel(graph), onOpen = { nav.navigate(ItineraryRoute) })
            }
        }
        composable<ItineraryRoute> { entry ->
            val graph = remember(entry) { nav.getBackStackEntry<PlannerRoute>() }
            val top by nav.currentBackStackEntryAsState()
            AnimatedDestination(this) {
                ItineraryDestination(
                    hiltViewModel(graph),
                    mapOpen = top?.destination?.hasRoute<JourneyMapRoute>() == true,
                    onBack = { nav.popBackStack() },
                    onMap = { nav.navigate(JourneyMapRoute) },
                )
            }
        }
        composable<JourneyMapRoute> { entry ->
            val graph = remember(entry) { nav.getBackStackEntry<PlannerRoute>() }
            AnimatedDestination(this) {
                JourneyMapDestination(hiltViewModel(graph), onBack = { nav.popBackStack() })
            }
        }
    }
}
