package cz.obehy.core.ui.components

import android.content.res.Resources
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cz.obehy.core.model.Mode
import cz.obehy.core.model.Punctuality
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R
import cz.obehy.core.ui.Sizes
import cz.obehy.core.ui.Spacing
import cz.obehy.core.ui.icon
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** How sure a map can be of where a vehicle is. */
enum class VehicleCertainty {
    /** Reported just now. */
    Live,

    /** Reported, but a while ago. */
    Stale,

    /** Worked out from its times: nothing reports it. */
    Estimated,
}

/** The most of a line's name that fits on its marker. */
private const val MAX_LINE_LENGTH = 6
private const val STALE_ALPHA = 0.7f

/** How much thicker a selected marker's rim is. */
private const val SELECTED_RIM = 2f

/** A marker is only drawn again for a turn of this many degrees: it's a picture, not a dial. */
private const val HEADING_STEP = 5

/** How far the tip's base sits under the pill's edge, and its point beyond it, as shares of its size. */
private const val TIP_BASE_INSET = 0.5f
private const val TIP_REACH = 1.25f

/**
 * A vehicle on a map, as a picture to centre on its position: a small pill with its [line] in the
 * route's [colors], and a tip pointing along [heading] (degrees clockwise from north, null when
 * it's not known). A guess looks like one: hollow and dashed, in quiet colours. A delay is spelled
 * out beside it, so colour never has to say so alone. A picture, because the map has to draw it
 * itself to keep it glued down as it's dragged: anything laid over a map runs ahead of it.
 */
@Composable
fun rememberVehicleMarker(
    line: String,
    colors: RouteColors,
    certainty: VehicleCertainty,
    delayMinutes: Long?,
    heading: Float?,
    selected: Boolean = false,
): ImageBitmap {
    val painter = rememberVehicleMarkerPainter()
    val turned = heading?.let { (it / HEADING_STEP).roundToInt() * HEADING_STEP }
    return remember(painter, line, colors, certainty, delayMinutes, turned, selected) {
        painter.draw(line, colors, certainty, delayMinutes, turned?.toFloat(), selected)
    }
}

/** A [VehicleMarkerPainter] for the current theme. */
@Composable
fun rememberVehicleMarkerPainter(): VehicleMarkerPainter {
    val scheme = MaterialTheme.colorScheme
    val label = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val resources = LocalResources.current
    val spacing = ObehyTheme.spacing
    val sizes = ObehyTheme.sizes
    return remember(scheme, label, density, direction, measurer, resources, spacing, sizes) {
        VehicleMarkerPainter(scheme, label, density, direction, measurer, resources, spacing, sizes)
    }
}

/**
 * Draws vehicle markers (see [rememberVehicleMarker]) with the theme it was made with, anywhere:
 * a map can have each drawn away from the main thread, and only when it shows it. Not for use
 * from two threads at once: its text measurer keeps a cache.
 */
