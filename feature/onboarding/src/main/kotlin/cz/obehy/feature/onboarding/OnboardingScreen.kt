package cz.obehy.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * First start: the mode and the routing provider. Both are saved as soon as they are picked;
 * [onDone] runs when the user confirms with both chosen.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Phones use the full width; tablets keep a readable column.
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceGroup(R.string.mode_title, modeChoices, settings.mode, viewModel::choose)
            ChoiceGroup(R.string.provider_title, providerChoices, settings.routingProvider, viewModel::choose)
            Text(
                stringResource(R.string.onboarding_changeable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onDone,
                enabled = settings.isOnboarded,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboarding_continue)) }
        }
    }
}
