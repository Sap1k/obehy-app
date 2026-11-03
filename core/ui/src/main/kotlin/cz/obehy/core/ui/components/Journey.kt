package cz.obehy.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.obehy.core.model.Leg
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R
import cz.obehy.core.ui.icon
import cz.obehy.core.ui.label

/** A route's colours as the feed gives them, or the theme's when it gives none. */
@Immutable
data class RouteColors(
    val container: Color,
    val content: Color,
    /** An edge for a route colour that would vanish into the background (dark navy on Night). */
    val outline: Color? = null,
)

/**
 * The colours of a route given as `RRGGBB` [hex] (and [textHex]). Route colours are data: shown
 * exactly as they come, never recoloured to the brand. Without a text colour, the lettering is
 * dark or white, whichever reads better on the route colour.
 */
@Composable
fun routeColors(
    hex: String?,
    textHex: String? = null,
): RouteColors {
    val container =
        parseHex(hex)
            ?: return RouteColors(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
            )
    val content = parseHex(textHex) ?: if (container.luminance() > 0.5f) DarkLettering else Color.White
    val outline = MaterialTheme.colorScheme.outline.takeIf { container.blendsInto(MaterialTheme.colorScheme.surface) }
    return RouteColors(container, content, outline)
}

/** Whether this colour would all but disappear on [background]; such route colours get an edge. */
internal fun Color.blendsInto(background: Color): Boolean {
    val a = luminance() + 0.05f
    val b = background.luminance() + 0.05f
    return maxOf(a, b) / minOf(a, b) < 1.6f
}

private fun Modifier.edge(
    color: Color?,
    shape: Shape,
): Modifier = if (color == null) this else border(1.dp, color, shape)

/** The colour the route line is drawn in for a ride on [hex]. */
@Composable
fun routeLineColor(hex: String?): Color = parseHex(hex) ?: MaterialTheme.colorScheme.primary

private val DarkLettering = Color(0xFF102B2D)

private fun parseHex(hex: String?): Color? =
    hex
        ?.removePrefix("#")
        ?.takeIf { it.length == 6 }
        ?.toLongOrNull(16)
        ?.let { Color(0xFF000000 or it) }

/** A line's name on its colour, with the mode's icon so the colour never has to say it alone. */
@Composable
fun LineBadge(
    ride: Leg.Transit,
    modifier: Modifier = Modifier,
) {
    val colors = routeColors(ride.color, ride.textColor)
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(colors.container)
            .edge(colors.outline, MaterialTheme.shapes.small)
            .padding(horizontal = ObehyTheme.spacing.s, vertical = ObehyTheme.spacing.xs / 2),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            ride.mode.icon,
            contentDescription = stringResource(ride.mode.label),
            modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
            tint = colors.content,
        )
        Text(ride.name, color = colors.content, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * A journey as one bar, each leg exactly as long as it takes: rides in their route colours, walks
 * dotted. Each ride shows the longest label that fits its part of the bar: the mode icon with
 * the full name (`S49 (Os 25647)`), the full name, the line (`S49`), the icon alone, or nothing.
 */
@Composable
fun JourneyBar(
    legs: List<Leg>,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val sizes = ObehyTheme.sizes
    val padding = ObehyTheme.spacing.xs
    BoxWithConstraints(modifier.fillMaxWidth().height(sizes.journeyBar)) {
        val widths =
            segmentWidths(
                legs.map { it.duration.seconds.coerceAtLeast(1) },
                maxWidth - SegmentGap * (legs.size - 1),
            )
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
            legs.zip(widths).forEach { (leg, width) ->
                when (leg) {
                    is Leg.Walk -> {
                        WalkSegment(Modifier.width(width).fillMaxHeight())
                    }

                    is Leg.Transit -> {
                        fun textWidth(text: String) =
                            with(density) {
                                measurer
                                    .measure(text, style)
                                    .size.width
                                    .toDp()
                            }
                        val room = width - padding * 2
                        val withIcon = sizes.iconSmall + padding
                        val line = leg.line
                        val label =
                            when {
                                textWidth(leg.name) + withIcon <= room -> SegmentLabel(leg.name, icon = true)
                                textWidth(leg.name) <= room -> SegmentLabel(leg.name, icon = false)
                                line != null && textWidth(line) <= room -> SegmentLabel(line, icon = false)
                                sizes.iconSmall <= room -> SegmentLabel(null, icon = true)
                                else -> SegmentLabel(null, icon = false)
                            }
                        RideSegment(leg, label, style, Modifier.width(width).fillMaxHeight())
                    }
                }
            }
        }
    }
}

private val SegmentGap = 2.dp
private val MinSegment = 3.dp

private data class SegmentLabel(
    val text: String?,
    val icon: Boolean,
)

/** Splits [available] by [weights], giving every part at least [MinSegment] and taking it from the larger ones. */
private fun segmentWidths(
    weights: List<Long>,
    available: Dp,
): List<Dp> {
    val total = weights.sum().toFloat()
    val raw = weights.map { available * (it / total) }
    val short = raw.filter { it < MinSegment }
    if (short.isEmpty()) return raw
    val missing = short.fold(0.dp) { sum, it -> sum + (MinSegment - it) }
    val large = raw.filter { it >= MinSegment }.fold(0.dp) { sum, it -> sum + it }
    return raw.map { if (it < MinSegment) MinSegment else it - missing * (it / large) }
}

@Composable
private fun RideSegment(
    ride: Leg.Transit,
    label: SegmentLabel,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = routeColors(ride.color, ride.textColor)
    val description = stringResource(ride.mode.label) + " " + ride.name
    Row(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.container)
            .edge(colors.outline, MaterialTheme.shapes.extraSmall)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = ObehyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.icon) {
            Icon(
                ride.mode.icon,
                contentDescription = null,
                Modifier.size(ObehyTheme.sizes.iconSmall),
                tint = colors.content,
            )
        }
        label.text?.let {
            Text(
                it,
                color = colors.content,
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun WalkSegment(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val stroke = 3.dp.toPx()
        drawLine(
            color,
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke, stroke * 1.5f)),
        )
    }
}

/**
 * A delay of [minutes]: "+6 min" in the error colour with a clock, so the colour is never the
 * only sign. Nothing for trips on time or early.
 */
@Composable
fun DelayLabel(
    minutes: Long?,
    modifier: Modifier = Modifier,
) {
    if (minutes == null || minutes < 1) return
    val spoken = stringResource(R.string.delay_late, minutes)
    Row(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Schedule,
            null,
            Modifier.size(ObehyTheme.sizes.iconSmall),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(R.string.delay_short, minutes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** A platform or stand; outlined, and in the error colour with a spoken note when it changed. */
@Composable
fun PlatformLabel(
    track: String,
    changed: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (changed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val spoken =
        if (changed) {
            stringResource(
                R.string.platform_changed,
                track,
            )
        } else {
            stringResource(R.string.platform, track)
        }
    Text(
        stringResource(R.string.platform, track),
        modifier
            .semantics { contentDescription = spoken }
            .border(1.dp, color, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = ObehyTheme.spacing.xs),
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** Marks a time that comes from where the vehicle is now, not the timetable: an icon, spoken as "live". */
@Composable
fun LiveMark(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.Sensors,
        contentDescription = stringResource(R.string.live),
        modifier = modifier.size(ObehyTheme.sizes.iconSmall),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** The low-floor mark: an icon, spoken as "Low-floor". */
@Composable
fun LowFloorIcon(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.Accessible,
        contentDescription = stringResource(R.string.low_floor),
        modifier = modifier.size(ObehyTheme.sizes.iconSmall),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
