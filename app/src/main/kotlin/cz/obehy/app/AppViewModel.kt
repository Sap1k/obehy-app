package cz.obehy.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.obehy.core.data.LiveMapAvailability
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.LiveMapSource
import cz.obehy.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState

    data object NeedsOnboarding : AppState

    data class Ready(
        val mode: AppMode,
        /** What the map tab shows, if there is one. */
        val liveMap: LiveMapSource,
    ) : AppState
}

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
        availability: LiveMapAvailability,
    ) : ViewModel() {
        val state: StateFlow<AppState> =
            combine(settings.settings, availability.source) { s, map ->
                if (s.isOnboarded) AppState.Ready(s.mode!!, map) else AppState.NeedsOnboarding
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

        init {
            // Whether the MOTIS server in use draws a map (railviz): tried once per start and on each change.
            viewModelScope.launch { availability.keepChecked() }
        }

        val theme: StateFlow<ThemeMode> =
            settings.settings
                .map { it.theme }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)
    }
