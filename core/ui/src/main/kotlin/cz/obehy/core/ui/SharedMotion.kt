package cz.obehy.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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

/** The [SharedBounds] key of a ride on a journey and the trip's content it grows into. */
fun rideMorphKey(motisTripId: String): String = "ride:$motisTripId"

/** How long a shared element takes to morph, and a screen to leave with it. */
const val BACK_MS: Int = 350

/**
 * The curve of a morph. The back gesture scrubs through it by the finger, so the curve shapes the
 * drag: a spring would do nearly all of it at the start and linear feels dead, while this eases
 * in and out and stays with the finger.
 */
val BackEasing: Easing = FastOutSlowInEasing

/** Leaves a thing fully there for as long as a morph over it takes, and only then gone. */
val HoldDuringMorph: ExitTransition = fadeOut(tween(BACK_MS), targetAlpha = 1f)

/** Marks [content] as one navigation destination's screen, the scope its shared elements animate in. */
@Composable
fun AnimatedDestination(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNavAnimatedScope provides scope) { content() }
}

/** How far this destination has come in, from 0 to 1, following the back gesture too; 1 outside navigation. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun rememberEntrance(): State<Float> {
    val animated = LocalNavAnimatedScope.current ?: return remember { mutableFloatStateOf(1f) }
    return animated.transition.animateFloat(
        transitionSpec = { tween(BACK_MS, easing = BackEasing) },
        label = "entrance",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
}

/**
 * [content] as the same thing as every other [SharedBounds] with this [key] on another screen:
 * moving between the screens morphs one into the other. With [holdOnExit] it stays as it is when
 * it leaves instead of fading, for something else growing over it. With [inPlace] it morphs among
 * its screen's other content, behind whatever lies on top of it there, rather than above everything.
 * Without [SharedMotion] around, it is a plain box.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedBounds(
    key: Any,
    modifier: Modifier = Modifier,
    holdOnExit: Boolean = false,
    inPlace: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    val bounds =
        if (shared != null && animated != null) {
            with(shared) {
                // Rounded the whole way, so a card doesn't square off as it grows or shrinks.
                Modifier.sharedBounds(
                    rememberSharedContentState(key),
                    animated,
                    // Not a spring: the back gesture scrubs through this by the finger, and a spring
                    // would do nearly all of it in the first bit of the drag.
                    boundsTransform = BoundsTransform { _, _ -> tween(BACK_MS, easing = BackEasing) },
                    enter = fadeIn(tween(BACK_MS, easing = BackEasing)),
                    exit =
                        if (holdOnExit) {
                            HoldDuringMorph
                        } else {
                            fadeOut(tween(BACK_MS, easing = BackEasing))
                        },
                    renderInOverlayDuringTransition = !inPlace,
                    clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.large),
                )
            }
        } else {
            Modifier
        }
    Box(modifier.then(bounds)) { content() }
}
