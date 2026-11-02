package cz.obehy.feature.planner

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.obehy.core.ui.ComingSoon
import kotlinx.serialization.Serializable

@Serializable
data object PlannerRoute

/** Journey search, results and itinerary detail (PLAN.md, milestone 1 steps 3–4). */
fun NavGraphBuilder.plannerScreen() {
    composable<PlannerRoute> { ComingSoon(stringResource(R.string.planner_title)) }
}
