package cz.obehy.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** How far a card sinks in while it is pressed. */
private const val PRESSED_SCALE = 0.97f

/** The scale of a card pressed through [source], for [scaledBy]. */
@Composable
fun pressScale(source: InteractionSource): State<Float> {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
}

/** Draws this at [scale], so a press never lays anything out again. */
fun Modifier.scaledBy(scale: State<Float>): Modifier =
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
