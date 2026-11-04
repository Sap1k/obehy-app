package cz.obehy.feature.planner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.core.app.ActivityCompat
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.core.ui.components.StaggeredEntry
import cz.obehy.core.ui.distinctModeIcons
import cz.obehy.core.ui.icon
import kotlinx.coroutines.delay
import cz.obehy.core.ui.R as UiR

/**
 * Picking a place: type, and stops, addresses and places appear as you do. Before anything is
 * typed it offers "My location", the stops around you when picking a start, and your own places. A search view takes the whole screen
 * like Material's search, so it has its own frame instead of ObehyScreen.
 */
@Composable
internal fun PlaceSearchScreen(
    search: PlaceSearch,
    onType: (String) -> Unit,
    onChoose: (Place) -> Unit,
    onForget: (Place) -> Unit,
    onClose: () -> Unit,
    onLocationAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val list = rememberLazyListState()
    // A list keeps its place by item, so it would stay on an old result as new ones come in above.
    LaunchedEffect(search.text, search.results) { list.requestScrollToItem(0) }
    val location = rememberLocationAsk(onAnswer = onLocationAnswer, onAllow = { onChoose(Place.myLocation()) })
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
            state = list,
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
            if (search.text.isBlank()) {
                startingPoints(search, location, onChoose, onForget)
            }
            items(search.results, key = { it.id }) { place ->
                PlaceRow(place, onClick = { onChoose(place) })
            }
        }
    }
}

/** What the picker offers before anything is typed. */
private fun LazyListScope.startingPoints(
    search: PlaceSearch,
    location: LocationAsk,
    onChoose: (Place) -> Unit,
    onForget: (Place) -> Unit,
) {
    if (!search.target.stopsOnly) {
        item(key = "my-location") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.my_location)) },
                leadingContent = { Icon(Icons.Rounded.MyLocation, contentDescription = null) },
                colors = rowColors(),
                modifier =
                    Modifier.clickable {
                        if (search.nearbyStatus == NearbyStatus.NeedsPermission) {
                            location.askFor(true)
                        } else {
                            onChoose(Place.myLocation())
                        }
                    },
            )
        }
    }
    // Where you are going, or going through, is rarely right here.
    if (search.target == PlaceTarget.From) nearby(search, location, onChoose)
    recents(search, onChoose, onForget)
}

private fun LazyListScope.recents(
    search: PlaceSearch,
    onChoose: (Place) -> Unit,
    onForget: (Place) -> Unit,
) {
    if (search.recents.isNotEmpty()) item(key = "recent-title") { SectionTitle(R.string.recent) }
    itemsIndexed(search.recents, key = { _, it -> "recent-${it.id}" }) { index, place ->
        StaggeredEntry(index, enabled = true) {
            PlaceRow(place, onClick = { onChoose(place) }, onLongClick = { onForget(place) })
        }
    }
}

private fun LazyListScope.nearby(
    search: PlaceSearch,
    location: LocationAsk,
    onChoose: (Place) -> Unit,
) {
    when (search.nearbyStatus) {
        NearbyStatus.Unknown -> {
            Unit
        }

        NearbyStatus.Loading -> {
            item(key = "near-loading") { NearbyLoading() }
        }

        NearbyStatus.NeedsPermission -> {
            item(key = "near-ask") {
                val blocked = location.blocked
                ListItem(
                    headlineContent = { Text(stringResource(R.string.near_you_ask)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (blocked) R.string.near_you_blocked_body else R.string.near_you_ask_body,
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.NearMe, contentDescription = null) },
                    colors = rowColors(),
                    modifier = Modifier.clickable { location.askFor(false) },
                )
            }
        }

        NearbyStatus.Off -> {
            item(key = "near-off") {
                val context = LocalContext.current
                ListItem(
                    headlineContent = { Text(stringResource(R.string.near_you_off)) },
                    supportingContent = { Text(stringResource(R.string.near_you_off_body)) },
                    leadingContent = { Icon(Icons.Rounded.NearMe, contentDescription = null) },
                    colors = rowColors(),
                    modifier =
                        Modifier.clickable {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                )
            }
        }

        NearbyStatus.Ready -> {
            if (search.nearby.isNotEmpty()) item(key = "near-title") { SectionTitle(R.string.near_you) }
            itemsIndexed(search.nearby, key = { _, it -> "near-${it.place.id}" }) { index, stop ->
                StaggeredEntry(index, enabled = true) {
                    PlaceRow(stop.place, detail = distanceText(stop.metres), onClick = { onChoose(stop.place) })
                }
            }
        }
    }
}

/**
 * The user beat the stops here. Something redrawing every frame keeps the keyboard waiting, so the
 * animation holds back until the keyboard is on its way, or plainly isn't coming.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NearbyLoading(modifier: Modifier = Modifier) {
    var noKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(KEYBOARD_ARRIVAL_MS)
        noKeyboard = true
    }
    Column(modifier) {
        SectionTitle(R.string.near_you)
        AnimatedVisibility(WindowInsets.isImeVisible || noKeyboard) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = ObehyTheme.spacing.l))
        }
    }
}

private const val KEYBOARD_ARRIVAL_MS = 1_000L

/** The title over a group of rows. */
@Composable
internal fun SectionTitle(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(title),
        modifier.padding(
            start = ObehyTheme.spacing.l,
            end = ObehyTheme.spacing.l,
            top = ObehyTheme.spacing.l,
            bottom = ObehyTheme.spacing.xs,
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A place in the list: what it is, where, and what serves it. [detail] says more than its area. */
@Composable
private fun PlaceRow(
    place: Place,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = place.area,
    onLongClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(place.label()) },
        supportingContent = detail?.let { { Text(it) } },
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
        colors = rowColors(),
        modifier =
            modifier.combinedClickable(
                onLongClickLabel = onLongClick?.let { stringResource(R.string.forget) },
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    )
}

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)

/** Asks for the location, only ever when the user taps something that needs it. */
private class LocationAsk(
    /** Android won't ask again, so the system settings are the only way left. */
    val blocked: Boolean,
    val askFor: (choosing: Boolean) -> Unit,
)

@Composable
private fun rememberLocationAsk(
    onAnswer: () -> Unit,
    onAllow: () -> Unit,
): LocationAsk {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var blocked by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answers ->
            onAnswer()
            if (answers.values.any { it }) {
                if (choosing) onAllow()
            } else {
                blocked = activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
            }
            choosing = false
        }
    return LocationAsk(blocked) { choose ->
        if (blocked) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        } else {
            choosing = choose
            ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}

private fun Place.icon(): ImageVector =
    when (kind) {
        PlaceKind.Stop -> modes.minByOrNull { it.ordinal }?.icon ?: Icons.Rounded.Place
        PlaceKind.Address -> Icons.Rounded.Home
        PlaceKind.Poi -> Icons.Rounded.Place
        PlaceKind.MyLocation -> Icons.Rounded.MyLocation
    }
