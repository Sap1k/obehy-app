package cz.obehy.core.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.LiveStop
import cz.obehy.core.model.LiveVehicle
import cz.obehy.core.model.MapBounds
import cz.obehy.core.model.MapMode
import cz.obehy.core.model.Mode
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.VehicleCertainty
import cz.obehy.core.ui.components.VehicleMarkerPainter
import cz.obehy.core.ui.components.rememberHeadingArrow
import cz.obehy.core.ui.components.rememberStopSign
import cz.obehy.core.ui.components.rememberVehicleMarkerPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.camera.CubicBezier
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.asDpOffset
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.BearingTargets
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.AndroidRenderMode
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.MissingImageRequest
import org.maplibre.compose.map.MissingImageResolver
import org.maplibre.compose.map.ResolvedStyleImage
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Position
import java.time.Instant
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import org.maplibre.spatialk.geojson.Feature as GeoFeature

/** The selected trip's way on a live map, in its route's [color]; [estimated] draws it dashed. */
data class LivePath(
    val points: List<LatLon>,
    val color: String?,
    val estimated: Boolean,
    /** Where it calls, named on the map as on the web; the map's other stops step aside meanwhile. */
    val stops: List<PathStop> = emptyList(),
)

/** A stop of the open trip on the map; [passed] ones go grey. */
data class PathStop(
    val position: LatLon,
    val name: String,
    val passed: Boolean,
)

/** Where a live map is asked to look. Each new value moves it; [Follow] keeps up with a vehicle. */
sealed interface LiveCamera {
    /** Go to [at], [zoom] in; [request] tells two asks for the same place apart. */
    data class Show(
        val at: LatLon,
        val zoom: Double,
        val request: Int,
    ) : LiveCamera

    /** All of [points] in view, above the sheet; [request] tells two asks apart. */
    data class Fit(
        val points: List<LatLon>,
        val request: Int,
    ) : LiveCamera

    /** Ride along with a vehicle now [at], this [zoom] in. */
    data class Follow(
        val at: LatLon,
        val zoom: Double,
    ) : LiveCamera
}

/**
 * Every vehicle around, live: dots when zoomed out, their line's pill (in its route colour,
 * hollow when only estimated) once there's room, a tip turned by each one's heading, and the
 * stops once zoomed in close. The map draws them all itself, from one source, so thousands stay
 * smooth and glued down as it's dragged; they glide to each new position over [glideMillis].
 * [selected] stands out in Lime with its trip's [path], alone. [onViewport] is told
 * what's in view each time the camera stops, [onGesture] when the user's own hands move it.
 */
