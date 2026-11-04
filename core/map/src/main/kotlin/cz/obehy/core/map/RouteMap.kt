package cz.obehy.core.map

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.max
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.RouteSurroundings
import cz.obehy.core.model.VehiclePosition
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.RouteColors
import cz.obehy.core.ui.components.routeBlendsInto
import cz.obehy.core.ui.components.routeColors
import cz.obehy.core.ui.heroStripHeight
import cz.obehy.core.ui.rememberEntrance
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.AndroidRenderMode
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.AttributionLinks
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.serialization.json.Json as JsonParser

/**
 * A journey on the Oběhy map: each ride in its route's colour, walks dashed, a dot at every stop
 * where you board or alight, framed to show the whole way. [interactive] lets it be panned and
 * zoomed; otherwise it is a still picture (a screen's header). A [fullscreen] map fills the
 * window, edge to edge. [around] belongs to [legs]: the same legs always come with the same one.
 * [vehicles] sit on top of a map that doesn't move, marked with [vehicleIcon] on the first ride's
 * colours, and slide along as their positions change. With [shrunk], from a screen's hero, all of
 * the route stays in view however far the hero has shrunk.
 */
@Composable
fun RouteMap(
    legs: List<Leg>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    fullscreen: Boolean = false,
    around: RouteSurroundings? = null,
    vehicles: List<VehiclePosition> = emptyList(),
    vehicleIcon: ImageVector? = null,
    shrunk: (() -> Float)? = null,
    focus: HeroFocus = HeroFocus.None,
) {
    val context = LocalContext.current
    val look = mapLook()
    val loading = MaterialTheme.colorScheme.surfaceContainer
    val shape = remember(legs, around) { legs.map { it.shape() } + listOfNotNull(around?.path) }
    // Keyed on the text too: fresh times bring new legs, but the same map, which needn't reload.
    val prepared = remember(legs, look) { preparedStyle(look, legs) }
    val json by produceState(prepared, legs, look) { value = routeStyle(context, look, legs, around) }
    val style = remember(json) { json?.let { BaseStyle.Json(it) } }
    val entrance = rememberEntrance()
    val cameFrom = remember { if (focus == HeroFocus.Arrive) lastHeroCamera else null }
    val strip = heroStripHeight()
    BoxWithConstraints(modifier) {
        val ready = json
        if (ready == null || style == null) {
            Box(Modifier.fillMaxSize().background(loading))
            return@BoxWithConstraints
        }
        val points = remember(shape) { shape.flatten() }
        val camera = remember(points, maxWidth, maxHeight) { frame(points, maxWidth.value, maxHeight.value) }
        if (focus == HeroFocus.Leave) SideEffect { lastHeroCamera = camera }
        val fit =
            remember(points, maxWidth, maxHeight, strip, shrunk != null) {
                if (shrunk == null) null else shrinkFit(points, maxWidth, maxHeight, strip)
            }
        val scale = { 1 - (1 - (fit?.scale ?: 1f)) * (shrunk?.invoke() ?: 0f) }
        val full = maxHeight
        val creditsModifier =
            Modifier
                .align(if (shrunk == null) Alignment.BottomEnd else Alignment.TopEnd)
                .then(if (fullscreen) Modifier.navigationBarsPadding() else Modifier)
                .then(
                    if (shrunk == null) {
                        Modifier
                    } else {
                        // Stays in the corner of what's left of the map as it's trimmed.
                        Modifier.statusBarsPadding().graphicsLayer {
                            translationY = (full - strip).toPx() / 2 * shrunk()
                        }
                    },
                ).padding(ObehyTheme.spacing.s)
        val picture =
            (if (fit != null) Modifier.requiredSize(fit.size) else Modifier.fillMaxSize()).graphicsLayer {
                val arrival = cameFrom?.let { camera.seenFrom(it) }
                val left = 1 - entrance.value
                val zoom = 1 + ((arrival?.scale ?: 1f) - 1) * left
                val size = scale() * zoom
                scaleX = size
                scaleY = size
                translationX = (arrival?.x ?: 0f) * left * density
                translationY = (arrival?.y ?: 0f) * left * density
            }
        val still =
            remember(ready, maxWidth, maxHeight, interactive, fit) {
                if (interactive) null else stillMap(ready, fit?.size ?: DpSize(maxWidth, maxHeight))
            }
        if (still != null) {
            Image(still.image, contentDescription = null, picture, contentScale = ContentScale.Crop)
            MapCredits({ still.credits }, creditsModifier)
        } else {
            val state = rememberMapState(baseStyle = style, initialCameraPosition = camera)
            MaplibreMap(
                picture,
                state = state,
                interactions = if (interactive) MapInteractions.Standard else MapInteractions.None,
                // A texture, not a surface: only a texture scales, clips and fades with the screen
                // around it, which the back gesture's preview and the card's morph both depend on.
                uiOptions =
                    MapUiOptions(if (interactive) MapUiOptions.Standard else MapUiOptions.None) {
                        renderMode = AndroidRenderMode.Texture
                        loadColor = loading
                    },
                // None of the library's own ornaments: its credits would double up with ours.
                overlay = {},
            )
            // The map data's credits (OpenStreetMap, OpenMapTiles), without MapLibre's logo.
            MapCredits({ state.style.attributions() }, creditsModifier)
        }
        if (!interactive && vehicleIcon != null && vehicles.isNotEmpty()) {
            val ride = legs.firstOrNull() as? Leg.Transit
            val colors = routeColors(ride?.color, ride?.textColor)
            vehicles.forEach { VehicleMark(it, camera, vehicleIcon, colors, scale, Modifier.align(Alignment.Center)) }
        }
    }
}

