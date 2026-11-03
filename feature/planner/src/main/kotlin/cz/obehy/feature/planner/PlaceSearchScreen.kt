package cz.obehy.feature.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.distinctModeIcons
import cz.obehy.core.ui.icon
import cz.obehy.core.ui.R as UiR

/**
 * Picking a place: type, and stops, addresses and places appear as you do. A search view takes
 * the whole screen like Material's search, so it has its own frame instead of ObehyScreen.
 */
@Composable
internal fun PlaceSearchScreen(
    search: PlaceSearch,
    onType: (String) -> Unit,
    onChoose: (Place) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
                title = {
                    TextField(
                        value = search.text,
                        onValueChange = onType,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        placeholder = {
                            Text(
                                stringResource(
                                    if (search.target.stopsOnly) R.string.place_hint_stop else R.string.place_hint,
                                ),
                            )
                        },
                        trailingIcon = {
                            if (search.text.isNotEmpty()) {
                                IconButton(onClick = { onType("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.place_clear))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                },
            )
        },
    ) { padding ->
        // The list ends above the keyboard, so every result can be scrolled into view.
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = WindowInsets.ime.asPaddingValues(),
        ) {
            if (search.loading) {
                item { LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = ObehyTheme.spacing.l)) }
            }
            val note =
                when {
                    search.failed -> R.string.place_error
                    !search.loading && search.results.isEmpty() && search.text.trim().length >= 2 -> R.string.place_none
                    else -> null
                }
            note?.let {
                item {
                    Text(
                        stringResource(it),
                        Modifier.padding(ObehyTheme.spacing.l),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(search.results, key = { it.id }) { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = place.area?.let { { Text(it) } },
                    leadingContent = { Icon(place.icon(), contentDescription = null) },
                    trailingContent =
                        place.modes.takeIf { it.isNotEmpty() }?.let { modes ->
                            {
                                Row(horizontalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.xs)) {
                                    distinctModeIcons(modes).take(4).forEach { icon ->
                                        Icon(
                                            icon,
                                            null,
                                            Modifier.size(ObehyTheme.sizes.iconSmall),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { onChoose(place) },
                )
            }
        }
    }
}

private fun Place.icon(): ImageVector =
    when (kind) {
        PlaceKind.Stop -> modes.minByOrNull { it.ordinal }?.icon ?: Icons.Rounded.Place
        PlaceKind.Address -> Icons.Rounded.Home
        PlaceKind.Poi -> Icons.Rounded.Place
    }
