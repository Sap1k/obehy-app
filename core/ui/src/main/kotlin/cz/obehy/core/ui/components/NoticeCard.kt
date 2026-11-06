package cz.obehy.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R

/** "passed", for reading out a row of a timeline that has gone by. */
@Composable
fun passedLabel(): String = stringResource(R.string.passed)

/** How many lines of a notice's title show while it's folded. */
private const val FOLDED_TITLE_LINES = 2

/**
 * Something the rider should know about: a disruption, or a journey that isn't working out. [severe]
 * ones stand out in the error colours. It shows only its title at first, and a tap folds out the
 * rest: the [text], and whatever of the title didn't fit. With [action], a button says what to do
 * about it, always in view.
 */
@Composable
fun NoticeCard(
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    severe: Boolean = true,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val background = if (severe) colors.errorContainer else colors.secondaryContainer
    val content = if (severe) colors.onErrorContainer else colors.onSecondaryContainer
    val motion = MaterialTheme.motionScheme
    var open by rememberSaveable(title) { mutableStateOf(false) }
    var cutShort by remember(title) { mutableStateOf(false) }
    val folds = text != null || cutShort
    val chevron by animateFloatAsState(if (open) 180f else 0f, motion.defaultSpatialSpec())
    val label = stringResource(if (open) R.string.notice_less else R.string.notice_more)
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(background)
            .then(if (folds) Modifier.clickable(onClickLabel = label) { open = !open } else Modifier)
            .padding(ObehyTheme.spacing.m)
            .animateContentSize(motion.defaultSpatialSpec()),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
    ) {
        Icon(
            Icons.Rounded.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(ObehyTheme.sizes.iconSmall),
            tint = content,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                maxLines = if (open) Int.MAX_VALUE else FOLDED_TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (it.hasVisualOverflow) cutShort = true },
            )
            if (open) text?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = content) }
            action?.let {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = background),
                ) { Text(it) }
            }
        }
        if (folds) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(ObehyTheme.sizes.iconSmall).rotate(chevron),
                tint = content,
            )
        }
    }
}
