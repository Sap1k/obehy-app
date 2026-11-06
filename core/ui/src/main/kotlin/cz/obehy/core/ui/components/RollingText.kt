package cz.obehy.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** [text] that rolls up to its next value, so a countdown ticking on gets noticed. */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        text,
        modifier,
        transitionSpec = {
            val entering = slideInVertically(motion.fastSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec())
            val leaving = slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.defaultEffectsSpec())
            entering.togetherWith(leaving).using(SizeTransform(clip = true))
        },
        contentAlignment = Alignment.CenterStart,
    ) { Text(it, style = style, color = color) }
}
