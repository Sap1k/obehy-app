package cz.obehy.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cz.obehy.core.model.Leg
import cz.obehy.core.model.Mode
import cz.obehy.core.model.RowSpan
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.icon
import java.time.Instant

/** What sits on the route line at a row. */
enum class RouteMark {
    /** Where the journey starts, and every stop where you board or alight: a ring. */
    Stop,

    /** A stop the search must go through: a diamond. */
    Via,

    /** Where the vehicle goes on as another line, so you stay on board: a ring in both colours. */
    Through,

    /** Where the journey ends: a filled dot. */
    End,

    /** A stop passed on the way: a small ring on the line. */
    Pass,

    /** Nothing: the line runs straight through (a ride, a walk). */
    None,
}

/** A stretch of the route line: a ride in its route colour, or a dotted walk. */
@Immutable
data class RouteStroke(
    val color: Color,
    val dotted: Boolean = false,
)

/** How much of a stroke's colour is left once it has gone by, and how much of its width. */
private const val PASSED_COLOUR = 0.45f
private const val PASSED_WIDTH = 0.5f

/** How big a mark is once it has gone by, as a share of its size. */
private const val PASSED_MARK = 0.5f

/** What marks the point reached on a route line: the way you're getting along, as the map shows it. */
@Immutable
data class RouteHere(
    val icon: ImageVector,
    val fill: Color,
    val ink: Color,
    /** An edge for a fill that would vanish into the background. */
    val rim: Color? = null,
)

/** The [RouteHere] of [ride], in its route's colours. */
@Composable
fun rideHere(ride: Leg.Transit): RouteHere {
    val colors = routeColors(ride.color, ride.textColor)
    return RouteHere(ride.mode.icon, colors.container, colors.content, colors.outline)
}

/** The [RouteHere] of a walk, as quiet as its dotted line. */
@Composable
fun walkHere(): RouteHere {
    val colors = MaterialTheme.colorScheme
    return RouteHere(Mode.Walk.icon, colors.surfaceContainerHighest, colors.onSurfaceVariant, colors.outline)
}

