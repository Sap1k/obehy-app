package cz.obehy.feature.planner

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
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

/** The open journey on a full-screen map. */
@Serializable
internal data object JourneyMapRoute

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
            AnimatedDestination(this) {
                ItineraryDestination(
                    hiltViewModel(graph),
                    onBack = { nav.popBackStack() },
                    onMap = { nav.navigate(JourneyMapRoute) },
                )
            }
        }
        composable<JourneyMapRoute> { entry ->
            val graph = remember(entry) { nav.getBackStackEntry<PlannerRoute>() }
            JourneyMapDestination(hiltViewModel(graph), onBack = { nav.popBackStack() })
        }
    }
}
