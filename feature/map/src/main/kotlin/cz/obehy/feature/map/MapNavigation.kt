package cz.obehy.feature.map

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The live map: a top-level tab wherever there's a map to show. */
@Serializable
data object MapRoute

fun NavGraphBuilder.mapScreen() {
    composable<MapRoute> { MapDestination(hiltViewModel()) }
}
