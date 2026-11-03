package cz.obehy.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

// Container transforms between screens: a thing on one screen (a result card) grows into the
// next screen (the journey) and shrinks back, also while the back gesture previews it.

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

private val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Wraps the app's navigation so screens inside can share elements ([SharedBounds]). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedMotion(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) { content() }
    }
}

/** Marks [content] as one navigation destination's screen, the scope its shared elements animate in. */
@Composable
fun AnimatedDestination(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNavAnimatedScope provides scope) { content() }
}

/**
 * [content] as the same thing as every other [SharedBounds] with this [key] on another screen:
 * moving between the screens morphs one into the other. Without [SharedMotion] around, it is a
 * plain box.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedBounds(
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    val bounds =
        if (shared != null && animated != null) {
            with(shared) { Modifier.sharedBounds(rememberSharedContentState(key), animated) }
        } else {
            Modifier
        }
    Box(modifier.then(bounds)) { content() }
}
