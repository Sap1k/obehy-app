package cz.obehy.core.map

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpSize
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Leg
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.routeBlendsInto
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
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.serialization.json.Json as JsonParser

/**
 * A journey on the Oběhy map: each ride in its route's colour, walks dashed, a dot at every stop
 * where you board or alight, framed to show the whole way. [interactive] lets it be panned and
 * zoomed; otherwise it is a still picture (a screen's header). A [fullscreen] map fills the
 * window, edge to edge.
 */
@Composable
fun RouteMap(
    legs: List<Leg>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    fullscreen: Boolean = false,
) {
    val context = LocalContext.current
    val look = mapLook()
    val loading = MaterialTheme.colorScheme.surfaceContainer
    val shape = remember(legs) { legs.map { it.shape() } }
    // Keyed on the text too: fresh times bring new legs, but the same map, which needn't reload.
    val json = remember(legs, look) { journeyStyle(context, look, legs) }
    val style = remember(json) { BaseStyle.Json(json) }
    BoxWithConstraints(modifier) {
        val creditsModifier =
            Modifier
                .align(Alignment.BottomEnd)
                .then(if (fullscreen) Modifier.navigationBarsPadding() else Modifier)
                .padding(ObehyTheme.spacing.s)
        val still =
            remember(json, maxWidth, maxHeight, interactive) {
                if (interactive) null else stillMap(json, DpSize(maxWidth, maxHeight))
            }
        if (still != null) {
            Image(still.image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            MapCredits({ still.credits }, creditsModifier)
        } else {
            val camera =
                remember(shape, maxWidth, maxHeight) { frame(shape.flatten(), maxWidth.value, maxHeight.value) }
            val state = rememberMapState(baseStyle = style, initialCameraPosition = camera)
            MaplibreMap(
                Modifier.fillMaxSize(),
                state = state,
                interactions = if (interactive) MapInteractions.Standard else MapInteractions.None,
                // A texture, not a surface: only a texture scales, clips and fades with the screen
                // around it, which the back gesture's preview and the card's morph both depend on.
                uiOptions =
                    MapUiOptions(if (interactive) MapUiOptions.Standard else MapUiOptions.None) {
                        renderMode = AndroidRenderMode.Texture
                        loadColor = loading
                    },
                // The map data's credits (OpenStreetMap, OpenMapTiles), without MapLibre's logo.
                overlay = { MapCredits({ state.style.attributions() }, creditsModifier) },
            )
        }
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
    FilledTonalIconButton(onClick = { open = true }, modifier.size(ObehyTheme.sizes.mapCredits)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(R.string.map_credits),
            modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
        )
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
): String {
    val primary = look.primary
    val walk = look.walk
    val asset = if (look.dark) "map-style-dark.json" else "map-style-light.json"
    val base =
        JsonParser
            .parseToJsonElement(
                context.assets
                    .open(asset)
                    .bufferedReader()
                    .use { it.readText() },
            ).jsonObject
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
    val walkLayer =
        buildJsonObject {
            put("id", "journey-walk")
            put("type", "line")
            put("source", "trip")
            putJsonArray("filter") {
                add(JsonPrimitive("=="))
                addJsonArray {
                    add(JsonPrimitive("get"))
                    add(JsonPrimitive("kind"))
                }
                add(JsonPrimitive("walk"))
            }
            putJsonObject("layout") { put("line-cap", "round") }
            putJsonObject("paint") {
                put("line-color", walk)
                put("line-width", 3)
                putJsonArray("line-dasharray") {
                    add(JsonPrimitive(0.5))
                    add(JsonPrimitive(2))
                }
            }
        }
    val layers =
        buildJsonArray {
            base.getValue("layers").jsonArray.forEach { layer ->
                if (layer.jsonObject["id"] == JsonPrimitive("trip-casing")) {
                    add(walkLayer)
                    add(layer.withCasingColour())
                } else {
                    add(layer)
                }
            }
        }
    return JsonObject(base + mapOf("sources" to sources, "layers" to layers)).toString()
}

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
): JsonObject =
    buildJsonObject {
        put("type", "Feature")
        putJsonObject("properties") {
            put("kind", kind)
            put("colour", colour)
            put("passed", false)
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

/** A camera showing every one of [points] in a [width]×[height] dp map, with a margin. */
internal fun frame(
    points: List<LatLon>,
    width: Float,
    height: Float,
): CameraPosition {
    if (points.isEmpty()) return CameraPosition(Position(15.5, 49.8), zoom = 6.0)
    val margin = 48.0
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