/** What a map at the head of a screen does as the next screen's map takes its place. */
enum class HeroFocus {
    None,

    /** It's what the next hero's map refocuses from. */
    Leave,

    /** It starts framed as the last hero was, and moves to its own framing as its screen comes in. */
    Arrive,
}

/** The framing of the last map to [leave][HeroFocus.Leave]: only ever touched while composing. */
private var lastHeroCamera: CameraPosition? = null

/** How a picture framed by this camera has to lie to look framed by another one. */
private class Refocus(
    val scale: Float,
    val x: Float,
    val y: Float,
)

private fun CameraPosition.seenFrom(other: CameraPosition): Refocus {
    val scale = 2.0.pow(other.zoom - zoom).toFloat()
    val (x, y) = offsetTo(LatLon(other.center.latitude, other.center.longitude))
    return Refocus(scale.coerceIn(MIN_REFOCUS, MAX_REFOCUS), -x * scale, -y * scale)
}

private const val MIN_REFOCUS = 0.2f
private const val MAX_REFOCUS = 8f

/**
 * How a map keeps all of a route in view as it shrinks from [height] to [strip]: it's drawn [size]
 * large, more than fits, and scaled down to [scale] when it's a strip. Tiles can't be fetched as
 * it shrinks, so they have to be there already.
 */
internal data class ShrinkFit(
    val scale: Float,
    val size: DpSize,
)

internal fun shrinkFit(
    points: List<LatLon>,
    width: Dp,
    height: Dp,
    strip: Dp,
): ShrinkFit {
    val full = frame(points, width.value, height.value).zoom
    val small = frame(points, width.value, strip.value, STRIP_MARGIN).zoom
    val scale = 2.0.pow(small - full).toFloat().coerceIn(MIN_SHRINK, 1f)
    return ShrinkFit(scale, DpSize(width / scale, max(height, strip / scale)))
}

/** The margin around a route on a map shrunk to a strip: enough for the status bar and the sheet. */
private const val STRIP_MARGIN = 36.0

/** Past this, a route that won't fit is cropped rather than a huge picture being drawn. */
private const val MIN_SHRINK = 0.4f

/** A vehicle where the map shows [vehicle]'s position, sliding there when that changes. */
@Composable
private fun VehicleMark(
    vehicle: VehiclePosition,
    camera: CameraPosition,
    icon: ImageVector,
    colors: RouteColors,
    scale: () -> Float,
    modifier: Modifier = Modifier,
) {
    val (x, y) = camera.offsetTo(vehicle.position)
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val shownX by animateFloatAsState(x, spec)
    val shownY by animateFloatAsState(y, spec)
    val sizes = ObehyTheme.sizes
    val description = stringResource(if (vehicle.estimated) R.string.map_vehicle_estimated else R.string.map_vehicle)
    val shape = MaterialShapes.Circle.toShape()
    // A guess looks like one: hollow, in quiet colours, rather than in the line's own.
    val background = if (vehicle.estimated) MaterialTheme.colorScheme.surface else colors.container
    val tint = if (vehicle.estimated) MaterialTheme.colorScheme.onSurfaceVariant else colors.content
    val edge = if (vehicle.estimated) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surface
    Box(
        modifier
            .graphicsLayer {
                translationX = shownX * density * scale()
                translationY = shownY * density * scale()
            }.size(sizes.mapVehicle)
            .semantics { contentDescription = description }
            .border(ObehyTheme.spacing.xs / 2, edge, shape)
            .padding(ObehyTheme.spacing.xs / 2)
            .background(background, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(sizes.iconSmall), tint = tint)
    }
}

