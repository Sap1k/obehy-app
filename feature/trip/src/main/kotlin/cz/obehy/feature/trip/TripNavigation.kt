package cz.obehy.feature.trip

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

fun NavController.navigateToTrip(route: TripRoute) = navigate(route)

fun NavGraphBuilder.tripScreen(nav: NavController) {
    composable<TripRoute> {
        AnimatedDestination(this) { TripDestination(onBack = { nav.popBackStack() }) }
    }
}
