package cz.obehy.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.AppSettings
import cz.obehy.core.model.RoutingProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The mode and routing provider: chosen at first start, changed in the settings. */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun choose(mode: AppMode) {
        viewModelScope.launch { settings.setMode(mode) }
    }

    fun choose(provider: RoutingProvider) {
        viewModelScope.launch { settings.setRoutingProvider(provider) }
    }
}
