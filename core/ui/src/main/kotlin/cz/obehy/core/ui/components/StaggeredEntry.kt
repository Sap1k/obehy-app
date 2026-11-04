package cz.obehy.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import cz.obehy.core.ui.ObehyTheme
import kotlinx.coroutines.delay

/** How long each item of a list waits after the one before it. */
private const val STEP_MS = 40L

/** Items further down than this all arrive together. */
private const val MAX_STEPS = 8

/**
 * [content] rising and fading in as the [order]th item of a list arriving, when [enabled]. With
 * [enabled] false it is simply there. It only arrives once, so later changes don't replay it.
 */
@Composable
fun StaggeredEntry(
    order: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Rows scrolling into view land here with nothing to animate, and they must stay cheap.
    val shown = remember { if (enabled) Animatable(0f) else null }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val rise = with(LocalDensity.current) { ObehyTheme.spacing.xl.toPx() }
    if (shown != null) {
        LaunchedEffect(Unit) {
            delay(order.coerceAtMost(MAX_STEPS) * STEP_MS)
            shown.animateTo(1f, spec)
        }
    }
    Box(
        if (shown == null) {
            modifier
        } else {
            modifier.graphicsLayer {
                alpha = shown.value.coerceIn(0f, 1f)
                translationY = (1 - shown.value) * rise
            }
        },
    ) { content() }
}
