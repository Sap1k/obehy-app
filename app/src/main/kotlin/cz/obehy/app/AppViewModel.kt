package cz.obehy.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState

    data object NeedsOnboarding : AppState

    data class Ready(
        val mode: AppMode,
    ) : AppState
}

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
    ) : ViewModel() {
        val state: StateFlow<AppState> =
            settings.settings
                .map { s -> if (s.isOnboarded) AppState.Ready(s.mode!!) else AppState.NeedsOnboarding }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

        val theme: StateFlow<ThemeMode> =
            settings.settings
                .map { it.theme }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)
    }
