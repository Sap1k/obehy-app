package cz.obehy.feature.trip

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import cz.obehy.core.ui.ComingSoon
import kotlinx.serialization.Serializable

/**
 * A trip's detail: an Oběhy trip (`/api/v1/trips/{tripId}/{serviceDate}`) when the leg resolved,
 * else the MOTIS trip ([motisTripId], `/api/v5/trip`).
 */
@Serializable
data class TripRoute(
    val tripId: String? = null,
    val serviceDate: String? = null,
    val motisTripId: String? = null,
)

fun NavController.navigateToTrip(route: TripRoute) = navigate(route)

fun NavGraphBuilder.tripScreen() {
    composable<TripRoute> { entry ->
        val route = entry.toRoute<TripRoute>()
        ComingSoon(route.tripId ?: route.motisTripId ?: stringResource(R.string.trip_title))
    }
}
