package cz.obehy.core.map

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.max
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.model.MapStop
import cz.obehy.core.model.Mode
import cz.obehy.core.model.RouteSurroundings
import cz.obehy.core.model.focusAt
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.routeBlendsInto
import cz.obehy.core.ui.heroStripHeight
import cz.obehy.core.ui.rememberEntrance
import kotlinx.coroutines.flow.first
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
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.camera.CubicBezier
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.AndroidRenderMode
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.AttributionLinks
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.Json as JsonParser

/**
 * A journey on the Oběhy map: each ride in its route's colour, walks dashed, a dot at every stop
 * where you board or alight, the stops on the way as the zoom makes room for them. [interactive]
 * lets it be panned and zoomed; otherwise it's a still picture (a screen's header) for as long as
 * nothing has to move. A [fullscreen] map fills the window, edge to edge. [around] belongs to
 * [legs], and is the same object for as long as it stays the same. [framing] is what it shows, and
 * a change of it moves the camera there, handing a still over to a live map first; [onGesture] is
 * told when the user's own hands move it. [vehicles] slide along as their positions change, over
 * [glideMillis]. With [shrunk], from a screen's hero, all of the route stays in view however far
 * the hero has shrunk.
 */
@Composable
fun RouteMap(
    legs: List<Leg>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    fullscreen: Boolean = false,
    around: RouteSurroundings? = null,
    vehicles: List<MapVehicle> = emptyList(),
    glideMillis: Int = GLIDE_MS,
    framing: MapFraming = MapFraming.Whole,
    onGesture: () -> Unit = {},
    shrunk: (() -> Float)? = null,
    focus: HeroFocus = HeroFocus.None,
) {
    val context = LocalContext.current
    val look = mapLook()
    val loading = MaterialTheme.colorScheme.surfaceContainer
    val shape = remember(legs, around) { legs.map { it.shape() } + listOfNotNull(around?.path) }
    // Keyed on the text too: fresh times bring new legs, but the same map, which needn't reload.
    val prepared = remember(legs, look, around) { preparedStyle(look, legs, around) }
    val json by produceState(prepared, legs, look, around) { value = routeStyle(context, look, legs, around) }
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
        // What it opens on. Moving on from there takes a live map.
        val opening = remember(points, maxWidth, maxHeight) { framing }
        val camera =
            remember(opening, points, maxWidth, maxHeight) { opening.camera(points, maxWidth.value, maxHeight.value) }
        if (focus == HeroFocus.Leave) SideEffect { lastHeroCamera = camera }
        val fit =
            remember(opening, points, maxWidth, maxHeight, strip, shrunk != null) {
                if (shrunk == null) null else shrinkFit(opening.points(points), maxWidth, maxHeight, strip)
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
        val mapSize = fit?.size ?: DpSize(maxWidth, maxHeight)
        val still =
            remember(
                ready,
                mapSize,
                interactive,
                camera,
            ) { if (interactive) null else stillMap(ready, mapSize, camera) }
        // A picture stays up until the live map under it has something to show.
        var lastStill by remember { mutableStateOf(still) }
        var wentLive by remember { mutableStateOf(false) }
        var liveMap by remember { mutableStateOf<MapState?>(null) }
        var drawn by remember { mutableStateOf(false) }
        val shownVehicles = vehicles.map { rememberVehicleOnMap(it, glideMillis) }
        val onTheMap = rememberUpdatedState(shownVehicles)
        val live = interactive || wentLive || still == null || framing != opening
        SideEffect {
            if (still != null) lastStill = still
            if (live) wentLive = true
        }
        if (live) {
            val state =
                rememberMapState(baseStyle = style, initialCameraPosition = camera) {
                    onTheMap.value.forEachIndexed { index, vehicle -> VehicleSymbol(vehicle, index) }
                }
            SideEffect { liveMap = state }
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
            val waitsForDrawing = !interactive && lastStill != null
            LaunchedEffect(state) {
                if (waitsForDrawing) state.events.first { it is MapEvent.Idle }
                drawn = true
            }
            MovingCamera(state, framing, opening, drawn, onGesture, points, DpSize(maxWidth, maxHeight), glideMillis)
        }
        val picturing = lastStill?.takeUnless { interactive || (live && drawn) }
        if (picturing != null) {
            Image(picturing.image, contentDescription = null, picture, contentScale = ContentScale.Crop)
            MapCredits({ picturing.credits }, creditsModifier)
        } else {
            // The map data's credits (OpenStreetMap, OpenMapTiles), without MapLibre's logo.
            liveMap?.let { state -> MapCredits({ state.style.attributions() }, creditsModifier) }
        }
        if (picturing != null) {
            shownVehicles.forEach { VehicleOnPicture(it, camera, scale, Modifier.align(Alignment.Center)) }
        }
        vehicles.forEach { VehicleDescription(it) }
    }
}

/** Where a map looks. */
sealed interface MapFraming {
    /** All of the route. */
    data object Whole : MapFraming

