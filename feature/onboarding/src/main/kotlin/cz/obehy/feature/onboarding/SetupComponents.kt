package cz.obehy.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.RoutingProvider

internal data class Choice<T>(val value: T, @StringRes val title: Int, @StringRes val description: Int)

internal val modeChoices = listOf(
    Choice(AppMode.Enhanced, R.string.mode_enhanced, R.string.mode_enhanced_description),
    Choice(AppMode.Transitous, R.string.mode_transitous, R.string.mode_transitous_description),
)

internal val providerChoices = listOf(
    Choice(RoutingProvider.Transitous, R.string.provider_transitous, R.string.provider_transitous_description),
    Choice(RoutingProvider.Obehy, R.string.provider_obehy, R.string.provider_obehy_description),
)

/** One choice of several, as cards with a radio button each. */
@Composable
internal fun <T> ChoiceGroup(
    @StringRes title: Int,
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        choices.forEach { choice ->
            val isSelected = choice.value == selected
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(isSelected, role = Role.RadioButton) { onSelect(choice.value) },
                border = if (isSelected) {
                    CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
                } else {
                    CardDefaults.outlinedCardBorder()
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    RadioButton(selected = isSelected, onClick = null)
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(choice.title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(choice.description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