@Composable
fun LiveMap(
    vehicles: List<LiveVehicle>,
    start: LatLon,
    startZoom: Double,
    onViewport: (MapBounds, Double) -> Unit,
    onVehicleClick: (String) -> Unit,
    onStopClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    stops: List<LiveStop> = emptyList(),
    selected: LiveVehicle? = null,
    path: LivePath? = null,
    me: LatLon? = null,
    from: LatLon? = null,
    camera: LiveCamera? = null,
    glideMillis: Int = GLIDE_MS,
    bottomInset: Dp = 0.dp,
    topInset: Dp = 0.dp,
    onGesture: (MapBounds) -> Unit = {},
) {
    val context = LocalContext.current
    val look = mapLook()
    val loading = MaterialTheme.colorScheme.surfaceContainer
    val json by produceState<String?>(null, look) { value = routeStyle(context, look, emptyList()) }
    val style = remember(json) { json?.let { BaseStyle.Json(it) } }
    Box(modifier) {
        if (style == null) {
            Box(Modifier.fillMaxSize().background(loading))
            return@Box
        }
        // Only what's in view (and a margin) is worked on: zoomed out, that's still all of it,
        // but then as dots, with no pictures to draw and no gliding. A pan inside the area already
        // covered, or a zoom that keeps dots dots, changes nothing here: the vehicles' data is
        // rebuilt only when it has to be, never for every frame of a pan.
        val covered = remember { mutableStateOf<MapBounds?>(null) }
        val pillsShown = remember { mutableStateOf(startZoom >= PILLS_FROM - PILL_LEAD) }
        val zoomStep = remember { mutableStateOf(startZoom.toInt()) }
        val pills = pillsShown.value
        val area = covered.value
        // With a trip open, only its vehicle: the others would only get in the way of finding it.
        val tripOpen = selected != null || path != null
        val drawn =
            remember(vehicles, area, tripOpen, selected?.id) {
                when {
                    tripOpen -> vehicles.filter { it.id == selected?.id }
                    area == null -> vehicles
                    else -> vehicles.filter { it.at.position in area }
                }
            }
        // The selected vehicle looks as it did; a halo under it says it's the one.
        // Every vehicle's marker is named once, at one moment, for both the pictures and the map's
        // data: named twice, a vehicle going stale in between would find no picture of its own.
        // Names only for what has a picture: dots go without. A pill's picture doesn't say which
        // way it faces: its tip is a picture of its own, turned by the map, so a vehicle turning
        // a corner needs nothing new drawn.
        val now = remember(drawn, selected) { Instant.now() }
        val pictured = if (pills) drawn + listOfNotNull(selected) else listOfNotNull(selected)
        // The selected one in its own look: a thick Lime ring, as anything selected.
        val wanted =
            remember(pictured, now, selected?.id) {
                pictured.associate { it.id to it.wantedLook(now, selected = it.id == selected?.id) }
            }
        val atlas = remember(pictured, wanted, now) { markerSpecs(pictured, wanted, now) }
        val painter = rememberVehicleMarkerPainter()
        // Pictures are drawn ahead, in the background, and given to the map before any vehicle is
        // named after one: until then a vehicle keeps the picture it had (or stays a dot), so a
        // marker never blinks out while its new look is drawn.
        val ready = remember { mutableStateOf(emptySet<String>()) }
        val names = remember { MarkerNames() }
        val keys = remember(wanted, ready.value) { names.publish(wanted, ready.value) }
        // The vehicle that was selected, until the vehicles drawn with the rest have it again: its
        // marker stays put meanwhile rather than blinking out as its sheet closes.
        val last = remember { LastSelected() }
        SideEffect {
            if (selected != null) {
                last.vehicle = selected
                last.look = keys[selected.id]
            }
        }
        val departed = last.vehicle.takeIf { selected == null }
        val arrow = rememberHeadingArrow()
        val fallback = MaterialTheme.colorScheme.secondary.hex()
        val surface = MaterialTheme.colorScheme.surface.hex()
        val lime = MaterialTheme.colorScheme.primaryContainer.hex()
        val ink = MaterialTheme.colorScheme.onSurface.hex()
        val view = remember { mutableStateOf<MapBounds?>(null) }
        val glide =
            rememberGlide(
                drawn,
                if (pills) glideMillis else 0,
                selected?.id,
                keys,
                // Less than this is too small to see move: such a vehicle just takes its new place.
                snap = SNAP_DP * DEGREES_PER_DP_AT_ZOOM_0 / 2.0.pow(zoomStep.value),
                view = { view.value },
            ) { vehicle, at, look -> vehicle.feature(at, look, fallback, surface, painter) }
        val stopJson = remember(stops) { stops.toFeatures() }
        val muted = MaterialTheme.colorScheme.outline.hex()
        val quiet = MaterialTheme.colorScheme.onSurfaceVariant.hex()
        val tripStopJson = remember(path, ink) { path?.tripStops(fallback, muted, ink, quiet) ?: EMPTY }
        val signs = MapMode.entries.associate { it.name to rememberStopSign(it.sign) }
        val pathJson = remember(path) { path?.points?.toLine() ?: EMPTY }
        val meJson = remember(me) { me?.let { listOf(point(it, "{}")).toCollection() } ?: EMPTY }
        val fromJson = remember(from) { from?.let { listOf(point(it, "{}")).toCollection() } ?: EMPTY }
        val layers =
            rememberUpdatedState(
                LiveLayers(
                    glide,
                    selected ?: departed,
                    selected?.let { keys[it.id] } ?: departed?.let { keys[it.id] ?: last.look },
                    departed = departed != null,
                    painter,
                    stopJson,
                    pathJson,
                    meJson,
                    fromJson,
                    atlas,
                    arrow,
                    path,
                    tripStopJson,
                    signs,
                    colours = LiveColours(surface, lime, ink, fallback, MaterialTheme.colorScheme.primary.hex()),
                    onVehicleClick = onVehicleClick,
                    onStopClick = onStopClick,
                ),
            )
        val state =
            rememberMapState(
                baseStyle = style,
                initialCameraPosition = CameraPosition(Position(start.lon, start.lat), zoom = startZoom),
            ) {
                LiveContent(layers.value)
            }
        // Each look is drawn once, away from the main thread, as soon as a vehicle in the covered
        // area has it: from just before pills show, so they're ready when they do.
        val specs = rememberUpdatedState(atlas.markers)
        LaunchedEffect(state, painter) {
            // Only in case the map meets a name before its picture arrives.
            state.missingImageResolver =
                object : MissingImageResolver {
                    override suspend fun resolve(request: MissingImageRequest): ResolvedStyleImage {
                        val spec = specs.value[request.id]
                        val bitmap = spec?.let { withContext(PAINTING) { painter.draw(it) } } ?: arrow
                        return ResolvedStyleImage.fromBitmap(bitmap)
                    }
                }
            ready.value = emptySet()
            snapshotFlow { state.style.loadState }.first { it is StyleLoadState.Ready }
            // Symbols appear at once: a vehicle moving to a new place shouldn't fade in there.
            state.style.transition.setPlacementTransitions(false)
            snapshotFlow { specs.value }.collectLatest { markers -> preload(state, painter, markers, ready) }
        }
        MaplibreMap(
            Modifier.fillMaxSize(),
            state = state,
            // A map to read, mostly north up: it only turns for a firm twist, never while pinching
            // to zoom, and settles back north when let go near it.
            interactions =
                MapInteractions(MapInteractions.Standard) {
                    camera {
                        rotate {
                            snapping {
                                enabled = true
                                targets = BearingTargets.at(0.0)
                                tolerance = NORTH_SNAP
                            }
                        }
                    }
                },
            uiOptions =
                MapUiOptions(MapUiOptions.Standard) {
                    renderMode = AndroidRenderMode.Surface
                    loadColor = loading
                    bindings {
                        transform {
                            rotate {
                                startAngle = ROTATE_FROM
                                allowDuringZoom = false
                            }
                        }
                    }
                },
            overlay = {},
        )
        ViewportReports(
            state,
            onViewport = { bounds, at ->
                val showPills = at >= PILLS_FROM - PILL_LEAD
                if (showPills != pillsShown.value) pillsShown.value = showPills
                if (at.toInt() != zoomStep.value) zoomStep.value = at.toInt()
                if (covered.value?.covers(bounds) != true) covered.value = bounds.grown(VIEW_MARGIN)
                view.value = bounds
                onViewport(bounds, at)
            },
            onGesture = onGesture,
        )
        CameraRequests(state, camera, glideMillis, bottomInset, topInset)
        // Down in the corner, clear of the status bar and whatever a screen puts at the top.
        MapCredits(
            { state.style.attributions() },
            Modifier.align(Alignment.BottomStart).padding(ObehyTheme.spacing.s),
        )
        // Turned or tilted, a way back to north up, just under whatever is at the top.
        NorthUp(
            state,
            Modifier.align(Alignment.TopEnd).padding(top = topInset, end = ObehyTheme.spacing.m),
        )
    }
}

