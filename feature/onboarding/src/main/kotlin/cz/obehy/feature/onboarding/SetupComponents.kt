package cz.obehy.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Route
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.ThemeMode
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
        Choice(
            AppMode.Obehy,
            R.string.mode_obehy,
            R.string.mode_obehy_description,
            { ImageVector.vectorResource(UiR.drawable.obehy_symbol) },
        ),
        Choice(
            AppMode.TransitousEnriched,
            R.string.mode_enriched,
            R.string.mode_enriched_description,
            { Icons.Rounded.Public },
            recommended = true,
        ),
        Choice(AppMode.MotisOnly, R.string.mode_motis, R.string.mode_motis_description, { Icons.Rounded.Route }),
    )

internal val themeChoices =
    listOf(
        Choice(ThemeMode.System, R.string.theme_system, R.string.theme_system_description, {
            Icons.Rounded.BrightnessAuto
        }),
        Choice(ThemeMode.Light, R.string.theme_light, R.string.theme_light_description, { Icons.Rounded.LightMode }),
        Choice(ThemeMode.Dark, R.string.theme_dark, R.string.theme_dark_description, { Icons.Rounded.DarkMode }),
    )

@Composable
internal fun <T> List<Choice<T>>.toItems(): List<ChoiceItem<T>> =
    map { choice ->
        ChoiceItem(
            value = choice.value,
            title = stringResource(choice.title),
            description = stringResource(choice.description),
            icon = choice.icon(),
            overline = if (choice.recommended) stringResource(R.string.mode_recommended) else null,
        )
    }
