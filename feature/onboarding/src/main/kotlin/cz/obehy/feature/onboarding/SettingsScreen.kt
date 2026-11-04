package cz.obehy.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.AppSettings
import cz.obehy.core.model.RoutingProvider
import cz.obehy.core.model.ThemeMode
import cz.obehy.core.ui.ObehyScreen
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.ChoiceList
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsScreen() {
    composable<SettingsRoute> { SettingsDestination() }
}

@Composable
private fun SettingsDestination(viewModel: SetupViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(settings, onMode = viewModel::choose, onProvider = viewModel::choose, onTheme = viewModel::choose)
}

/** The onboarding choices, changeable at any time; they apply immediately. */
@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    onMode: (AppMode) -> Unit,
    onProvider: (RoutingProvider) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ObehyScreen(title = stringResource(R.string.settings_title), modifier = modifier) {
        Section(R.string.settings_mode) { ChoiceList(modeChoices.toItems(), settings.mode, onMode) }
        Section(
            R.string.settings_provider,
        ) { ChoiceList(providerChoices.toItems(), settings.routingProvider, onProvider) }
        Section(R.string.settings_theme) { ChoiceList(themeChoices.toItems(), settings.theme, onTheme) }
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m)) {
        Text(
            stringResource(title),
            modifier = Modifier.padding(horizontal = ObehyTheme.spacing.s),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}
