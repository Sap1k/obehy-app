package cz.obehy.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import cz.obehy.core.ui.ObehyTheme

/**
 * A row of chips that scrolls sideways. It runs to the screen's edges (out of the standard side
 * padding), so chips visibly continue past the edge, and fades out wherever more chips wait.
 */
@Composable
fun ScrollingChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val gutter = ObehyTheme.spacing.l
    Row(
        modifier
            .layout { measurable, constraints ->
                // Widen by the screen's side padding on both sides, then sit back over it.
                val extra = gutter.roundToPx() * 2
                val wide = constraints.maxWidth + extra
                val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
            }.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val fade = 32.dp.toPx()
                if (scroll.canScrollBackward) {
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = fade),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (scroll.canScrollForward) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Black, Color.Transparent),
                            startX = size.width - fade,
                            endX = size.width,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }.horizontalScroll(scroll)
            .padding(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
        content = content,
    )
}

/** A filter chip's colours: Lime when it is on, like anything else that is selected. */
@Composable
fun limeFilterChipColors(): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
