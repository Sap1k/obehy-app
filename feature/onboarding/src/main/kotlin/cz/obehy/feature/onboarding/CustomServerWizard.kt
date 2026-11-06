package cz.obehy.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import cz.obehy.core.data.ServerCheckStep
import cz.obehy.core.ui.ObehyTheme

/**
 * Sets up the user's own server: its address, then a look at whether it works, and only then the
 * choice to use it. Dismissing it at any point leaves everything as it was.
 */
@Composable
internal fun CustomServerWizard(
    wizard: ServerWizard,
    onType: (String) -> Unit,
    onCheck: () -> Unit,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AnimatedContent(targetState = wizard.stage(), label = "server wizard") { stage ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ObehyTheme.spacing.xl)
                    .padding(bottom = ObehyTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.l),
            ) {
                Text(stringResource(stage.title), style = MaterialTheme.typography.headlineSmallEmphasized)
                when (wizard) {
                    is ServerWizard.Entering -> {
                        AddressStage(wizard, onType, onCheck)
                    }

                    is ServerWizard.Checking -> {
                        StepList(wizard.step, failed = false)
                        TextButton(
                            onClick = onClose,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.server_cancel)) }
                    }

                    is ServerWizard.Passed -> {
                        StepList(current = null, failed = false)
                        Text(stringResource(R.string.server_passed_body, wizard.version))
                        Button(onClick = onUse, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.server_use))
                        }
                        TextButton(
                            onClick = onEdit,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.server_edit)) }
                    }

                    is ServerWizard.Failed -> {
                        StepList(wizard.step, failed = true)
                        Text(stringResource(wizard.step.failure))
                        Button(
                            onClick = onRetry,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.server_retry))
                        }
                        TextButton(
                            onClick = onEdit,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.server_edit)) }
                    }
                }
            }
        }
    }
}

private enum class Stage(
    @StringRes val title: Int,
) {
    Address(R.string.server_title),
    Checking(R.string.server_checking),
    Passed(R.string.server_passed),
    Failed(R.string.server_failed),
}

/** Only a change of stage animates, not each step of the check. */
private fun ServerWizard.stage() =
    when (this) {
        is ServerWizard.Entering -> Stage.Address
        is ServerWizard.Checking -> Stage.Checking
        is ServerWizard.Passed -> Stage.Passed
        is ServerWizard.Failed -> Stage.Failed
    }

@Composable
private fun AddressStage(
    state: ServerWizard.Entering,
    onType: (String) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.l)) {
        Text(
            stringResource(R.string.server_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.address,
            onValueChange = onType,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.server_address)) },
            supportingText =
                if (state.invalid) {
                    { Text(stringResource(R.string.server_address_invalid)) }
                } else {
                    null
                },
            isError = state.invalid,
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { onCheck() }),
        )
        Button(
            onClick = onCheck,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
            enabled = state.address.isNotBlank(),
        ) {
            Text(stringResource(R.string.server_check))
        }
    }
}

/** Every step, done up to [current]; with no [current], all are done. */
@Composable
private fun StepList(
    current: ServerCheckStep?,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s)) {
        ServerCheckStep.entries.forEach { step ->
            val done = current == null || step < current
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(ObehyTheme.sizes.icon), contentAlignment = Alignment.Center) {
                    when {
                        done -> {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.server_step_done),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        step == current && failed -> {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = stringResource(R.string.server_step_failed),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }

                        step == current -> {
                            LoadingIndicator()
                        }
                    }
                }
                Text(
                    stringResource(step.label),
                    color =
                        if (done || step == current) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

private val ServerCheckStep.label
    @StringRes get() =
        when (this) {
            ServerCheckStep.Server -> R.string.server_step_server
            ServerCheckStep.Places -> R.string.server_step_places
            ServerCheckStep.Stops -> R.string.server_step_stops
            ServerCheckStep.Journeys -> R.string.server_step_journeys
        }

private val ServerCheckStep.failure
    @StringRes get() =
        when (this) {
            ServerCheckStep.Server -> R.string.server_failed_server
            ServerCheckStep.Places -> R.string.server_failed_places
            ServerCheckStep.Stops -> R.string.server_failed_stops
            ServerCheckStep.Journeys -> R.string.server_failed_journeys
        }
