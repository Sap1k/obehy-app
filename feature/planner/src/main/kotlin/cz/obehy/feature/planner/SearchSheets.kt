package cz.obehy.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.model.TransportType
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.clock
import cz.obehy.core.ui.icon
import cz.obehy.core.ui.label
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** When to travel: leave or arrive, now or at a date and time. */
@Composable
internal fun TimeSheet(
    query: JourneyQuery,
    onDone: (time: Instant?, arriveBy: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val initial = (query.time ?: Instant.now()).atZone(zone).toLocalDateTime()
    var arriveBy by rememberSaveable { mutableStateOf(query.arriveBy) }
    var now by rememberSaveable { mutableStateOf(query.time == null) }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0)) }
    var picking by remember { mutableStateOf<Picker?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            Modifier.padding(horizontal = ObehyTheme.spacing.xl).padding(bottom = ObehyTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.l),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to R.string.depart, true to R.string.arrive).forEachIndexed { index, (arrive, label) ->
                    SegmentedButton(
                        selected = arriveBy == arrive,
                        onClick = { arriveBy = arrive },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(label)) }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = now, onClick = { now = true }, label = { Text(stringResource(R.string.now)) })
                OutlinedButton(onClick = { picking = Picker.Date }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CalendarToday, null, Modifier.size(ObehyTheme.sizes.iconSmall))
                    Text(
                        DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern)).format(date),
                        Modifier.padding(start = ObehyTheme.spacing.s),
                    )
                }
                OutlinedButton(onClick = { picking = Picker.Time }) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(ObehyTheme.sizes.iconSmall))
                    Text(
                        LocalDateTime
                            .of(date, time)
                            .atZone(zone)
                            .toInstant()
                            .clock(),
                        Modifier.padding(start = ObehyTheme.spacing.s),
                    )
                }
            }
            Button(
                onClick = {
                    onDone(
                        if (now) null else LocalDateTime.of(date, time).atZone(zone).toInstant(),
                        arriveBy,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.done)) }
        }
    }

    when (picking) {
        Picker.Date -> {
            val state = rememberDatePickerState(date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            date =
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        now = false
                        picking = null
                    }) { Text(stringResource(R.string.done)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
            ) { DatePicker(state) }
        }

        Picker.Time -> {
            val state = rememberTimePickerState(time.hour, time.minute)
            AlertDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        time = LocalTime.of(state.hour, state.minute)
                        now = false
                        picking = null
                    }) { Text(stringResource(R.string.done)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
                text = { TimePicker(state) },
            )
        }

        null -> {
            Unit
        }
    }
}

private enum class Picker { Date, Time }

/** The remembered options: which transport to use, low-floor, and walks along real paths. */
@Composable
internal fun OptionsSheet(
    options: SearchOptions,
    onChange: (SearchOptions) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.padding(bottom = ObehyTheme.spacing.xl)) {
            Text(
                stringResource(R.string.options_title),
                Modifier.padding(horizontal = ObehyTheme.spacing.xl),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.types_title),
                Modifier.padding(
                    start = ObehyTheme.spacing.xl,
                    top = ObehyTheme.spacing.l,
                    bottom = ObehyTheme.spacing.s,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                Modifier.padding(horizontal = ObehyTheme.spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.s),
            ) {
                TransportType.entries.forEach { type ->
                    val on = type in options.types
                    FilterChip(
                        selected = on,
                        // The last type stays on: a search without transport finds nothing.
                        onClick = {
                            if (!on ||
                                options.types.size > 1
                            ) {
                                onChange(
                                    options.copy(
                                        types =
                                            if (on) {
                                                options.types - type
                                            } else {
                                                options.types +
                                                    type
                                            },
                                    ),
                                )
                            }
                        },
                        label = { Text(stringResource(type.label)) },
                        leadingIcon = { Icon(type.icon, null, Modifier.size(ObehyTheme.sizes.iconSmall)) },
                    )
                }
            }
            if (options.types.size == 1) {
                Text(
                    stringResource(R.string.types_keep_one),
                    Modifier.padding(horizontal = ObehyTheme.spacing.xl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.low_floor_title)) },
                supportingContent = { Text(stringResource(R.string.low_floor_body)) },
                trailingContent = { Switch(options.lowFloor, { onChange(options.copy(lowFloor = it)) }) },
                modifier = Modifier.padding(horizontal = ObehyTheme.spacing.s),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.routed_title)) },
                supportingContent = { Text(stringResource(R.string.routed_body)) },
                trailingContent = { Switch(options.routedTransfers, { onChange(options.copy(routedTransfers = it)) }) },
                modifier = Modifier.padding(horizontal = ObehyTheme.spacing.s),
            )
            if (options != SearchOptions()) {
                TextButton(
                    onClick = { onChange(SearchOptions()) },
                    Modifier.padding(horizontal = ObehyTheme.spacing.l),
                ) {
                    Text(stringResource(R.string.reset))
                }
            }
        }
    }
}