class VehicleMarkerPainter internal constructor(
    private val scheme: ColorScheme,
    private val label: TextStyle,
    private val density: Density,
    private val direction: LayoutDirection,
    private val measurer: TextMeasurer,
    private val resources: Resources,
    private val spacing: Spacing,
    private val sizes: Sizes,
) {
    private val dark = scheme.surface.luminance() < HALF_LIGHT

    /** How wide each line's pill is drawn, halved, in dp: where a tip drawn apart from it goes. */
    private val halfWidths = ConcurrentHashMap<String, Float>()

    /**
     * How far from a marker's middle, in dp, the middle of its tip ([drawTip]) goes when it points
     * along [heading] on a map turned north up: just out from under the pill's edge, as the tip a
     * marker draws itself. Null until the pill for [line] has been drawn. Safe from any thread.
     */
    fun tipReach(
        line: String,
        heading: Float,
    ): Float? {
        val half = halfWidths[line.take(MAX_LINE_LENGTH)] ?: return null
        val radians = Math.toRadians(heading.toDouble())
        val way = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
        val edge = capsuleEdge(way, half, sizes.mapVehicle.value / 2).getDistance()
        return edge + sizes.mapVehicleTip.value * (TIP_REACH - TIP_BASE_INSET) / 2
    }

    /**
     * A marker's tip alone, pointing north, for a map to turn by each vehicle's heading and set
     * [tipReach] out from the middle of a pill drawn without one: one picture for every heading.
     */
    fun drawTip(
        color: String?,
        textColor: String?,
        certainty: VehicleCertainty,
        selected: Boolean = false,
    ): ImageBitmap {
        val look = look(routeColorsIn(scheme, color, textColor), certainty, selected)
        return with(density) {
            val tip = sizes.mapVehicleTip.toPx()
            val stroke = (spacing.xs / 2).toPx() * (if (selected) SELECTED_RIM else 1f)
            val length = tip * (TIP_REACH + TIP_BASE_INSET)
            val canvasSize = Size(tip * 2 + stroke * 2, length + stroke * 2)
            val bitmap = ImageBitmap(ceil(canvasSize.width).toInt(), ceil(canvasSize.height).toInt())
            CanvasDrawScope().draw(this, direction, Canvas(bitmap), canvasSize) {
                val middle = canvasSize.width / 2
                val path =
                    Path().apply {
                        moveTo(middle, stroke)
                        lineTo(middle + tip, stroke + length)
                        lineTo(middle - tip, stroke + length)
                        close()
                    }
                drawPath(path, look.fill, look.alpha)
                drawPath(path, look.rim, look.alpha, Stroke(stroke, join = StrokeJoin.Round))
            }
            bitmap
        }
    }

    private fun look(
        colors: RouteColors,
        certainty: VehicleCertainty,
        selected: Boolean,
    ): MarkerLook {
        val estimated = certainty == VehicleCertainty.Estimated
        // A guess keeps its route colour as a ring (and its lettering, where that reads on the surface).
        val routeInk = colors.container.takeUnless { it.blendsInto(scheme.surface) }
        return MarkerLook(
            fill = if (estimated) scheme.surface else colors.container,
            ink = if (estimated) routeInk ?: scheme.onSurfaceVariant else colors.content,
            rim =
                when {
                    selected -> scheme.primaryContainer
                    estimated -> routeInk ?: scheme.outline
                    else -> scheme.surface
                },
            alpha = if (certainty == VehicleCertainty.Stale) STALE_ALPHA else 1f,
        )
    }

    /** A marker for a vehicle in route colours given as `RRGGBB`. */
    fun draw(
        line: String,
        color: String?,
        textColor: String?,
        certainty: VehicleCertainty,
        delayMinutes: Long?,
        heading: Float?,
        selected: Boolean = false,
    ): ImageBitmap = draw(line, routeColorsIn(scheme, color, textColor), certainty, delayMinutes, heading, selected)

    fun draw(
        line: String,
        colors: RouteColors,
        certainty: VehicleCertainty,
        delayMinutes: Long?,
        heading: Float?,
        selected: Boolean = false,
    ): ImageBitmap {
        val estimated = certainty == VehicleCertainty.Estimated
        val (fill, ink, rim, alpha) = look(colors, certainty, selected)
        val rimScale = if (selected) SELECTED_RIM else 1f
        val lineStyle = label.copy(color = ink)
        val delay =
            delayMinutes
                ?.takeIf {
                    Punctuality.isLate(
                        it,
                    )
                }?.let { resources.getString(R.string.delay_marker, it) }
        val delayTone = PunctualityTone.of(delayMinutes).colorsIn(dark)
        val delayStyle = label.copy(color = delayTone.text)
        return with(density) {
            val name = measurer.measure(line.take(MAX_LINE_LENGTH), lineStyle)
            val late = delay?.let { measurer.measure(it, delayStyle) }
            val pad = spacing.xs.toPx()
            val height = sizes.mapVehicle.toPx()
            val pill = Size(maxOf(height, name.size.width + pad * 2), height)
            halfWidths[line.take(MAX_LINE_LENGTH)] = pill.width.toDp().value / 2
            val tip = sizes.mapVehicleTip.toPx()
            val stroke = (spacing.xs / 2).toPx() * rimScale
            // Room all round the middle, which is where the position goes.
            val around = tip * TIP_REACH + stroke + (late?.let { it.size.width + pad * 2 } ?: 0f)
            val canvasSize = Size(pill.width + around * 2, pill.height + around * 2)
            val bitmap = ImageBitmap(ceil(canvasSize.width).toInt(), ceil(canvasSize.height).toInt())
            CanvasDrawScope().draw(this, direction, Canvas(bitmap), canvasSize) {
                val corner = Offset(around, around)
                val radius = CornerRadius(pill.height / 2)
                val dashes = if (estimated) PathEffect.dashPathEffect(floatArrayOf(stroke * 2, stroke * 2)) else null
                heading?.let { pillTip(it, corner, pill, tip, stroke, fill, rim, alpha) }
                drawRoundRect(fill, corner, pill, radius, alpha = alpha)
                drawRoundRect(rim, corner, pill, radius, Stroke(stroke, pathEffect = dashes), alpha)
                drawText(
                    name,
                    topLeft = corner + Offset((pill.width - name.size.width) / 2, (pill.height - name.size.height) / 2),
                    alpha = alpha,
                )
                late?.let {
                    val at = corner + Offset(pill.width - pad, -it.size.height / 2f)
                    val badge = Size(it.size.width + pad * 2, it.size.height.toFloat())
                    drawRoundRect(delayTone.fill, at, badge, CornerRadius(badge.height / 2))
                    drawText(it, topLeft = at + Offset(pad, 0f))
                }
            }
            bitmap
        }
    }
}

