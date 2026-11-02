package cz.obehy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.AppSettings
import cz.obehy.core.model.RoutingProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The user's mode and routing provider (by enum name) and whether onboarding is done, in DataStore. */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            mode = prefs[MODE]?.let { name -> AppMode.entries.firstOrNull { it.name == name } },
            routingProvider = prefs[PROVIDER]?.let { name ->
                RoutingProvider.entries.firstOrNull { it.name == name }
            },
            onboardingFinished = prefs[ONBOARDED] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setMode(mode: AppMode) {
        store.edit { it[MODE] = mode.name }
    }

    suspend fun setRoutingProvider(provider: RoutingProvider) {
        store.edit { it[PROVIDER] = provider.name }
    }

    suspend fun finishOnboarding() {
        store.edit { it[ONBOARDED] = true }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val PROVIDER = stringPreferencesKey("routing_provider")
        val ONBOARDED = booleanPreferencesKey("onboarding_finished")
    }
}
