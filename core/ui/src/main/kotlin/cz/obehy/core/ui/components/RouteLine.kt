package cz.obehy.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.obehy.core.ui.ObehyTheme

/** What sits on the route line at a row. */
enum class RouteMark {
    /** Where the journey starts, and every stop where you board or alight: a ring. */
    Stop,

    /** A stop the search must go through: a diamond. */
    Via,

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

/** The route line's colour for walks. */
@Composable
fun walkStroke(): RouteStroke = RouteStroke(MaterialTheme.colorScheme.outline, dotted = true)

/**
 * One row along a route line, shared by the search form (from, via, to) and a journey's detail
 * (stops, rides, walks), so both draw the same line. The line comes in [above] the [mark] and
 * leaves [below] it; [leading] (e.g. a time) sits in front of the line, [content] after it.
 * With [markAt], everything is aligned to the top and the mark sits that far down, so it stays on
 * a stop's first line when the row grows.
 */
@Composable
fun RouteRow(
    mark: RouteMark,
    modifier: Modifier = Modifier,
    above: RouteStroke? = null,
    below: RouteStroke? = null,
    markColor: Color = above?.color ?: below?.color ?: MaterialTheme.colorScheme.primary,
    markAt: Dp? = null,
    leading: @Composable () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val sizes = ObehyTheme.sizes
    val surface = MaterialTheme.colorScheme.surface
    val edge = MaterialTheme.colorScheme.outline
    Row(
        modifier.height(IntrinsicSize.Min),
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
                    above?.let {
                        stroke(
                            it,
                            Offset(x, 0f),
                            Offset(x, y),
                            width,
                            edge.takeIf { _ ->
                                it.color.blendsInto(surface)
                            },
                        )
                    }
                    below?.let {
                        stroke(
                            it,
                            Offset(x, y),
                            Offset(x, size.height),
                            width,
                            edge.takeIf { _ ->
                                it.color.blendsInto(surface)
                            },
                        )
                    }
                    marker(
                        mark,
                        Offset(x, y),
                        (if (mark == RouteMark.Pass) sizes.routePassMarker else sizes.routeMarker).toPx() / 2,
                        markColor,
                        surface,
                        edge.takeIf {
                            markColor.blendsInto(surface)
                        },
                    )
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
