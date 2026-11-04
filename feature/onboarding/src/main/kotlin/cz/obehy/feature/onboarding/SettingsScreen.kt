package cz.obehy.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.AppSettings
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
private fun SettingsDestination(
    viewModel: SetupViewModel = hiltViewModel(),
    server: CustomServerViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val wizard by server.wizard.collectAsStateWithLifecycle()
    SettingsScreen(
        settings,
        onMode = viewModel::choose,
        onTheme = viewModel::choose,
        onClearHistory = viewModel::clearHistory,
        onOwnServer = server::open,
        onResetServer = server::reset,
    )
    wizard?.let {
        CustomServerWizard(
            it,
            onType = server::type,
            onCheck = server::check,
            onUse = server::use,
            onEdit = server::edit,
            onRetry = server::retry,
            onClose = server::close,
        )
    }
}

/** The onboarding choices, changeable at any time; they apply immediately. */
@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    onMode: (AppMode) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onClearHistory: () -> Unit,
    onOwnServer: () -> Unit,
    onResetServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMotisOnly = settings.mode == AppMode.MotisOnly
    val ownServer = settings.customMotisUrl.takeIf { isMotisOnly }
    ObehyScreen(title = stringResource(R.string.settings_title), modifier = modifier) {
        Section(R.string.settings_mode) {
            ownServer?.let { OwnServerNote(it, onResetServer) }
            ChoiceList(modeChoices.toItems(), settings.mode, onMode, enabled = ownServer == null)
            if (isMotisOnly && ownServer == null) {
                TextButton(onClick = onOwnServer) {
                    Icon(Icons.Rounded.Dns, contentDescription = null)
                    Spacer(Modifier.width(ObehyTheme.spacing.s))
                    Text(stringResource(R.string.server_entry))
                }
            }
        }
        Section(R.string.settings_theme) { ChoiceList(themeChoices.toItems(), settings.theme, onTheme) }
        Section(R.string.settings_history) { HistoryControls(onClearHistory) }
    }
}

/** Why the modes can't be picked, and the way out: above them, so it's read first. */
@Composable
private fun OwnServerNote(
    url: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            Modifier.padding(ObehyTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Dns, contentDescription = null)
                Text(stringResource(R.string.server_in_use), style = MaterialTheme.typography.titleMediumEmphasized)
            }
            Text(url.removeSuffix("/"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.server_in_use_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onReset) { Text(stringResource(R.string.server_reset)) }
        }
    }
}

/** What past searches are for, and the way to wipe them, after a confirmation. */
@Composable
private fun HistoryControls(
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var cleared by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.padding(ObehyTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.l),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(ObehyTheme.sizes.iconBadge)
                        .clip(MaterialShapes.Circle.toShape())
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (cleared) Icons.Rounded.Check else Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(
                    stringResource(if (cleared) R.string.history_cleared else R.string.history_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(!cleared) {
                OutlinedButton(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(ObehyTheme.spacing.s))
                    Text(stringResource(R.string.history_clear))
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    cleared = true
                    confirming = false
                }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.history_clear_cancel)) }
            },
        )
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
