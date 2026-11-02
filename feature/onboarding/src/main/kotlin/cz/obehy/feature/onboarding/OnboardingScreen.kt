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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.obehy.core.model.AppSettings
import cz.obehy.core.ui.ObehyLogo
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.ChoiceList
import cz.obehy.core.ui.R as UiR

private enum class Step { Welcome, Mode, Provider }

/**
 * First start: a welcome, then the mode, then the routing provider. Each choice is saved as soon
 * as it is picked; the last button finishes onboarding and the app switches to its main screens.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    BackHandler(enabled = step != Step.Welcome) { step = Step.entries[step.ordinal - 1] }

    Scaffold(
        modifier = modifier,
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
                Step.Welcome -> {
                    WelcomeStep()
                }

                Step.Mode -> {
                    ChoiceStep(number = 1, title = R.string.mode_title, subtitle = R.string.mode_subtitle) {
                        ChoiceList(modeChoices.toItems(), settings.mode, viewModel::choose)
                    }
                }

                Step.Provider -> {
                    ChoiceStep(
                        number = 2,
                        title = R.string.provider_title,
                        subtitle = R.string.provider_subtitle,
                    ) {
                        ChoiceList(providerChoices.toItems(), settings.routingProvider, viewModel::choose)
                    }
                }
            }
        }
    }
}

private fun Step.isAnswered(settings: AppSettings) =
    when (this) {
        Step.Welcome -> true
        Step.Mode -> settings.mode != null
        Step.Provider -> settings.routingProvider != null
    }

/** The brand moment: the route loop on a slowly turning Lime cookie, the wordmark and the promise. */
@Composable
private fun WelcomeStep(modifier: Modifier = Modifier) {
    val turning = rememberInfiniteTransition(label = "welcome")
    val turn by turning.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "cookie turn",
    )
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ObehyTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.padding(vertical = ObehyTheme.spacing.xxl).size(ObehyTheme.sizes.hero),
            contentAlignment = Alignment.Center,
        ) {
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
                modifier = Modifier.size(ObehyTheme.sizes.heroSymbol),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        ObehyLogo(height = ObehyTheme.sizes.logoLarge)
        Spacer(Modifier.height(ObehyTheme.spacing.xl))
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ObehyTheme.spacing.m))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            modifier = Modifier.widthIn(max = ObehyTheme.sizes.paragraphWidth),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChoiceStep(
    number: Int,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    modifier: Modifier = Modifier,
    choices: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(number / 2f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "progress")
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ObehyTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = ObehyTheme.sizes.readableWidth)
                .padding(top = ObehyTheme.spacing.l, bottom = ObehyTheme.spacing.xl),
        ) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(ObehyTheme.spacing.xxl))
            Text(
                stringResource(R.string.onboarding_step, number, 2),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(ObehyTheme.spacing.s))
            Text(stringResource(title), style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.height(ObehyTheme.spacing.s))
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ObehyTheme.spacing.xxl))
            choices()
            Spacer(Modifier.height(ObehyTheme.spacing.xl))
            Text(
                stringResource(R.string.onboarding_changeable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Back (from the first question on) and the one big forward button. */
@Composable
private fun BottomActions(
    step: Step,
    canContinue: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = ObehyTheme.spacing.xl, vertical = ObehyTheme.spacing.l),
        horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            step != Step.Welcome,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(height),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_back),
                )
            }
        }
        Button(
            onClick = onNext,
            shapes = ButtonDefaults.shapesFor(height),
            modifier = Modifier.weight(1f).height(height),
            enabled = canContinue,
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            val label =
                when (step) {
                    Step.Welcome -> R.string.onboarding_start
                    Step.Mode -> R.string.onboarding_next
                    Step.Provider -> R.string.onboarding_finish
                }
            Text(stringResource(label), style = ButtonDefaults.textStyleFor(height))
        }
    }
}