    /** These [points] of it, however much bigger they then come out. */
    data class Focus(
        val points: List<LatLon>,
    ) : MapFraming

    /** Keeping up with a vehicle, [at] this [zoom]. */
    data class Follow(
        val at: LatLon,
        val zoom: Double,
    ) : MapFraming

    /** The points this framing has to show, of all those of the route. */
    fun points(route: List<LatLon>): List<LatLon> = if (this is Focus) points else route

    /** The camera for this framing of the [route] in a [width]×[height] dp map. */
    fun camera(
        route: List<LatLon>,
        width: Float,
        height: Float,
    ): CameraPosition =
        if (this is Follow) {
            CameraPosition(Position(at.lon, at.lat), zoom = zoom)
        } else {
            frame(points(route), width, height)
        }
}

/** The framing that shows what matters of [legs] at [now]. */
fun heroFraming(
    legs: List<Leg>,
    now: Instant,
): MapFraming =
    legs.focusAt(now)?.let { focused -> MapFraming.Focus(focused.legs.flatMap { it.shape() }) } ?: MapFraming.Whole

/** How close in to follow a vehicle of this [mode]: a train leaves a street-level map quickly. */
fun followZoom(mode: Mode): Double =
    when (mode) {
        Mode.Bus, Mode.Tram, Mode.Subway -> FOLLOW_ZOOM_STREET
        else -> FOLLOW_ZOOM_FAST
    }

private const val FOLLOW_ZOOM_STREET = 14.0
private const val FOLLOW_ZOOM_FAST = 12.0

/** How long the camera takes to go from one framing to another. */
private val REFRAME_TIME = 800.milliseconds