private const val HALF_LIGHT = 0.5f

/** A marker's colours: [fill] and [rim] for the pill and its tip, [ink] for its line. */
private data class MarkerLook(
    val fill: Color,
    val ink: Color,
    val rim: Color,
    val alpha: Float,
)

/** The tip showing which way the pill faces, sticking out from under its edge. */
private fun DrawScope.pillTip(
    heading: Float,
    corner: Offset,
    pill: Size,
    length: Float,
    stroke: Float,
    fill: Color,
    rim: Color,
    alpha: Float,
) {
    val radians = Math.toRadians(heading.toDouble())
    val way = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
    val across = Offset(-way.y, way.x) * length
    val middle = corner + Offset(pill.width / 2, pill.height / 2)
    val edge = middle + capsuleEdge(way, pill.width / 2, pill.height / 2)
    // Its base well under the pill, so a curved end of it leaves no gap.
    val base = edge - way * (length * TIP_BASE_INSET)
    val point = edge + way * (length * TIP_REACH)
    val path =
        Path().apply {
            moveTo(base.x + across.x, base.y + across.y)
            lineTo(point.x, point.y)
            lineTo(base.x - across.x, base.y - across.y)
            close()
        }
    drawPath(path, fill, alpha)
    drawPath(path, rim, alpha, Stroke(stroke, join = StrokeJoin.Round))
}

/**
 * Where a ray from the middle of a capsule leaves it, heading along the unit vector [way]: through
 * one of its flat sides, or else one of the half circles capping them.
 */
internal fun capsuleEdge(
    way: Offset,
    halfWidth: Float,
    radius: Float,
): Offset {
    val flat = (halfWidth - radius).coerceAtLeast(0f)
    if (way.y != 0f) {
        val x = radius * way.x / abs(way.y)
        if (abs(x) <= flat) return Offset(x, radius * sign(way.y))
    }
    val cap = sign(way.x) * flat
    val along = way.x * cap
    val distance = along + sqrt((along * along - cap * cap + radius * radius).coerceAtLeast(0f))
    return way * distance
}

/**
 * A small tip pointing north at the top of a square [ObehyTheme.sizes.mapHeading] wide, for a map
 * to turn by each vehicle's heading under its marker: one picture for every vehicle and direction.
 */
@Composable
fun rememberHeadingArrow(): ImageBitmap {
    val sizes = ObehyTheme.sizes
    val spacing = ObehyTheme.spacing
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val fill = scheme.inverseSurface
    val rim = scheme.surface
    return remember(fill, rim, density, sizes, spacing) {
        with(density) {
            val side = sizes.mapHeading.toPx()
            val tip = sizes.mapVehicleTip.toPx()
            val stroke = (spacing.xs / 2).toPx()
            val bitmap = ImageBitmap(ceil(side).toInt(), ceil(side).toInt())
            CanvasDrawScope().draw(density, direction, Canvas(bitmap), Size(side, side)) {
                val middle = side / 2
                val path =
                    Path().apply {
                        moveTo(middle, stroke)
                        lineTo(middle + tip, stroke + tip * TIP_REACH)
                        lineTo(middle - tip, stroke + tip * TIP_REACH)
                        close()
                    }
                drawPath(path, fill)
                drawPath(path, rim, style = Stroke(stroke, join = StrokeJoin.Round))
            }
            bitmap
        }
    }
}

/**
 * A stop on a live map: a small rounded sign with its [mode]'s glyph, like the signs at the
 * kerb. Drawn once per mode, for the map to place.
 */
@Composable
fun rememberStopSign(mode: Mode): ImageBitmap {
    val sizes = ObehyTheme.sizes
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val glyph = rememberVectorPainter(mode.icon)
    val fill = scheme.surface
    val rim = scheme.outline
    val ink = scheme.onSurfaceVariant
    return remember(mode, fill, rim, ink, density, sizes) {
        with(density) {
            val side = sizes.mapStopSign.toPx()
            val inner = sizes.mapStopGlyph.toPx()
            val stroke = sizes.mapVehicleDotRim.toPx()
            val bitmap = ImageBitmap(ceil(side).toInt(), ceil(side).toInt())
            CanvasDrawScope().draw(density, direction, Canvas(bitmap), Size(side, side)) {
                val corner = CornerRadius(side / 4)
                drawRoundRect(fill, cornerRadius = corner)
                drawRoundRect(
                    rim,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(side - stroke, side - stroke),
                    cornerRadius = corner,
                    style = Stroke(stroke),
                )
                translate((side - inner) / 2, (side - inner) / 2) {
                    with(glyph) { draw(Size(inner, inner), colorFilter = ColorFilter.tint(ink)) }
                }
            }
            bitmap
        }
    }
}
