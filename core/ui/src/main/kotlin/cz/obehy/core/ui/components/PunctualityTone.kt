package cz.obehy.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import cz.obehy.core.model.Punctuality
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.R

/**
 * How late something is, as a colour: the web's tones (`web/src/theme/tokens.css`, `--ob-*-text`
 * and `--ob-*-fill`), so a delay reads the same in the app and on the web. The band comes from the
 * API (or the same thresholds for MOTIS times); here it's only coloured.
 */
enum class PunctualityTone {
    Early,
    OnTime,
    Late,
    VeryLate,
    Cancelled,
    Unknown,
    ;

    companion object {
        /** The tone of a delay in shown minutes; [Unknown] without one, [Cancelled] when it doesn't run. */
        fun of(
            delayMinutes: Long?,
            cancelled: Boolean = false,
        ): PunctualityTone =
            when {
                cancelled -> {
                    Cancelled
                }

                delayMinutes == null -> {
                    Unknown
                }

                else -> {
                    when (Punctuality.of(delayMinutes)) {
                        Punctuality.Early -> Early
                        Punctuality.OnTime -> OnTime
                        Punctuality.Late -> Late
                        Punctuality.VeryLate -> VeryLate
                    }
                }
            }
    }
}

/** A tone's lettering and the tint it sits on. */
@Immutable
data class ToneColors(
    val text: Color,
    val fill: Color,
)

/** This tone's colours in the current theme. */
val PunctualityTone.colors: ToneColors
    @Composable @ReadOnlyComposable
    get() = colorsIn(dark = MaterialTheme.colorScheme.surface.luminance() < HALF_LIGHT)

/** This tone's colours in a light or [dark] theme, outside composition. */
fun PunctualityTone.colorsIn(dark: Boolean): ToneColors = if (dark) DARK.getValue(this) else LIGHT.getValue(this)

/** A tone's lettering on the page, for a time: early in blue, on time in green, late in amber. */
val PunctualityTone.text: Color
    @Composable @ReadOnlyComposable
    get() = colors.text

private val LIGHT =
    mapOf(
        PunctualityTone.OnTime to ToneColors(Color(0xFF256342), Color(0xFFE6F3E9)),
        PunctualityTone.Late to ToneColors(Color(0xFF875200), Color(0xFFFFF0CF)),
        PunctualityTone.VeryLate to ToneColors(Color(0xFF9A3412), Color(0xFFFFEDD5)),
        PunctualityTone.Early to ToneColors(Color(0xFF1E40AF), Color(0xFFDBEAFE)),
        PunctualityTone.Cancelled to ToneColors(Color(0xFFAF3038), Color(0xFFFCE8E8)),
        PunctualityTone.Unknown to ToneColors(Color(0xFF526760), Color(0xFFEAF0E9)),
    )

private val DARK =
    mapOf(
        PunctualityTone.OnTime to ToneColors(Color(0xFF9BDEB1), Color(0xFF193E2C)),
        PunctualityTone.Late to ToneColors(Color(0xFFFFD278), Color(0xFF443417)),
        PunctualityTone.VeryLate to ToneColors(Color(0xFFFDBA74), Color(0xFF492719)),
        PunctualityTone.Early to ToneColors(Color(0xFFA9CAFF), Color(0xFF172E50)),
        PunctualityTone.Cancelled to ToneColors(Color(0xFFFFADB1), Color(0xFF4A252B)),
        PunctualityTone.Unknown to ToneColors(Color(0xFFB6C8BD), Color(0xFF214447)),
    )

private const val HALF_LIGHT = 0.5f

/** The web's caption for a delay: "Včas", "+3 min", "−2 min", "Zrušeno", "Dle JŘ". */
@Composable
fun delayCaption(
    delayMinutes: Long?,
    cancelled: Boolean = false,
): String =
    when {
        cancelled -> stringResource(R.string.delay_cancelled)
        delayMinutes == null -> stringResource(R.string.delay_scheduled)
        delayMinutes == 0L -> stringResource(R.string.delay_on_time)
        delayMinutes > 0 -> stringResource(R.string.delay_short, delayMinutes)
        else -> stringResource(R.string.delay_early, -delayMinutes)
    }

/** A delay as the web shows it in a trip's head: its caption on its tone's tint. */
@Composable
fun DelayChip(
    delayMinutes: Long?,
    modifier: Modifier = Modifier,
    cancelled: Boolean = false,
) {
    val tone = PunctualityTone.of(delayMinutes, cancelled).colors
    Surface(modifier, shape = MaterialTheme.shapes.small, color = tone.fill, contentColor = tone.text) {
        Row(
            Modifier.padding(horizontal = ObehyTheme.spacing.s, vertical = ObehyTheme.spacing.xs / 2),
            horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(delayCaption(delayMinutes, cancelled), style = MaterialTheme.typography.labelLarge)
        }
    }
}
