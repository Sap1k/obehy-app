package cz.obehy.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import cz.obehy.core.data.AndroidLocationRepository
import cz.obehy.core.data.HISTORY_DATABASE
import cz.obehy.core.data.HistoryDatabase
import cz.obehy.core.data.JourneySearchDao
import cz.obehy.core.data.LocationRepository
import cz.obehy.core.data.MotisServerChecker
import cz.obehy.core.data.RoomSearchHistoryStore
import cz.obehy.core.data.SearchHistoryStore
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.network.MotisJson
import cz.obehy.core.network.ObehyJson
import cz.obehy.core.network.apiClient
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.obehy.ObehyApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

/** `@Named("userAgent")` comes from the app module (BuildConfig), e.g. `Obehy-Android/0.1.0`. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun dataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    internal fun historyDatabase(
        @ApplicationContext context: Context,
    ): HistoryDatabase = Room.databaseBuilder(context, HistoryDatabase::class.java, HISTORY_DATABASE).build()

    @Provides
    internal fun journeySearches(database: HistoryDatabase): JourneySearchDao = database.searches()

    /** Where answers are decoded and worked through, so a screen's animations keep their frames. */
    @Provides
    @Named("computation")
    fun computation(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun motisApi(
        settings: SettingsRepository,
        @Named("userAgent") userAgent: String,
    ): MotisApi = MotisApi(apiClient(OkHttp.create(), MotisJson, userAgent)) { settings.current().motisUrl }

    /** Talks to the server being checked, not to the one in the settings. */
    @Provides
    @Singleton
    fun motisServerChecker(
        @Named("userAgent") userAgent: String,
        @Named("computation") computation: CoroutineDispatcher,
    ): MotisServerChecker {
        val client = apiClient(OkHttp.create(), MotisJson, userAgent)
        return MotisServerChecker(computation) { url -> MotisApi(client) { url } }
    }

    @Provides
    @Singleton
    fun obehyApi(
        @Named("userAgent") userAgent: String,
    ): ObehyApi = ObehyApi(apiClient(OkHttp.create(), ObehyJson, userAgent))
}

@Module
@InstallIn(SingletonComponent::class)
internal interface LocationModule {
    @Binds
    fun location(repository: AndroidLocationRepository): LocationRepository

    @Binds
    fun searchHistory(store: RoomSearchHistoryStore): SearchHistoryStore
}
