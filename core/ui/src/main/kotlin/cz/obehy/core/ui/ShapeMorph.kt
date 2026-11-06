package cz.obehy.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * [content] on a [color]ed [shape] (one of the `MaterialShapes`), which morphs when [shape] changes
 * and fades to a new [color]. Only the layer changes as it does, so no frame recomposes or lays out
 * anything. Shapes here mark a change of state; don't give any one shape a meaning of its own.
 */
@Composable
fun MorphingBadge(
    shape: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val spec = motion.defaultSpatialSpec<Float>()
    val fill by animateColorAsState(color, motion.fastEffectsSpec())
    var morph by remember { mutableStateOf(Morph(shape, shape)) }
    var settled by remember { mutableStateOf(shape) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(shape) {
        if (shape === settled) return@LaunchedEffect
        morph = Morph(settled, shape)
        settled = shape
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
    Box(
        modifier
            .graphicsLayer {
                clip = true
                // The spring goes past the end, but a shape has nowhere to go past it.
                this.shape = MorphedShape(morph, progress.value.coerceIn(0f, 1f))
            }.drawBehind { drawRect(fill) },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A value, so the layer sees a new shape on every step of the morph. */
private data class MorphedShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}
