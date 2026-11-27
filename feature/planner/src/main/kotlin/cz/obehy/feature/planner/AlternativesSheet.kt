package cz.obehy.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import cz.obehy.core.model.Itinerary
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.PunctualityTone
import cz.obehy.core.ui.components.StateMessage
import cz.obehy.core.ui.components.clock
import cz.obehy.core.ui.components.colors
import java.time.Duration
import java.time.Instant

/**
 * Other ways on, over the journey that isn't working out: where they leave from, when the journey
 * would have got there, then each way as a result, marked with how much later or
 * sooner it gets there than that. Taking one makes it the journey; dismissing keeps the old one.
 */
@Composable
internal fun AlternativesSheet(
    alternatives: Alternatives,
    onTake: (Itinerary) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ObehyTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.l)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(stringResource(R.string.alternatives_title), style = MaterialTheme.typography.titleLargeEmphasized)
                (alternatives.from ?: alternatives.query.from?.label())?.let { from ->
                    Text(stringResource(R.string.alternatives_from, from), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    stringResource(R.string.alternatives_yours, alternatives.arriving.clock()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val found = alternatives.found
            when {
                alternatives.failed -> {
                    StateMessage(
                        Icons.Rounded.CloudOff,
                        stringResource(R.string.alternatives_failed),
                        action = stringResource(R.string.retry),
                        onAction = onRetry,
                    )
                }

                found == null -> {
                    Box(Modifier.fillMaxWidth().padding(spacing.l), Alignment.Center) { LoadingIndicator() }
                }

                found.itineraries.isEmpty() -> {
                    StateMessage(Icons.Rounded.SearchOff, stringResource(R.string.alternatives_none))
                }

                else -> {
                    val measurer = rememberTextMeasurer()
                    val ways = found.itineraries
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        ways.forEachIndexed { index, way ->
                            ResultCard(
                                way,
                                index,
                                ways.size,
                                selected = false,
                                measurer = measurer,
                                onClick = { onTake(way) },
                                badge = { ArrivalShift(alternatives.arriving, way.end.expected) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * How much later or sooner a way gets there than the journey would have: the one figure the ways
 * are compared by, on a tone like a delay's (late is amber or orange, sooner green), and spelled
 * out, so the colour never has to say it alone.
 */
@Composable
private fun ArrivalShift(
    was: Instant,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val minutes = Duration.between(was, now).toMinutes()
    val (text, spoken) =
        when {
            minutes > 0 -> {
                stringResource(R.string.alternatives_later, minutes) to
                    pluralStringResource(R.plurals.alternatives_later_spoken, minutes.toInt(), minutes)
            }

            minutes < 0 -> {
                stringResource(R.string.alternatives_sooner, -minutes) to
                    pluralStringResource(R.plurals.alternatives_sooner_spoken, (-minutes).toInt(), -minutes)
            }

            else -> {
                stringResource(R.string.alternatives_same) to stringResource(R.string.alternatives_same_spoken)
            }
        }
    val tone = PunctualityTone.of(minutes).colors
    Surface(
        modifier.semantics { contentDescription = spoken },
        shape = MaterialTheme.shapes.small,
        color = tone.fill,
        contentColor = tone.text,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = ObehyTheme.spacing.s, vertical = ObehyTheme.spacing.xs / 2),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