/** A [RouteHere] on its own, away from the line. */
@Composable
fun RouteHereBadge(
    here: RouteHere,
    modifier: Modifier = Modifier,
) {
    val sizes = ObehyTheme.sizes
    Box(
        modifier
            .size(sizes.routeHere)
            .background(here.fill, CircleShape)
            .then(here.rim?.let { Modifier.border(1.dp, it, CircleShape) } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(here.icon, contentDescription = null, Modifier.size(sizes.routeHereIcon), tint = here.ink)
    }
}

/**
 * How far along a timeline has got on one row: the stretch of time the row covers, the clock, and
 * the [here] to mark the point reached with. Reading [now] only while drawing keeps a tick of the
 * clock from recomposing the timeline.
 */
@Immutable
data class RouteProgress(
    val span: RowSpan,
    val now: () -> Instant,
    val here: RouteHere,
)

/** Whether this row's own time has gone by; it changes only when it does. */
@Composable
fun rememberPassed(progress: RouteProgress?): Boolean {
    val passed by remember(progress) {
        derivedStateOf { progress != null && progress.now() >= progress.span.at }
    }
    return passed
}

/** The route line's colour for walks. */
@Composable
fun walkStroke(): RouteStroke = RouteStroke(MaterialTheme.colorScheme.outline, dotted = true)

/** The route line's colour for the part of a trip the journey doesn't ride on. */
@Composable
fun quietStroke(): RouteStroke = RouteStroke(MaterialTheme.colorScheme.outline)

/**
 * One row along a route line, shared by the search form (from, via, to) and a journey's detail
 * (stops, rides, walks), so both draw the same line. The line comes in [above] the [mark] and
 * leaves [below] it; [leading] (e.g. a time) sits in front of the line, [content] after it.
 * With [markAt], everything is aligned to the top and the mark sits that far down, so it stays on
 * a stop's first line when the row grows. With [progress], what has gone by thins out and fades,
 * still in its own colour, and a badge marks the point reached.
 */
@Composable
fun RouteRow(
    mark: RouteMark,
    modifier: Modifier = Modifier,
    above: RouteStroke? = null,
    below: RouteStroke? = null,
    markColor: Color = above?.color ?: below?.color ?: MaterialTheme.colorScheme.primary,
    markAt: Dp? = null,
    progress: RouteProgress? = null,
    leading: @Composable () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val sizes = ObehyTheme.sizes
    val surface = MaterialTheme.colorScheme.surface
    val edge = MaterialTheme.colorScheme.outline
    val secondColor = if (mark == RouteMark.Through) below?.color ?: markColor else markColor
    val icon = progress?.let { rememberVectorPainter(it.here.icon) }
    val holding by remember(progress) {
        derivedStateOf { progress != null && progress.span.holds(progress.now()) }
    }

    fun gone(color: Color) = lerp(surface, if (color.blendsInto(surface)) edge else color, PASSED_COLOUR)
    Row(
        // Above the row after it, whose line would otherwise cross the badge at the edge between them.
        modifier.zIndex(if (holding) 1f else 0f).height(IntrinsicSize.Min),
        verticalAlignment = if (markAt == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        leading()
        Box(
            Modifier
                .width(sizes.routeGutter)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val y = markAt?.toPx() ?: (size.height / 2)
                    val width = sizes.routeStroke.toPx()
                    val now = progress?.now?.invoke()
                    val reached = now?.let { progress.span.reached(it, y, size.height) } ?: 0f

                    fun part(
                        stroke: RouteStroke,
                        top: Float,
                        bottom: Float,
                    ) {
                        val split = reached.coerceIn(top, bottom)
                        if (split > top) {
                            val faded = stroke.copy(color = gone(stroke.color))
                            stroke(faded, Offset(x, top), Offset(x, split), width * PASSED_WIDTH, null)
                        }
                        if (bottom > split) {
                            val outlined = edge.takeIf { stroke.color.blendsInto(surface) }
                            stroke(stroke, Offset(x, split), Offset(x, bottom), width, outlined)
                        }
                    }
                    above?.let { part(it, 0f, y) }
                    below?.let { part(it, y, size.height) }
                    val centre = Offset(x, y)
                    val radius = (if (mark == RouteMark.Pass) sizes.routePassMarker else sizes.routeMarker).toPx() / 2
                    if (now != null && reached >= y) {
                        if (mark != RouteMark.None) {
                            drawCircle(surface, radius, centre)
                            drawCircle(gone(markColor), radius * PASSED_MARK, centre)
                        }
                    } else {
                        marker(
                            mark,
                            centre,
                            radius,
                            markColor,
                            secondColor,
                            surface,
                            edge.takeIf { markColor.blendsInto(surface) || secondColor.blendsInto(surface) },
                        )
                    }
                    if (now != null && icon != null && progress.span.holds(now)) {
                        val here = progress.here
                        val at = Offset(x, reached)
                        val badge = sizes.routeHere.toPx() / 2
                        val inner = sizes.routeHereIcon.toPx()
                        drawCircle(surface, badge + 2.dp.toPx(), at)
                        drawCircle(here.fill, badge, at)
                        here.rim?.let { drawCircle(it, badge, at, style = Stroke(1.dp.toPx())) }
                        translate(x - inner / 2, reached - inner / 2) {
                            with(icon) { draw(Size(inner, inner), colorFilter = ColorFilter.tint(here.ink)) }
                        }
                    }
                },
        )
        content()
    }
}

private fun DrawScope.stroke(
    stroke: RouteStroke,
    start: Offset,
    end: Offset,
    width: Float,
    edge: Color?,
) {
    if (edge != null && !stroke.dotted) drawLine(edge, start, end, strokeWidth = width + 2.dp.toPx())
    drawLine(
        stroke.color,
        start,
        end,
        strokeWidth = if (stroke.dotted) width * 0.75f else width,
        cap = if (stroke.dotted) StrokeCap.Round else StrokeCap.Butt,
        pathEffect = if (stroke.dotted) PathEffect.dashPathEffect(floatArrayOf(0.1f, width * 1.6f)) else null,
    )
}

private fun DrawScope.marker(
    mark: RouteMark,
    centre: Offset,
    radius: Float,
    color: Color,
    secondColor: Color,
    surface: Color,
    edge: Color?,
) {
    val ring = (if (mark == RouteMark.Pass) 2.dp else 3.dp).toPx()
    if (edge != null && mark != RouteMark.None) drawCircle(edge, radius + 1.dp.toPx(), centre)
    when (mark) {
        RouteMark.None -> {
            Unit
        }

        RouteMark.Stop, RouteMark.Pass -> {
            drawCircle(surface, radius, centre)
            drawCircle(color, radius - ring / 2, centre, style = Stroke(ring))
            // A ring that would vanish needs its edge on the inside too.
            if (edge != null) {
                val width = 1.dp.toPx()
                drawCircle(edge, radius - ring - width / 2, centre, style = Stroke(width))
            }
        }

        RouteMark.Through -> {
            drawCircle(surface, radius, centre)
            val middle = radius - ring / 2
            val corner = Offset(centre.x - middle, centre.y - middle)
            val box = Size(middle * 2, middle * 2)
            drawArc(color, 180f, 180f, useCenter = false, topLeft = corner, size = box, style = Stroke(ring))
            drawArc(secondColor, 0f, 180f, useCenter = false, topLeft = corner, size = box, style = Stroke(ring))
            if (edge != null) {
                val width = 1.dp.toPx()
                drawCircle(edge, radius - ring - width / 2, centre, style = Stroke(width))
            }
        }

        RouteMark.End -> {
            drawCircle(color, radius, centre)
        }

        RouteMark.Via -> {
            val diamond =
                Path().apply {
                    moveTo(centre.x, centre.y - radius)
                    lineTo(centre.x + radius, centre.y)
                    lineTo(centre.x, centre.y + radius)
                    lineTo(centre.x - radius, centre.y)
                    close()
                }
            drawPath(diamond, surface)
            drawPath(diamond, color, style = Stroke(ring))
        }
    }
}
