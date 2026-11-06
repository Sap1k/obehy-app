package cz.obehy.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R
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
): ImageBitmap {
    val spacing = ObehyTheme.spacing
    val sizes = ObehyTheme.sizes
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val estimated = certainty == VehicleCertainty.Estimated
    val fill = if (estimated) scheme.surface else colors.container
    val ink = if (estimated) scheme.onSurfaceVariant else colors.content
    val rim = if (estimated) scheme.outline else scheme.surface
    val alpha = if (certainty == VehicleCertainty.Stale) STALE_ALPHA else 1f
    val lineStyle = MaterialTheme.typography.labelSmall.copy(color = ink)
    val delay = delayMinutes?.takeIf { it >= 1 }?.let { stringResource(R.string.delay_marker, it) }
    val delayStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onErrorContainer)
    val delayFill = scheme.errorContainer
    val turned = heading?.let { (it / HEADING_STEP).roundToInt() * HEADING_STEP }
    return remember(line, fill, ink, rim, alpha, delay, turned, density, lineStyle, delayStyle) {
        with(density) {
            val name = measurer.measure(line.take(MAX_LINE_LENGTH), lineStyle)
            val late = delay?.let { measurer.measure(it, delayStyle) }
            val pad = spacing.xs.toPx()
            val height = sizes.mapVehicle.toPx()
            val pill = Size(maxOf(height, name.size.width + pad * 2), height)
            val tip = sizes.mapVehicleTip.toPx()
            val stroke = (spacing.xs / 2).toPx()
            // Room all round the middle, which is where the position goes.
            val around = tip * TIP_REACH + stroke + (late?.let { it.size.width + pad * 2 } ?: 0f)
            val canvasSize = Size(pill.width + around * 2, pill.height + around * 2)
            val bitmap = ImageBitmap(ceil(canvasSize.width).toInt(), ceil(canvasSize.height).toInt())
            CanvasDrawScope().draw(density, direction, Canvas(bitmap), canvasSize) {
                val corner = Offset(around, around)
                val radius = CornerRadius(pill.height / 2)
                val dashes = if (estimated) PathEffect.dashPathEffect(floatArrayOf(stroke * 2, stroke * 2)) else null
                turned?.let { pillTip(it.toFloat(), corner, pill, tip, stroke, fill, rim, alpha) }
                drawRoundRect(fill, corner, pill, radius, alpha = alpha)
                drawRoundRect(rim, corner, pill, radius, Stroke(stroke, pathEffect = dashes), alpha)
                drawText(
                    name,
                    topLeft =
                        corner + Offset((pill.width - name.size.width) / 2, (pill.height - name.size.height) / 2),
                    alpha = alpha,
                )
                late?.let {
                    val at = corner + Offset(pill.width - pad, -it.size.height / 2f)
                    val badge = Size(it.size.width + pad * 2, it.size.height.toFloat())
                    drawRoundRect(delayFill, at, badge, CornerRadius(badge.height / 2))
                    drawText(it, topLeft = at + Offset(pad, 0f))
                }
            }
            bitmap
        }
    }
}

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
