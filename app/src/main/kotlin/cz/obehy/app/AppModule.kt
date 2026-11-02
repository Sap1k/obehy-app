package cz.obehy.app

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** How the app names itself to Transitous and Oběhy (Transitous asks apps to identify). */
    @Provides
    @Named("userAgent")
    fun userAgent(): String = "Obehy-Android/${BuildConfig.VERSION_NAME} (+https://obehy.cz)"
}