/** What a journey's map takes from the app's theme. */
internal data class MapLook(
    val dark: Boolean,
    val primary: String,
    val walk: String,
)

@Composable
internal fun mapLook(): MapLook =
    MapLook(
        dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
        primary = MaterialTheme.colorScheme.primary.hex(),
        walk = MaterialTheme.colorScheme.outline.hex(),
    )

/** A small (i) that opens the map data's [credits], readable on any map. */
@Composable
private fun MapCredits(
    credits: () -> List<String>,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    FilledTonalIconButton(onClick = { open = true }, modifier) {
        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.map_credits))
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(
                Modifier.padding(
                    start = ObehyTheme.spacing.l,
                    end = ObehyTheme.spacing.l,
                    bottom = ObehyTheme.spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
            ) {
                Text(stringResource(R.string.map_credits), style = MaterialTheme.typography.titleMedium)
                AttributionLinks(
                    credits(),
                    Modifier,
                    MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    TextLinkStyles(
                        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
                    ),
                    ObehyTheme.spacing.s,
                    // Wrap the credits instead of scrolling them sideways on one line.
                    true,
                )
            }
        }
    }
}

/** The points a leg is drawn through: its path, or a straight line between its stops. */
internal fun Leg.shape(): List<LatLon> = path.ifEmpty { listOfNotNull(from.position, to.position) }

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/**
 * The web's map style (exported to the assets by `tools/export-style.mjs`) with the journey in
 * its `trip` source: rides as paths in their colours, walks as a dashed line under them, and the
 * stops where you board or alight. A route colour that would vanish into the map is edged in the
 * walks' colour.
 */
internal fun journeyStyle(
    context: Context,
    look: MapLook,
    legs: List<Leg>,
    around: RouteSurroundings? = null,
): String {
    val primary = look.primary
    val walk = look.walk
    val asset = if (look.dark) "map-style-dark.json" else "map-style-light.json"
    val base =
        baseStyles.getOrPut(asset) {
            JsonParser
                .parseToJsonElement(
                    context.assets
                        .open(asset)
                        .bufferedReader()
                        .use { it.readText() },
                ).jsonObject
        }
    val land =
        base
            .getValue("layers")
            .jsonArray
            .first { it.jsonObject["id"] == JsonPrimitive("land") }
            .jsonObject
            .getValue("paint")
            .jsonObject
            .getValue("background-color")
            .jsonPrimitive.content

    fun vanishes(ride: Leg.Transit) = routeBlendsInto(ride.color, land.removePrefix("#"))
    val features =
        buildJsonArray {
            around?.let { rest ->
                if (rest.path.size >= 2) add(feature("rest", rest.path, walk, null))
                rest.stops.forEach { add(feature("stop", listOf(it), walk, null, passed = true)) }
            }
            legs.forEach { leg ->
                val points = leg.shape()
                if (points.size < 2) return@forEach
                val colour = (leg as? Leg.Transit)?.color?.let { "#$it" } ?: primary
                val casing = if (leg is Leg.Transit && vanishes(leg)) walk else land
                add(feature(if (leg is Leg.Walk) "walk" else "path", points, colour, null, casing = casing))
            }
            val stops = legs.filterIsInstance<Leg.Transit>().flatMap { listOf(it.from to it, it.to to it) }
            stops.forEachIndexed { rank, (call, ride) ->
                val at = call.position ?: return@forEachIndexed
                val colour = if (vanishes(ride)) walk else ride.color?.let { "#$it" } ?: primary
                add(feature("stop", listOf(at), colour, call.name, rank))
            }
        }
    val sources =
        buildJsonObject {
            base.getValue("sources").jsonObject.forEach { (id, source) ->
                if (id == "trip") {
                    putJsonObject(id) {
                        put("type", "geojson")
                        putJsonObject("data") {
                            put("type", "FeatureCollection")
                            put("features", features)
                        }
                    }
                } else {
                    put(id, source)
                }
            }
        }
    val walkLayer = lineLayer("walk", walk, dashed = true)
    val restLayer = lineLayer("rest", walk, dashed = false)
    val layers =
        buildJsonArray {
            base.getValue("layers").jsonArray.forEach { layer ->
                if (layer.jsonObject["id"] == JsonPrimitive("trip-casing")) {
                    add(restLayer)
                    add(walkLayer)
                    add(layer.withCasingColour())
                } else {
                    add(layer)
                }
            }
        }
    return JsonObject(base + mapOf("sources" to sources, "layers" to layers)).toString()
}

