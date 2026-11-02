package cz.obehy.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.obehy.core.model.AppSettings
import cz.obehy.core.ui.ObehyLogo
import cz.obehy.core.ui.R as UiR

private enum class Step { Welcome, Mode, Provider }

/**
 * First start: a welcome, then the mode, then the routing provider. Each choice is saved as soon
 * as it is picked; the last button finishes onboarding and the app switches to its main screens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(viewModel: SetupViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    BackHandler(enabled = step != Step.Welcome) { step = Step.entries[step.ordinal - 1] }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomActions(
                step = step,
                canContinue = step.isAnswered(settings),
                onBack = { step = Step.entries[step.ordinal - 1] },
                onNext = {
                    if (step == Step.Provider) viewModel.finishOnboarding() else step = Step.entries[step.ordinal + 1]
                },
            )
        },
    ) { padding ->
        val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        AnimatedContent(
            targetState = step,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(slide) { direction * it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(slide) { -direction * it / 3 } + fadeOut())
            },
            label = "onboarding step",
        ) { current ->
            when (current) {
                Step.Welcome -> WelcomeStep()
                Step.Mode -> ChoiceStep(
                    number = 1,
                    title = R.string.mode_title,
                    subtitle = R.string.mode_subtitle,
                ) { ChoiceList(modeChoices, settings.mode, viewModel::choose) }
                Step.Provider -> ChoiceStep(
                    number = 2,
                    title = R.string.provider_title,
                    subtitle = R.string.provider_subtitle,
                ) { ChoiceList(providerChoices, settings.routingProvider, viewModel::choose) }
            }
        }
    }
}

private fun Step.isAnswered(settings: AppSettings) = when (this) {
    Step.Welcome -> true
    Step.Mode -> settings.mode != null
    Step.Provider -> settings.routingProvider != null
}

/** The brand moment: the route loop on a slowly turning Lime cookie, the wordmark and the promise. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeStep() {
    val turning = rememberInfiniteTransition(label = "welcome")
    val turn by turning.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "cookie turn",
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.padding(vertical = 32.dp).size(220.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .rotate(turn)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Icon(
                painterResource(UiR.drawable.obehy_symbol),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        ObehyLogo(height = 52.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            modifier = Modifier.widthIn(max = 420.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceStep(
    number: Int,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    choices: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(number / 2f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "progress")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Phones use the full width; tablets keep a readable column.
        Column(Modifier.widthIn(max = 560.dp).padding(top = 16.dp, bottom = 24.dp)) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.onboarding_step, number, 2),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            choices()
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.onboarding_changeable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Back (from the first question on) and the one big forward button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomActions(step: Step, canContinue: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    val height = ButtonDefaults.MediumContainerHeight
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(step != Step.Welcome, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(height),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
            }
        }
        Button(
            onClick = onNext,
            shapes = ButtonDefaults.shapesFor(height),
            modifier = Modifier.weight(1f).height(height),
            enabled = canContinue,
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            val label = when (step) {
                Step.Welcome -> R.string.onboarding_start
                Step.Mode -> R.string.onboarding_next
                Step.Provider -> R.string.onboarding_finish
            }
            Text(stringResource(label), style = ButtonDefaults.textStyleFor(height))
        }
    }
}
