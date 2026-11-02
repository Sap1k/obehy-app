package cz.obehy.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.model.RoutingProvider
import cz.obehy.core.network.MotisJson
import cz.obehy.core.network.ObehyJson
import cz.obehy.core.network.apiClient
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.obehy.ObehyApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
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
    fun motisApi(
        settings: SettingsRepository,
        @Named("userAgent") userAgent: String,
    ): MotisApi =
        MotisApi(apiClient(OkHttp.create(), MotisJson, userAgent)) {
            (settings.current().routingProvider ?: RoutingProvider.Transitous).baseUrl
        }

    @Provides
    @Singleton
    fun obehyApi(
        @Named("userAgent") userAgent: String,
    ): ObehyApi = ObehyApi(apiClient(OkHttp.create(), ObehyJson, userAgent))
}