/** A thin line for the `trip` source's features of this [kind]. */
private fun lineLayer(
    kind: String,
    colour: String,
    dashed: Boolean,
): JsonObject =
    buildJsonObject {
        put("id", "journey-$kind")
        put("type", "line")
        put("source", "trip")
        putJsonArray("filter") {
            add(JsonPrimitive("=="))
            addJsonArray {
                add(JsonPrimitive("get"))
                add(JsonPrimitive("kind"))
            }
            add(JsonPrimitive(kind))
        }
        putJsonObject("layout") { put("line-cap", "round") }
        putJsonObject("paint") {
            put("line-color", colour)
            put("line-width", 3)
            if (dashed) {
                putJsonArray("line-dasharray") {
                    add(JsonPrimitive(0.5))
                    add(JsonPrimitive(2))
                }
            }
        }
    }

/** The base styles, read once. */
private val baseStyles = ConcurrentHashMap<String, JsonObject>()

/** This casing layer, coloured per path by its `casing` property. */
private fun JsonElement.withCasingColour(): JsonObject {
    val casing =
        buildJsonArray {
            add(JsonPrimitive("get"))
            add(JsonPrimitive("casing"))
        }
    val paint = JsonObject(jsonObject.getValue("paint").jsonObject + ("line-color" to casing))
    return JsonObject(jsonObject + ("paint" to paint))
}

private fun feature(
    kind: String,
    points: List<LatLon>,
    colour: String,
    name: String?,
    rank: Int = 0,
    casing: String? = null,
    passed: Boolean = false,
): JsonObject =
    buildJsonObject {
        put("type", "Feature")
        putJsonObject("properties") {
            put("kind", kind)
            put("colour", colour)
            put("passed", passed)
            put("rank", rank)
            name?.let { put("name", it) }
            casing?.let { put("casing", it) }
        }
        putJsonObject("geometry") {
            if (points.size == 1) {
                put("type", "Point")
                put("coordinates", points.single().coordinates())
            } else {
                put("type", "LineString")
                put("coordinates", JsonArray(points.map { it.coordinates() }))
            }
        }
    }

private fun LatLon.coordinates(): JsonArray = JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat)))

/** Where [point] is from the middle of this camera's view, in dp. */
internal fun CameraPosition.offsetTo(point: LatLon): Pair<Float, Float> {
    // MapLibre's world is 512 dp wide at zoom 0.
    val world = 512 * 2.0.pow(zoom)
    val x = (point.lon - center.longitude) / 360 * world
    val y = (mercator(center.latitude) - mercator(point.lat)) / (2 * PI) * world
    return x.toFloat() to y.toFloat()
}

/** A camera showing every one of [points] in a [width]×[height] dp map, with a margin. */
internal fun frame(
    points: List<LatLon>,
    width: Float,
    height: Float,
    margin: Double = 48.0,
): CameraPosition {
    if (points.isEmpty()) return CameraPosition(Position(15.5, 49.8), zoom = 6.0)
    val west = points.minOf { it.lon }
    val east = points.maxOf { it.lon }
    val south = mercator(points.minOf { it.lat })
    val north = mercator(points.maxOf { it.lat })
    // MapLibre's world is 512 dp wide at zoom 0.
    val zoomX = log2((width - 2 * margin).coerceAtLeast(1.0) * 360 / ((east - west).coerceAtLeast(1e-5) * 512))
    val zoomY = log2((height - 2 * margin).coerceAtLeast(1.0) * 2 * PI / ((north - south).coerceAtLeast(1e-7) * 512))
    val centre = Position((west + east) / 2, Math.toDegrees(atan(sinh((south + north) / 2))))
    return CameraPosition(centre, zoom = minOf(zoomX, zoomY, 16.0))
}

private fun mercator(lat: Double): Double = ln(tan(PI / 4 + Math.toRadians(lat) / 2))
