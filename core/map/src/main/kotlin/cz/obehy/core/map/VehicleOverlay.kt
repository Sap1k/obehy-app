package cz.obehy.core.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.VehiclePosition
import cz.obehy.core.ui.components.VehicleCertainty
import cz.obehy.core.ui.components.rememberVehicleMarker
import cz.obehy.core.ui.components.routeColors
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import java.time.Instant

/** A vehicle to show on a map, and what its marker says. */
data class MapVehicle(
    val at: VehiclePosition,
    val ride: Leg.Transit,
    val delayMinutes: Long?,
    val stale: Boolean,
)

/** This trip's vehicles as they stand at [now]. */
fun TripDetail.mapVehicles(now: Instant): List<MapVehicle> =
    if (cancelled) {
        emptyList()
    } else {
        vehiclesAt(now).map { MapVehicle(it, ride, delayMinutes, it.isStaleAt(now)) }
    }

/**
 * How long a vehicle takes to slide to a position it was reported at: just enough for the eye to
 * follow, since all the while it's shown behind where it's known to be.
 */
const val GLIDE_MS = 1_500

/** How often a sliding vehicle is moved: it covers a fraction of a dp between two moves. */
private const val GLIDE_STEP_MS = 100L

/** A vehicle's marker, and where it is on its way to its latest position. */
internal class VehicleOnMap(
    val marker: ImageBitmap,
    val place: State<LatLon>,
)

/** [vehicle] ready for a map, sliding to each new position over [glideMillis]. */
@Composable
internal fun rememberVehicleOnMap(
    vehicle: MapVehicle,
    glideMillis: Int,
): VehicleOnMap {
    val ride = vehicle.ride
    val marker =
        rememberVehicleMarker(
            line = ride.line ?: ride.name,
            colors = routeColors(ride.color, ride.textColor),
            certainty =
                when {
                    vehicle.at.estimated -> VehicleCertainty.Estimated
                    vehicle.stale -> VehicleCertainty.Stale
                    else -> VehicleCertainty.Live
                },
            delayMinutes = vehicle.delayMinutes,
            heading = vehicle.at.bearing?.toFloat(),
        )
    return VehicleOnMap(marker, gliding(vehicle.at.position, glideMillis))
}

@Composable
private fun gliding(
    target: LatLon,
    glideMillis: Int,
): State<LatLon> {
    val shown = remember { mutableStateOf(target) }
    var goingTo by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == goingTo) return@LaunchedEffect
        goingTo = target
        val from = shown.value
        val started = System.currentTimeMillis()
        while (true) {
            val done = ((System.currentTimeMillis() - started).toDouble() / glideMillis).coerceAtMost(1.0)
            shown.value = LatLon(from.lat + (target.lat - from.lat) * done, from.lon + (target.lon - from.lon) * done)
            if (done >= 1.0) break
            delay(GLIDE_STEP_MS)
        }
    }
    return shown
}

/**
 * [vehicle] drawn by a live map itself, so that it stays where it is on the map however the map is
 * dragged. To be composed in the map's style content. [index] tells it from the others on the map.
 */
@Composable
internal fun VehicleSymbol(
    vehicle: VehicleOnMap,
    index: Int,
) {
    val at = vehicle.place.value
    val json = """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${at.lon},${at.lat}]}}"""
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    SymbolLayer(
        id = "journey-vehicle-$index",
        source = source,
        iconImage = image(vehicle.marker),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** [vehicle] laid over a still picture of a map taken by [camera], which is drawn at [scale]. */
@Composable
internal fun VehicleOnPicture(
    vehicle: VehicleOnMap,
    camera: CameraPosition,
    scale: () -> Float,
    modifier: Modifier = Modifier,
) {
    Image(
        vehicle.marker,
        contentDescription = null,
        modifier.graphicsLayer {
            val (x, y) = camera.offsetTo(vehicle.place.value)
            translationX = x * density * scale()
            translationY = y * density * scale()
        },
    )
}

/** Tells a screen reader of [vehicle], which it can't find in a map's own drawing. */
@Composable
internal fun VehicleDescription(
    vehicle: MapVehicle,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(if (vehicle.at.estimated) R.string.map_vehicle_estimated else R.string.map_vehicle)
    Spacer(modifier.semantics { contentDescription = description })
}
