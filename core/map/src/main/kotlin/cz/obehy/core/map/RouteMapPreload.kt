package cz.obehy.core.map

import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import cz.obehy.core.model.Leg
import cz.obehy.core.model.RouteSurroundings
import cz.obehy.core.ui.heroFullHeight
import cz.obehy.core.ui.heroStripHeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.style.BaseStyle
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.time.Duration.Companion.seconds

/** How many finished maps stay in memory, each a few megabytes. */
private const val KEPT = 8

/**
 * The wait before each map. It lets results finish arriving and a scroll that pauses carry on, and
 * leaves the main thread some frames between maps.
 */
private const val PAUSE_MS = 500L

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
    val camera: CameraPosition,
)

private val stills = LruCache<StillKey, StillMap>(KEPT)

/** A journey's style by the very objects it was made from: comparing their paths would cost more. */
internal class StyleKey(
    val look: MapLook,
    val legs: List<Leg>,
    val around: RouteSurroundings?,
) {
    override fun equals(other: Any?): Boolean =
        other is StyleKey && look == other.look && legs === other.legs && around === other.around

    override fun hashCode(): Int =
        31 * (31 * look.hashCode() + System.identityHashCode(legs)) + System.identityHashCode(around)
}

private val styles = LruCache<StyleKey, String>(KEPT)

/** The style already made for these [legs], if there is one. */
internal fun preparedStyle(
    look: MapLook,
    legs: List<Leg>,
    around: RouteSurroundings? = null,
): String? = styles[StyleKey(look, legs, around)]

/** The style for these [legs], made away from the main thread. */
internal suspend fun routeStyle(
    context: Context,
    look: MapLook,
    legs: List<Leg>,
    around: RouteSurroundings? = null,
): String =
    preparedStyle(look, legs, around)
        ?: withContext(Dispatchers.Default) { journeyStyle(context, look, legs, around) }
            .also { styles.put(StyleKey(look, legs, around), it) }

/** The map already drawn for this [style] at this [size] from this [camera], if there is one. */
internal fun stillMap(
    style: String,
    size: DpSize,
    camera: CameraPosition,
): StillMap? = stills[StillKey(style, size, camera)]

/**
 * Keeps the header maps of the journeys in [wanted] drawn, most wanted first, so a journey opens
 * with its map in place, framed on what matters by then. Drawing them also caches their tiles,
 * which speeds up the full-screen map.
 *
 * [wanted] reads state and can change at any time, and is empty while the screen needs its frames,
 * such as during a scroll. One map is drawn at a time, after a pause, and a journey that stops being
 * wanted in that pause costs nothing; one already being drawn is finished, as it's nearly paid for.
 * Draws nothing itself; a journey not ready yet simply loads its map on opening.
 */
@Composable
fun PreloadRouteMaps(
    around: (List<Leg>) -> RouteSurroundings? = { null },
    wanted: () -> List<List<Leg>>,
) {
    val journeys by rememberUpdatedState(wanted)
    val surroundings by rememberUpdatedState(around)
    val context = LocalContext.current
    val density by rememberUpdatedState(LocalDensity.current)
    val look = mapLook()
    val window = LocalWindowInfo.current.containerSize
    val height = heroFullHeight()
    val strip = heroStripHeight()
    LaunchedEffect(look, window, height, strip) {
        val width = with(density) { window.width.toDp() }

        fun routeOf(legs: List<Leg>) = legs.flatMap { it.shape() } + surroundings(legs)?.path.orEmpty()

        // A trip's map shows all of it, around the stretch ridden.
        fun framingOf(legs: List<Leg>) =
            if (surroundings(legs) == null) heroFraming(legs, Instant.now()) else MapFraming.Whole

        fun fitOf(
            legs: List<Leg>,
            framing: MapFraming,
        ) = shrinkFit(framing.points(routeOf(legs)), width, height, strip)

        fun cameraOf(
            legs: List<Leg>,
            framing: MapFraming,
        ) = framing.camera(routeOf(legs), width.value, height.value)

        val failed = Collections.newSetFromMap(IdentityHashMap<List<Leg>, Boolean>())

        fun isDrawn(legs: List<Leg>): Boolean {
            val style = preparedStyle(look, legs, surroundings(legs)) ?: return false
            val framing = framingOf(legs)
            return stills[StillKey(style, fitOf(legs, framing).size, cameraOf(legs, framing))] != null
        }

        while (true) {
            val legs = snapshotFlow { journeys().firstOrNull { it !in failed && !isDrawn(it) } }.filterNotNull().first()
            delay(PAUSE_MS)
            if (journeys().none { it === legs }) continue
            val style = routeStyle(context, look, legs, surroundings(legs))
            val framing = framingOf(legs)
            val (size, camera) =
                withContext(Dispatchers.Default) { fitOf(legs, framing).size to cameraOf(legs, framing) }
            val key = StillKey(style, size, camera)
            if (stills[key] != null) continue
            val still = capture(style, size, camera, density)
            if (still != null) {
                stills.put(key, still)
            } else {
                // Probably no connection: the next one would fail too, so wait for the view to change.
                failed += legs
                snapshotFlow { journeys() }.drop(1).first()
            }
        }
    }
}

/** The map drawn off screen, or null if that failed. */
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
