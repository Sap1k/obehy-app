package cz.obehy.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Route
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.RoutingProvider
import cz.obehy.core.ui.components.ChoiceItem
import cz.obehy.core.ui.R as UiR

/** A setup option as resources; [toItems] resolves it for the shared ChoiceList. */
internal data class Choice<T>(
    val value: T,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: @Composable () -> ImageVector,
    val recommended: Boolean = false,
)

internal val modeChoices =
    listOf(
        Choice(AppMode.Enhanced, R.string.mode_enhanced, R.string.mode_enhanced_description, { Icons.Rounded.Map }),
        Choice(
            AppMode.Transitous,
            R.string.mode_transitous,
            R.string.mode_transitous_description,
            { Icons.Rounded.Route },
        ),
    )

internal val providerChoices =
    listOf(
        Choice(
            RoutingProvider.Transitous,
            R.string.provider_transitous,
            R.string.provider_transitous_description,
            { Icons.Rounded.Public },
            recommended = true,
        ),
        Choice(
            RoutingProvider.Obehy,
            R.string.provider_obehy,
            R.string.provider_obehy_description,
            { ImageVector.vectorResource(UiR.drawable.obehy_symbol) },
        ),
    )

@Composable
internal fun <T> List<Choice<T>>.toItems(): List<ChoiceItem<T>> =
    map { choice ->
        ChoiceItem(
            value = choice.value,
            title = stringResource(choice.title),
            description = stringResource(choice.description),
            icon = choice.icon(),
            overline = if (choice.recommended) stringResource(R.string.provider_recommended) else null,
        )
    }