/** Moves [state]'s camera to each new [framing] once the map is [drawn], and reports the user's own moves. */
@Composable
private fun MovingCamera(
    state: MapState,
    framing: MapFraming,
    opening: MapFraming,
    drawn: Boolean,
    onGesture: () -> Unit,
    points: List<LatLon>,
    size: DpSize,
    glideMillis: Int,
) {
    val gesture by rememberUpdatedState(onGesture)
    LaunchedEffect(state) {
        snapshotFlow { state.cameraMoveReason }.collect { if (it is CameraMoveReason.Gesture) gesture() }
    }
    val placed = remember { arrayOf(opening) }
    LaunchedEffect(state, framing, drawn) {
        val from = placed[0]
        if (!drawn || framing == from) return@LaunchedEffect
        placed[0] = framing
        val target = framing.camera(points, size.width.value, size.height.value)
        val steady = framing is MapFraming.Follow && from is MapFraming.Follow
        val animation =
            CameraAnimation.Ease(CameraAnimation.Ease.Standard) {
                duration = if (steady) glideMillis.milliseconds else REFRAME_TIME
                if (steady) easing = CubicBezier.Linear
            }
        state.animateCamera(CameraUpdate(center = target.center, zoom = target.zoom), animation)
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
    FilledTonalIconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes(), modifier) {
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
 * its `trip` source: rides as paths in their colours, walks as a dashed line under them, the stops
 * where you board or alight, and beads for the stops on the way as the zoom makes room. A route
 * colour that would vanish into the map is edged in the walks' colour.
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
                if (rest.approach.size >= 2) {
                    add(feature("approach", rest.approach, rest.approachColor?.let { "#$it" } ?: primary, null))
                }
            }
            legs.forEach { leg ->
                val points = leg.shape()
                if (points.size < 2) return@forEach
                val colour = (leg as? Leg.Transit)?.color?.let { "#$it" } ?: primary
                val casing = if (leg is Leg.Transit && vanishes(leg)) walk else land
                add(feature(if (leg is Leg.Walk) "walk" else "path", points, colour, null, casing = casing))
            }
            val rides = legs.filterIsInstance<Leg.Transit>()
            val stops = rides.flatMap { listOf(it.from to it, it.to to it) }
            val colourOf = { ride: Leg.Transit? ->
                if (ride != null && vanishes(ride)) walk else ride?.color?.let { "#$it" } ?: primary
            }
            val onTheWay =
                (
                    around?.stops.orEmpty().map { it to rides.firstOrNull() } +
                        rides.flatMap { ride ->
                            ride.intermediateStops.mapNotNull { call ->
                                call.position?.let { MapStop(it, call.name, ridden = true) to ride }
                            }
                        }
                ).distinctBy { (stop) -> stop.position }
            val reveals =
                stopReveals(
                    onTheWay.map { (stop) ->
                        stop.position
                    },
                    stops.mapNotNull { (call) -> call.position },
                )
            onTheWay.forEachIndexed { index, (stop, ride) ->
                val colour = if (stop.ridden) colourOf(ride) else walk
                add(
                    feature(
                        "minor",
                        listOf(stop.position),
                        colour,
                        stop.name,
                        passed = !stop.ridden,
                        reveal = reveals[index],
                    ),
                )
            }
            stops.forEachIndexed { rank, (call, ride) ->
                val at = call.position ?: return@forEachIndexed
                add(feature("stop", listOf(at), colourOf(ride), call.name, rank))
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
    val walkLayer = lineLayer("walk", JsonPrimitive(walk), dashed = true)
    val restLayer = lineLayer("rest", JsonPrimitive(walk), dashed = false)
    val approachLayer = lineLayer("approach", propertyOf("colour"), dashed = true)
    val baseLayers = base.getValue("layers").jsonArray.map { it.jsonObject }
    val layers =
        buildJsonArray {
            baseLayers.forEach { layer ->
                when (layer["id"]) {
                    JsonPrimitive("trip-casing") -> {
                        add(restLayer)
                        add(approachLayer)
                        add(walkLayer)
                        add(layer.withCasingColour())
                    }

                    // Under the stops you board and alight at, so their names win where there's no room.
                    JsonPrimitive("trip-stops") -> {
                        add(layer.asMinorStops())
                        add(baseLayers.first { it["id"] == JsonPrimitive("trip-stop-names") }.asMinorNames())
                        add(layer)
                    }

                    else -> {
                        add(layer)
                    }
                }
            }
        }
    return JsonObject(base + mapOf("sources" to sources, "layers" to layers)).toString()
}

/** A thin line for the `trip` source's features of this [kind]. */
private fun lineLayer(
    kind: String,
    colour: JsonElement,
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

private fun propertyOf(name: String): JsonArray =
    buildJsonArray {
        add(JsonPrimitive("get"))
        add(JsonPrimitive(name))
    }

/** The stops on the way that the zoom has made room for. */
private val shownMinorStops =
    buildJsonArray {
        add(JsonPrimitive("all"))
        addJsonArray {
            add(JsonPrimitive("=="))
            add(propertyOf("kind"))
            add(JsonPrimitive("minor"))
        }
        addJsonArray {
            add(JsonPrimitive("<="))
            add(propertyOf("reveal"))
            addJsonArray { add(JsonPrimitive("zoom")) }
        }
    }

/** A size growing with the zoom, as [lowest] at [LOW_ZOOM] to [highest] at [HIGH_ZOOM]. */
private fun growing(
    lowest: Double,
    highest: Double,
): JsonArray =
    buildJsonArray {
        add(JsonPrimitive("interpolate"))
        addJsonArray {
            add(JsonPrimitive("exponential"))
            add(JsonPrimitive(GROWTH))
        }
        addJsonArray { add(JsonPrimitive("zoom")) }
        add(JsonPrimitive(LOW_ZOOM))
        add(JsonPrimitive(lowest))
        add(JsonPrimitive(HIGH_ZOOM))
        add(JsonPrimitive(highest))
    }

private const val GROWTH = 1.5
private const val LOW_ZOOM = 8
private const val HIGH_ZOOM = 16

/** Stops on the way are named from here: closer in there's room, and further out it's just noise. */
private const val MINOR_NAMES_ZOOM = 13

/** The `trip-stops` layer redone as small beads on the line, for the stops on the way. */
private fun JsonObject.asMinorStops(): JsonObject {
    val paint =
        JsonObject(
            getValue("paint").jsonObject +
                mapOf(
                    "circle-radius" to growing(lowest = 1.25, highest = 5.0),
                    "circle-stroke-width" to growing(lowest = 0.5, highest = 2.0),
                ),
        )
    return JsonObject(
        this +
            mapOf(
                "id" to JsonPrimitive("journey-minor-stops"),
                "filter" to shownMinorStops,
                "paint" to paint,
            ),
    )
}

/** The `trip-stop-names` layer redone for the stops on the way: smaller, and only close in. */
private fun JsonObject.asMinorNames(): JsonObject {
    val layout = JsonObject(getValue("layout").jsonObject + ("text-size" to growing(lowest = 10.0, highest = 12.0)))
    return JsonObject(
        this +
            mapOf(
                "id" to JsonPrimitive("journey-minor-names"),
                "filter" to shownMinorStops,
                "minzoom" to JsonPrimitive(MINOR_NAMES_ZOOM),
                "layout" to layout,
            ),
    )
}

/** The base styles, read once. */
private val baseStyles = ConcurrentHashMap<String, JsonObject>()

/** This casing layer, coloured per path by its `casing` property. */
private fun JsonObject.withCasingColour(): JsonObject {
    val paint = JsonObject(getValue("paint").jsonObject + ("line-color" to propertyOf("casing")))
    return JsonObject(this + ("paint" to paint))
}

private fun feature(
    kind: String,
    points: List<LatLon>,
    colour: String,
    name: String?,
    rank: Int = 0,
    casing: String? = null,
    passed: Boolean = false,
    reveal: Int = 0,
): JsonObject =
    buildJsonObject {
        put("type", "Feature")
        putJsonObject("properties") {
            put("kind", kind)
            put("colour", colour)
            put("passed", passed)
            put("rank", rank)
            put("reveal", reveal)
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
