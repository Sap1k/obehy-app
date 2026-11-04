package cz.obehy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import cz.obehy.core.model.AppMode
import cz.obehy.core.model.AppSettings
import cz.obehy.core.model.RoutingProvider
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.model.ThemeMode
import cz.obehy.core.model.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The user's mode, routing provider and search options (by enum name) and whether onboarding is done. */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) {
        val settings: Flow<AppSettings> =
            store.data.map { prefs ->
                AppSettings(
                    mode = prefs[MODE]?.let { name -> AppMode.entries.firstOrNull { it.name == name } },
                    routingProvider =
                        prefs[PROVIDER]?.let { name ->
                            RoutingProvider.entries.firstOrNull { it.name == name }
                        },
                    onboardingFinished = prefs[ONBOARDED] ?: false,
                    theme = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: ThemeMode.System,
                )
            }

        suspend fun current(): AppSettings = settings.first()

        suspend fun setMode(mode: AppMode) {
            store.edit { it[MODE] = mode.name }
        }

        suspend fun setRoutingProvider(provider: RoutingProvider) {
            store.edit { it[PROVIDER] = provider.name }
        }

        suspend fun setTheme(theme: ThemeMode) {
            store.edit { it[THEME] = theme.name }
        }

        suspend fun finishOnboarding() {
            store.edit { it[ONBOARDED] = true }
        }

        /** The journey search options, remembered between searches. */
        val searchOptions: Flow<SearchOptions> =
            store.data.map { prefs ->
                SearchOptions(
                    lowFloor = prefs[LOW_FLOOR] ?: false,
                    routedTransfers = prefs[ROUTED_TRANSFERS] ?: false,
                    types =
                        prefs[TRANSPORT_TYPES]
                            ?.mapNotNullTo(
                                mutableSetOf(),
                            ) { name -> TransportType.entries.firstOrNull { it.name == name } }
                            ?.takeIf { it.isNotEmpty() }
                            ?: TransportType.entries.toSet(),
                )
            }

        suspend fun setSearchOptions(options: SearchOptions) {
            store.edit {
                it[LOW_FLOOR] = options.lowFloor
                it[ROUTED_TRANSFERS] = options.routedTransfers
                it[TRANSPORT_TYPES] = options.types.mapTo(mutableSetOf()) { type -> type.name }
            }
        }

        private companion object {
            val MODE = stringPreferencesKey("mode")
            val PROVIDER = stringPreferencesKey("routing_provider")
            val ONBOARDED = booleanPreferencesKey("onboarding_finished")
            val THEME = stringPreferencesKey("theme")
            val LOW_FLOOR = booleanPreferencesKey("search_low_floor")
            val ROUTED_TRANSFERS = booleanPreferencesKey("search_routed_transfers")
            val TRANSPORT_TYPES = stringSetPreferencesKey("search_transport_types")
        }
    }