/** Shows which way north is while the map is turned or tilted; a tap turns it back north up, flat. */
@Composable
private fun NorthUp(
    state: MapState,
    modifier: Modifier = Modifier,
) {
    // Read as a yes or no, so a pan or a zoom doesn't draw it again for every frame.
    val turned by remember(state) {
        derivedStateOf {
            val position = state.cameraPosition
            val off = (position.bearing % FULL_TURN + FULL_TURN) % FULL_TURN
            minOf(off, FULL_TURN - off) > STRAIGHT || position.pitch > STRAIGHT
        }
    }
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        turned,
        modifier,
        enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
    ) {
        SmallFloatingActionButton(
            onClick = {
                scope.launch {
                    state.animateCamera(
                        CameraUpdate(bearing = 0.0, pitch = 0.0),
                        CameraAnimation.Ease(CameraAnimation.Ease.Standard) { duration = REFRAME_TIME },
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                Icons.Rounded.Navigation,
                contentDescription = stringResource(R.string.map_north_up),
                // Its point is north, wherever that is on the screen now.
                modifier = Modifier.graphicsLayer { rotationZ = -state.cameraPosition.bearing.toFloat() },
            )
        }
    }
}

/** The vehicle selected last, and its marker's look: kept while it goes back among the rest. */
private class LastSelected {
    var vehicle: LiveVehicle? = null
    var look: Look? = null
}

/** What the map's own layers draw, read anew whenever any of it changes. */
private class LiveLayers(
    val glide: State<Glided>,
    val selectedVehicle: LiveVehicle?,
    val selectedLook: Look?,
    /** [selectedVehicle] is no longer selected, only drawn until the rest have it. */
    val departed: Boolean,
    val painter: VehicleMarkerPainter,
    val stops: String,
    val path: String,
    val me: String,
    val from: String,
    val atlas: MarkerAtlas,
    val arrow: ImageBitmap,
    val way: LivePath?,
    val tripStops: String,
    val signs: Map<String, ImageBitmap>,
    val colours: LiveColours,
    val onVehicleClick: (String) -> Unit,
    val onStopClick: (String) -> Unit,
)

private class LiveColours(
    val surface: String,
    val lime: String,
    val ink: String,
    val fallback: String,
    val me: String,
)

@Composable
private fun LiveContent(layers: LiveLayers) {
    val sizes = ObehyTheme.sizes
    val colours = layers.colours
    val stops = rememberGeoJsonSource(GeoJsonData.JsonString(layers.stops))
    val path = rememberGeoJsonSource(GeoJsonData.JsonString(layers.path))
    val me = rememberGeoJsonSource(GeoJsonData.JsonString(layers.me))
    val from = rememberGeoJsonSource(GeoJsonData.JsonString(layers.from))
    val vehicleClick: ClickEvent.(List<GeoFeature<Geometry, JsonObject?>>) -> ClickResult = { features ->
        features.idOf()?.let(layers.onVehicleClick)
        ClickResult.Consume
    }

    val font = const(listOf(STOP_FONT))
    val label = MaterialTheme.typography.labelMedium.fontSize
    val tripStops = rememberGeoJsonSource(GeoJsonData.JsonString(layers.tripStops))
    if (layers.way == null) {
        // Any stop: a plain sign from a little way out.
        SymbolLayer(
            id = "live-stops",
            source = stops,
            minZoom = STOPS_FROM,
            iconImage =
                switch(
                    Feature["sign"].asString(),
                    layers.signs.map { (name, bitmap) -> case(name, image(bitmap)) },
                    image(layers.signs.getValue(MapMode.Bus.name)),
                ),
            // Always shown, whatever else is on the map there.
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
            onClick = { features ->
                features.idOf()?.let(layers.onStopClick)
                ClickResult.Consume
            },
            // A small sign, but a finger-sized target.
            hitPadding = sizes.mapTapSlop,
        )
        // Their names once closer still.
        SymbolLayer(
            id = "live-stop-names",
            source = stops,
            minZoom = STOP_NAMES_FROM,
            textField = Feature["name"].asString(),
            textFont = font,
            textSize = const(label),
            textColor = const(colours.ink).convertToColor(),
            textHaloColor = const(colours.surface).convertToColor(),
            textHaloWidth = const(sizes.mapStopRim),
            textAnchor = const(SymbolAnchor.Left),
            textOffset = textOffset(STOP_LABEL_OFFSET.em, 0.em),
            textOptional = const(true),
        )
    }
    val way = layers.way
    if (way != null) {
        LineLayer(
            id = "live-path-casing",
            source = path,
            color = const(colours.surface).convertToColor(),
            width = const(sizes.mapPathCasing),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
        )
        val colour = const(way.color?.let { "#" + it.removePrefix("#") } ?: colours.fallback).convertToColor()
        if (way.estimated) {
            LineLayer(
                id = "live-path",
                source = path,
                color = colour,
                width = const(sizes.mapPath),
                join = const(LineJoin.Round),
                dasharray = const(listOf<Number>(DASH, GAP)),
            )
        } else {
            LineLayer(
                id = "live-path",
                source = path,
                color = colour,
                width = const(sizes.mapPath),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
        }
    }
    if (layers.way != null) {
        // The open trip's stops, as the web draws them: rings in its colour, grey once passed, named.
        CircleLayer(
            id = "live-trip-stops",
            source = tripStops,
            radius = const(sizes.mapStop),
            color = const(colours.surface).convertToColor(),
            strokeColor = Feature["rim"].asString().convertToColor(),
            strokeWidth = const(sizes.mapStopRim),
        )
        SymbolLayer(
            id = "live-trip-stop-names",
            source = tripStops,
            minZoom = TRIP_NAMES_FROM,
            textField = Feature["name"].asString(),
            textFont = font,
            textSize = const(label),
            textColor = Feature["ink"].asString().convertToColor(),
            textHaloColor = const(colours.surface).convertToColor(),
            textHaloWidth = const(sizes.mapStopRim),
            textAnchor = const(SymbolAnchor.Left),
            textOffset = textOffset(STOP_LABEL_OFFSET.em, 0.em),
            textOptional = const(true),
        )
    }
    CircleLayer(
        id = "live-trip-from",
        source = from,
        radius = const(sizes.mapStop + sizes.mapStopRim),
        color = const(colours.surface).convertToColor(),
        strokeColor = const(colours.lime).convertToColor(),
        strokeWidth = const(sizes.mapMeRim),
    )
    // Each glide step changes only these: the rest of the map's layers aren't gone over again.
    GlidingVehicles({ layers.glide.value }, vehicleClick)
    SelectedVehicle(layers)
    CircleLayer(
        id = "live-me-halo",
        source = me,
        radius = const(sizes.mapMeHalo),
        color = const(colours.me).convertToColor(),
        opacity = const(ME_HALO),
    )
    CircleLayer(
        id = "live-me",
        source = me,
        radius = const(sizes.mapMe),
        color = const(colours.me).convertToColor(),
        strokeColor = const(colours.surface).convertToColor(),
        strokeWidth = const(sizes.mapMeRim),
    )
}

/** The vehicles, at rest and gliding: the one part of the map redrawn for every step of a glide. */
@Composable
private fun GlidingVehicles(
    glide: () -> Glided,
    onClick: ClickEvent.(List<GeoFeature<Geometry, JsonObject?>>) -> ClickResult,
) {
    val frame = glide()
    val resting = rememberGeoJsonSource(GeoJsonData.JsonString(frame.rest))
    val moving = rememberGeoJsonSource(GeoJsonData.JsonString(frame.moving))
    VehicleLayers(resting, "rest", onClick)
    VehicleLayers(moving, "moving", onClick)
}

/** The selected vehicle over the rest, ringed in Lime; one just let go stays until the rest have it. */
@Composable
private fun SelectedVehicle(layers: LiveLayers) {
    val colours = layers.colours
    val frame = layers.glide.value
    val chosen = layers.selectedVehicle?.takeIf { !layers.departed || frame.without == it.id }
    val at = chosen?.let { frame.at(it.id) ?: it.at.position }
    val json =
        remember(chosen, layers.selectedLook, at) {
            val look = layers.selectedLook
            if (chosen == null || look == null || at == null) {
                EMPTY
            } else {
                listOf(chosen.feature(at, look, colours.fallback, colours.surface, layers.painter)).toCollection()
            }
        }
    val selected = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    // No halo round it: its own picture says it, ringed in Lime and a size up.
    VehicleTips(selected, "selected", size = SELECTED_SIZE)
    SymbolLayer(
        id = "live-selected",
        source = selected,
        filter = PICTURED,
        iconImage = image(Feature["icon"].asString()),
        iconSize = const(SELECTED_SIZE),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** The tips of a [source]'s pills, each turned by its vehicle's heading and set out to its pill's edge. */
@Composable
private fun VehicleTips(
    source: GeoJsonSource,
    name: String,
    size: Float = 1f,
) {
    SymbolLayer(
        id = "live-tips-$name",
        source = source,
        minZoom = PILLS_FROM,
        filter = Feature.has("tip"),
        iconImage = image(Feature["tip"].asString()),
        iconRotate = Feature["bearing"].asNumber(),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        // Out along the heading: the map turns the offset with the tip.
        iconOffset = Feature["reach"].asDpOffset(),
        iconSize = const(size),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** A vehicle still waiting for its pill's picture. */
private val WAITING = switch(Feature["icon"].asString(), listOf(case(DOT, const(true))), const(false))

/** A vehicle whose pill's picture the map has. */
private val PICTURED = switch(Feature["icon"].asString(), listOf(case(DOT, const(false))), const(true))

/** The vehicles of one [source]: dots when zoomed out, their pills from [PILLS_FROM]. */
@Composable
private fun VehicleLayers(
    source: GeoJsonSource,
    name: String,
    onClick: ClickEvent.(List<GeoFeature<Geometry, JsonObject?>>) -> ClickResult,
) {
    val sizes = ObehyTheme.sizes
    CircleLayer(
        id = "live-dots-$name",
        source = source,
        maxZoom = PILLS_FROM,
        radius = const(sizes.mapVehicleDot),
        color = Feature["fill"].asString().convertToColor(),
        strokeColor = Feature["rim"].asString().convertToColor(),
        strokeWidth = const(sizes.mapVehicleDotRim),
        onClick = onClick,
        // A small mark, but a finger-sized target.
        hitPadding = sizes.mapTapSlop,
    )
    // Closer in, a vehicle whose pill isn't drawn yet stays a dot until it is.
    CircleLayer(
        id = "live-dots-waiting-$name",
        source = source,
        minZoom = PILLS_FROM,
        filter = WAITING,
        radius = const(sizes.mapVehicleDot),
        color = Feature["fill"].asString().convertToColor(),
        strokeColor = Feature["rim"].asString().convertToColor(),
        strokeWidth = const(sizes.mapVehicleDotRim),
        onClick = onClick,
        hitPadding = sizes.mapTapSlop,
    )
    VehicleTips(source, name)
    SymbolLayer(
        id = "live-vehicles-$name",
        source = source,
        minZoom = PILLS_FROM,
        filter = PICTURED,
        iconImage = image(Feature["icon"].asString()),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = onClick,
        hitPadding = sizes.mapTapSlop,
    )
}

/** Tells [onViewport] what's in view each time the camera comes to rest, [onGesture] when a finger moves it. */
@Composable
private fun ViewportReports(
    state: MapState,
    onViewport: (MapBounds, Double) -> Unit,
    onGesture: (MapBounds) -> Unit,
) {
    val report by rememberUpdatedState(onViewport)
    val gesture by rememberUpdatedState(onGesture)
    val byHand = remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.cameraMoveReason }.collect { if (it is CameraMoveReason.Gesture) byHand.value = true }
    }
    LaunchedEffect(state) {
        var last: Pair<MapBounds, Double>? = null
        state.events.collect { event ->
            if (event is MapEvent.CameraMoveEnded && byHand.value) {
                byHand.value = false
                runCatching { state.getVisibleBounds() }.getOrNull()?.let { bounds ->
                    gesture(MapBounds(bounds.west, bounds.south, bounds.east, bounds.north))
                }
            }
            // The first rest after loading counts too: until then nothing knows what's in view. A rest
            // comes after every repaint, so only a view that has changed is told.
            if (event is MapEvent.CameraMoveEnded || event is MapEvent.StyleLoaded || event is MapEvent.Idle) {
                val bounds = runCatching { state.getVisibleBounds() }.getOrNull() ?: return@collect
                val view = MapBounds(bounds.west, bounds.south, bounds.east, bounds.north) to state.cameraPosition.zoom
                if (view != last) {
                    last = view
                    report(view.first, view.second)
                }
            }
        }
    }
}

/** Moves the camera for each new [camera] ask; following glides along with the vehicle. */
@Composable
private fun CameraRequests(
    state: MapState,
    camera: LiveCamera?,
    glideMillis: Int,
    bottomInset: Dp,
    topInset: Dp,
) {
    // Whatever lies over the map (the controls at the top, the sheet below) is kept clear of.
    val side = ObehyTheme.spacing.m
    val padding = DpPadding(left = side, top = topInset, right = side, bottom = bottomInset)
    val fitMargin = ObehyTheme.spacing.xxl
    val following = remember { mutableStateOf(false) }
    LaunchedEffect(state, camera, bottomInset, topInset) {
        when (camera) {
            null -> {
                following.value = false
            }

            is LiveCamera.Show -> {
                following.value = false
                state.animateCamera(
                    CameraUpdate(
                        center = Position(camera.at.lon, camera.at.lat),
                        zoom = camera.zoom,
                        padding = padding,
                    ),
                    CameraAnimation.Ease(CameraAnimation.Ease.Standard) { duration = REFRAME_TIME },
                )
            }

            is LiveCamera.Fit -> {
                following.value = false
                if (camera.points.isEmpty()) return@LaunchedEffect
                val box =
                    BoundingBox(
                        camera.points.minOf { it.lon },
                        camera.points.minOf { it.lat },
                        camera.points.maxOf { it.lon },
                        camera.points.maxOf { it.lat },
                    )
                state.animateCameraToBounds(
                    box,
                    CameraFit(
                        cameraPadding = padding,
                        fitPadding = DpPadding(fitMargin, fitMargin, fitMargin, fitMargin),
                    ),
                    CameraAnimation.Ease(CameraAnimation.Ease.Standard) { duration = REFRAME_TIME },
                )
            }

            is LiveCamera.Follow -> {
                val steady = following.value
                following.value = true
                state.animateCamera(
                    CameraUpdate(
                        center = Position(camera.at.lon, camera.at.lat),
                        // Once following, the zoom is the user's.
                        zoom = if (steady) null else camera.zoom,
                        padding = padding,
                    ),
                    CameraAnimation.Ease(CameraAnimation.Ease.Standard) {
                        duration = if (steady) glideMillis.milliseconds else REFRAME_TIME
                        if (steady) easing = CubicBezier.Linear
                    },
                )
            }
        }
    }
}

/** The look of each marker the vehicles on the map need, by name: cheap, nothing drawn yet. */
internal class MarkerAtlas(
    val markers: Map<String, MarkerSpec>,
)

/** What a marker's picture shows, enough to draw it: a vehicle's pill, or a tip in its colours. */
internal class MarkerSpec(
    val vehicle: LiveVehicle,
    val certainty: VehicleCertainty,
    val tip: Boolean,
    val selected: Boolean,
)

/** The pictures a vehicle is drawn with, by name: its pill, and its tip when it has a heading. */
internal data class Look(
    val icon: String,
    val tip: String?,
)

/** A picture for each name in [looks], taken from the first vehicle that has it: tips first, as they're quick. */
private fun markerSpecs(
    vehicles: List<LiveVehicle>,
    looks: Map<String, Look>,
    now: Instant,
): MarkerAtlas {
    val tips = LinkedHashMap<String, MarkerSpec>()
    val pills = LinkedHashMap<String, MarkerSpec>()
    for (vehicle in vehicles) {
        val look = looks[vehicle.id] ?: continue
        val selected = look.icon.endsWith(SELECTED)
        look.tip?.let {
            if (it !in
                tips
            ) {
                tips[it] = MarkerSpec(vehicle, vehicle.certaintyAt(now), tip = true, selected)
            }
        }
        if (look.icon !in pills) pills[look.icon] = MarkerSpec(vehicle, vehicle.certaintyAt(now), tip = false, selected)
    }
    return MarkerAtlas(tips + pills)
}

/** The pictures each vehicle is shown with: the ones it wants once the map has them, until then the pill it had. */
private class MarkerNames {
    private var shown = emptyMap<String, Look>()

    fun publish(
        wanted: Map<String, Look>,
        ready: Set<String>,
    ): Map<String, Look> {
        val next =
            wanted.mapValues { (id, look) ->
                Look(
                    icon = if (look.icon in ready) look.icon else shown[id]?.icon?.takeIf { it in ready } ?: DOT,
                    tip = look.tip?.takeIf { it in ready },
                )
            }
        shown = next
        return next
    }
}

/**
 * Draws the [markers] the map doesn't have yet, a batch at a time, hands each batch to the map and
 * then marks it [ready]. Each batch is twice the one before: the first pills show soon, and a
 * screenful needs only a few rounds of the vehicles being named anew. Once there are many, the
 * pictures no vehicle wants any more are dropped.
 */
private suspend fun preload(
    state: MapState,
    painter: VehicleMarkerPainter,
    markers: Map<String, MarkerSpec>,
    ready: MutableState<Set<String>>,
) {
    var missing = markers.entries.filter { it.key !in ready.value }
    var batch = PICTURE_BATCH
    while (missing.isNotEmpty()) {
        val next = missing.take(batch)
        missing = missing.drop(batch)
        batch *= 2
        val images =
            withContext(PAINTING) {
                next.associate { (name, spec) -> name to ResolvedStyleImage.fromBitmap(painter.draw(spec)) }
            }
        state.style.images.setAll(images)
        withTimeoutOrNull(PICTURE_WAIT_MS) { state.style.images.get(next.last().key) }
        ready.value += images.keys
    }
    if (ready.value.size > PICTURES_KEPT) {
        val unused = ready.value - markers.keys
        ready.value -= unused
        unused.forEach { state.style.images.remove(it) }
    }
}

private fun VehicleMarkerPainter.draw(spec: MarkerSpec): ImageBitmap =
    if (spec.tip) {
        drawTip(spec.vehicle.color, spec.vehicle.textColor, spec.certainty, spec.selected)
    } else {
        draw(
            line = spec.vehicle.line ?: "",
            color = spec.vehicle.color,
            textColor = spec.vehicle.textColor,
            certainty = spec.certainty,
            delayMinutes = spec.vehicle.delayMinutes?.takeIf { spec.vehicle.late },
            heading = null,
            selected = spec.selected,
        )
    }

private fun LiveVehicle.certaintyAt(now: Instant): VehicleCertainty =
    when {
        at.estimated -> VehicleCertainty.Estimated
        at.isStaleAt(now) -> VehicleCertainty.Stale
        else -> VehicleCertainty.Live
    }

/** The pictures this vehicle wants: named by all they show, so the same look is drawn once. */
private fun LiveVehicle.wantedLook(
    now: Instant,
    selected: Boolean,
): Look {
    val certainty = certaintyAt(now).name
    val mark = if (selected) SELECTED else ""
    return Look(
        icon = listOf(line, color, textColor, certainty, delayMinutes?.takeIf { late }).joinToString("|") + mark,
        tip = at.bearing?.let { listOf(TIP, color, textColor, certainty).joinToString("|") + mark },
    )
}

/** The glide under way: what it started from, so new names alone don't start it over. */
private class GlideRun {
    var vehicles: List<LiveVehicle>? = null
    var selectedId: String? = null
    var snap = 0.0
    var from = Glided(EMPTY, EMPTY, emptyMap())
    var view: MapBounds? = null
    var started = 0L
    var moving: Set<String> = emptySet()
}

/**
 * Where the vehicles are drawn now: those at rest ([rest], sent to the map once per update) and
 * those gliding to where they were last reported ([moving], sent each frame of the glide).
 */
private class Glided(
    val rest: String,
    val moving: String,
    /** Where each vehicle is at rest, or is going. */
    val still: Map<String, LatLon>,
    /** Where the gliding ones are drawn now, on their way there. */
    val moved: Map<String, LatLon> = emptyMap(),
    /** The vehicle left out of both, as it's drawn on its own (the selected one). */
    val without: String? = null,
) {
    /** Where vehicle [id] is drawn now. */
    fun at(id: String): LatLon? = moved[id] ?: still[id]
}

/**
 * The vehicles as a GeoJSON collection, each sliding from where it was drawn to its new place over
 * [glideMillis]; one that's new or jumped further than a vehicle moves between answers appears in
 * place, and so does one out of [view]: no one sees it, so it costs nothing each step. Worked out
 * away from the main thread.
 */
@Composable
private fun rememberGlide(
    vehicles: List<LiveVehicle>,
    glideMillis: Int,
    selectedId: String?,
    keys: Map<String, Look>,
    snap: Double,
    view: () -> MapBounds?,
    feature: (LiveVehicle, LatLon, Look) -> String,
): State<Glided> {
    val state = remember { mutableStateOf(Glided(EMPTY, EMPTY, emptyMap())) }
    var glided by state
    val make by rememberUpdatedState(feature)
    val currentView by rememberUpdatedState(view)
    val run = remember { GlideRun() }
    LaunchedEffect(vehicles, selectedId, keys, snap) {
        // New names alone (a picture now drawn) carry on with the glide under way.
        val fresh = run.vehicles !== vehicles || run.selectedId != selectedId || run.snap != snap
        if (fresh) {
            run.vehicles = vehicles
            run.selectedId = selectedId
            run.snap = snap
            run.from = glided
            run.view = currentView()
            run.started = System.currentTimeMillis()
        }
        val from = run.from
        val seen = run.view
        val started = run.started
        val shown = vehicles.filter { it.id != selectedId }
        // Who glides: a vehicle seen before, that moved more than can be seen but less than a jump,
        // within sight.
        val gliding =
            if (glideMillis <= 0) {
                emptySet()
            } else {
                shown
                    .filter { vehicle ->
                        val was = from.at(vehicle.id) ?: return@filter false
                        val to = vehicle.at.position
                        val step = maxOf(abs(was.lat - to.lat), abs(was.lon - to.lon))
                        step >= snap && step < JUMP && (seen == null || to in seen || was in seen)
                    }.mapTo(mutableSetOf()) { it.id }
            }

        fun look(vehicle: LiveVehicle) = keys[vehicle.id] ?: Look(DOT, null)
        // The rest take their places at once, in one update, and stay put through the glide.
        val still = withContext(Dispatchers.Default) { vehicles.associate { it.id to it.at.position } }
        val rest =
            withContext(Dispatchers.Default) {
                shown.filter { it.id !in gliding }.map { make(it, it.at.position, look(it)) }.toCollection()
            }
        val movers = shown.filter { it.id in gliding }
        // The two sources reach the map one after the other. A vehicle changing from one to the
        // other is first added to the new one, in the same place, and only then left out of the
        // old: never in neither for a frame.
        val wasMoving = run.moving
        val leaving = shown.filter { it.id in wasMoving && it.id !in gliding }
        val joining = movers.filter { it.id !in wasMoving }
        run.moving = gliding
        if (fresh && (leaving.isNotEmpty() || joining.isNotEmpty())) {
            val before = glided
            glided =
                withContext(Dispatchers.Default) {
                    val restFirst =
                        shown
                            .filter { it.id !in gliding || it.id !in wasMoving }
                            .map {
                                make(it, from.at(it.id)?.takeIf { _ -> it.id in gliding } ?: it.at.position, look(it))
                            }.toCollection()
                    val movingFirst =
                        (
                            movers.map { make(it, from.at(it.id) ?: it.at.position, look(it)) } +
                                leaving.map { make(it, it.at.position, look(it)) }
                        ).toCollection()
                    Glided(restFirst, movingFirst, before.still, before.moved, before.without)
                }
            delay(GLIDE_STEP_MS)
        }
        while (true) {
            val share =
                if (gliding.isEmpty() || glideMillis <= 0) {
                    1.0
                } else {
                    ((System.currentTimeMillis() - started).toDouble() / glideMillis).coerceAtMost(1.0)
                }
            glided =
                withContext(Dispatchers.Default) {
                    // Only the gliding ones are worked out anew each step.
                    val moved =
                        movers.associate { vehicle ->
                            val to = vehicle.at.position
                            val was = from.at(vehicle.id) ?: to
                            vehicle.id to
                                LatLon(was.lat + (to.lat - was.lat) * share, was.lon + (to.lon - was.lon) * share)
                        }
                    Glided(
                        rest,
                        movers.map { make(it, moved.getValue(it.id), look(it)) }.toCollection(),
                        still,
                        moved,
                        without = selectedId,
                    )
                }
            if (share >= 1.0) break
            delay(GLIDE_STEP_MS)
        }
    }
    return state
}

private fun LiveVehicle.feature(
    at: LatLon,
    look: Look,
    fallback: String,
    surface: String,
    painter: VehicleMarkerPainter,
): String {
    val colour = color?.let { if (it.startsWith("#")) it else "#$it" } ?: fallback
    val hollow = this.at.estimated
    val bearing = this.at.bearing
    // A tip once its picture and its pill's are in the map, set out to where the pill's edge is.
    val reach =
        bearing?.takeIf { look.tip != null && look.icon != DOT }?.let {
            painter.tipReach(
                line ?: "",
                it.toFloat(),
            )
        }
    val properties =
        buildString {
            append("{\"id\":").append(id.quoted())
            append(",\"icon\":").append(look.icon.quoted())
            append(",\"fill\":").append((if (hollow) surface else colour).quoted())
            append(",\"rim\":").append((if (hollow) colour else surface).quoted())
            bearing?.let { append(",\"bearing\":").append(it) }
            if (look.tip != null && reach != null) {
                append(",\"tip\":").append(look.tip.quoted())
                append(",\"reach\":[0,").append(-reach).append(']')
            }
            append('}')
        }
    return point(at, properties)
}

/** The `id` of the first of these clicked features that has one. */
private fun List<GeoFeature<Geometry, JsonObject?>>.idOf(): String? =
    firstNotNullOfOrNull {
        it.properties
            ?.get("id")
            ?.jsonPrimitive
            ?.content
    }

private fun List<LiveStop>.toFeatures(): String =
    map { stop ->
        val sign = stop.modes.map { MapMode.of(it) }.minOrNull() ?: MapMode.Bus
        point(
            stop.position,
            "{\"id\":${stop.id.quoted()},\"name\":${stop.name.quoted()},\"sign\":${sign.name.quoted()}}",
        )
    }.toCollection()

/** The open trip's stops: rings in its colour, grey and quiet once passed. */
private fun LivePath.tripStops(
    fallback: String,
    muted: String,
    ink: String,
    quiet: String,
): String {
    val colour = color?.let { "#" + it.removePrefix("#") } ?: fallback
    return stops
        .map { stop ->
            val rim = if (stop.passed) muted else colour
            val text = if (stop.passed) quiet else ink
            point(stop.position, "{\"name\":${stop.name.quoted()},\"rim\":${rim.quoted()},\"ink\":${text.quoted()}}")
        }.toCollection()
}

/** The mode a stop's sign shows. */
private val MapMode.sign: Mode
    get() =
        when (this) {
            MapMode.Rail -> Mode.RegionalRail
            MapMode.Metro -> Mode.Subway
            MapMode.Tram -> Mode.Tram
            MapMode.Bus -> Mode.Bus
            MapMode.Other -> Mode.Ferry
        }

private fun List<LatLon>.toLine(): String =
    if (size < 2) {
        EMPTY
    } else {
        val coordinates = joinToString(",") { "[${it.lon},${it.lat}]" }
        """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coordinates]}}]}"""
    }

private fun point(
    at: LatLon,
    properties: String,
) = """{"type":"Feature","properties":$properties,"geometry":{"type":"Point","coordinates":[${at.lon},${at.lat}]}}"""

private fun List<String>.toCollection(): String = """{"type":"FeatureCollection","features":[${joinToString(",")}]}"""

private fun String.quoted(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

/** How much larger the selected vehicle is drawn than the rest. */
private const val SELECTED_SIZE = 1.2f

/** A move shorter than this (in dp on screen) isn't glided: it can't be seen. */
private const val SNAP_DP = 2.0

/** How many degrees one dp spans at zoom 0 (512 dp tiles round the world). */
private const val DEGREES_PER_DP_AT_ZOOM_0 = 360.0 / 512.0

/** How long the map waits for a marker's picture to be drawn before making do. */
private const val PICTURE_WAIT_MS = 2_000L

/** How many pictures are drawn before they're handed to the map together. */
private const val PICTURE_BATCH = 24

/** Past this many pictures in the map, those no vehicle wants are dropped. */
private const val PICTURES_KEPT = 1_500

/** Markers are drawn one at a time, off the main thread: the painter isn't for two at once. */
@OptIn(ExperimentalCoroutinesApi::class)
private val PAINTING = Dispatchers.Default.limitedParallelism(1)

/** A dot's marker name: dots have no picture. */
private const val DOT = "dot"

/** Pictures are drawn a little before pills show, so they're there when they do. */
private const val PILL_LEAD = 1f

/** The vehicles worked on reach this far past the view (a share of it), so a small pan finds them drawn. */
private const val VIEW_MARGIN = 0.5

/** Vehicles are dots below this zoom, their pills from it on; stops come in from [STOPS_FROM]. */
private const val PILLS_FROM = 11f
private const val STOPS_FROM = 14f
private const val STOP_NAMES_FROM = 16f

/** The open trip's stops are named from here on. */
private const val TRIP_NAMES_FROM = 11f

/** Stop names sit this far right of their stop, in ems. */
private const val STOP_LABEL_OFFSET = 0.9f
private const val STOP_FONT = "Noto Sans Regular"

/** The name every tip picture starts with. */
private const val TIP = "tip"

/** The end of the selected vehicle's picture names. */
private const val SELECTED = "|selected"

/** Let go within this many degrees of north, the map settles north up. */
private const val NORTH_SNAP = 10.0

/** A twist of this many degrees before the map turns: a pinch or a pan never turns it by chance. */
private const val ROTATE_FROM = 20.0

/** Less than this (degrees) is still north up and flat. */
private const val STRAIGHT = 0.5
private const val FULL_TURN = 360.0

/** How strongly the halo round where you are shows. */
private const val ME_HALO = 0.2f

/** A dashed path: dash and gap, in line widths. */
private const val DASH = 2
private const val GAP = 1.5

/** Further than this (degrees, about 2 km) is a jump, not a move: drawn in place. */
private const val JUMP = 0.02

private const val GLIDE_STEP_MS = 100L
private val REFRAME_TIME = 800.milliseconds
