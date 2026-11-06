package cz.obehy.feature.trip

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.obehy.core.ui.AnimatedDestination
import kotlinx.serialization.Serializable

/**
 * A trip's detail, opened from a ride on a journey: [motisTripId] and the MOTIS stops it's ridden
 * between. [title] names the line until the trip arrives.
 */
@Serializable
data class TripRoute(
    val motisTripId: String,
    val title: String,
    val fromStopId: String? = null,
    val toStopId: String? = null,
)

/** The open trip on a full-screen map; the app's navigation bar makes way for it. */
@Serializable
data object TripMapRoute

fun NavController.navigateToTrip(route: TripRoute) = navigate(route)

fun NavGraphBuilder.tripScreen(nav: NavController) {
    composable<TripRoute> {
        AnimatedDestination(this) {
            TripDestination(onBack = { nav.popBackStack() }, onMap = { nav.navigate(TripMapRoute) })
        }
    }
    composable<TripMapRoute> { entry ->
        val trip = remember(entry) { nav.getBackStackEntry<TripRoute>() }
        AnimatedDestination(this) { TripMapDestination(hiltViewModel(trip), onBack = { nav.popBackStack() }) }
    }
}
