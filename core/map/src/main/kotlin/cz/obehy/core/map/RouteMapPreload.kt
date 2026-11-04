package cz.obehy.core.map

import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import cz.obehy.core.model.Leg
import cz.obehy.core.ui.ObehyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.style.BaseStyle
import kotlin.time.Duration.Companion.seconds

/** How many journeys get their map ahead of time: about as many as a results screen shows. */
private const val PRELOADED = 4

/** How many finished maps stay in memory, each a few megabytes. */
private const val KEPT = 8

/** A map that takes longer than this is left to load when it's opened (a bad connection). */
private val CAPTURE_LIMIT = 15.seconds

private const val TAG = "RouteMapPreload"

/** A journey's map, already drawn, with the credits of the data it shows. */
internal class StillMap(
    val image: ImageBitmap,
    val credits: List<String>,
)

private data class StillKey(
    val style: String,
    val size: DpSize,
)

private val stills = LruCache<StillKey, StillMap>(KEPT)

/** The map already drawn for this [style] at this [size], if there is one. */
internal fun stillMap(
    style: String,
    size: DpSize,
): StillMap? = stills[StillKey(style, size)]

/**
 * Draws the header maps of the first [journeys] in the background, so a journey opens with its map
 * in place. Drawing them also caches their tiles, which speeds up the full-screen map. Draws
 * nothing itself; a journey not ready yet simply loads its map on opening.
 */
@Composable
fun PreloadRouteMaps(journeys: List<List<Leg>>) {
    val context = LocalContext.current
    val density by rememberUpdatedState(LocalDensity.current)
    val look = mapLook()
    val window = LocalWindowInfo.current.containerSize
    val height = ObehyTheme.sizes.heroMap
    LaunchedEffect(journeys, look, window, height) {
        val size = with(density) { DpSize(window.width.toDp(), height) }
        for (legs in journeys.take(PRELOADED)) {
            val style = withContext(Dispatchers.Default) { journeyStyle(context, look, legs) }
            val key = StillKey(style, size)
            if (stills[key] != null) continue
            val camera = frame(legs.flatMap { it.shape() }, size.width.value, size.height.value)
            val still = capture(style, size, camera, density) ?: break
            stills.put(key, still)
        }
    }
}

/** The map drawn off screen, or null if that failed, so there's no use trying the next one. */
private suspend fun capture(
    style: String,
    size: DpSize,
    camera: CameraPosition,
    density: Density,
): StillMap? {
    val snapshotter = DefaultMapRuntime.instance.createSnapshotter(BaseStyle.Json(style))
    return try {
        withTimeoutOrNull(CAPTURE_LIMIT) {
            val image =
                snapshotter.capture(size) {
                    cameraPosition = camera
                    this.density = density
                }
            StillMap(image, snapshotter.style.attributions())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't draw a map ahead of time", e)
        null
    } finally {
        snapshotter.close()
    }
}
