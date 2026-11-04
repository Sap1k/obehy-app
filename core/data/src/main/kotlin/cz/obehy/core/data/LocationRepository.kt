package cz.obehy.core.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import cz.obehy.core.model.LatLon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Where the phone is. Nothing here asks for the permission: screens do, when the user wants it. */
interface LocationRepository {
    /** Whether the app may read the location, precisely or approximately. */
    val allowed: StateFlow<Boolean>

    /** Whether the phone's location is switched on. */
    val enabled: Boolean

    /** Looks at the permission again: it changes outside the app. */
    fun recheck()

    /** The latest position the phone already knows, however old; quick, for ranking. */
    fun lastKnown(): LatLon?

    /** The position now, or a recent one when none comes in time; null when there is neither. */
    suspend fun current(): LatLon?
}

@Singleton
internal class AndroidLocationRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : LocationRepository {
        private val manager = requireNotNull(context.getSystemService<LocationManager>())
        private val permission = MutableStateFlow(hasPermission())

        override val allowed: StateFlow<Boolean> = permission.asStateFlow()

        override val enabled: Boolean get() = LocationManagerCompat.isLocationEnabled(manager)

        override fun recheck() {
            permission.value = hasPermission()
        }

        override fun lastKnown(): LatLon? = newest()?.toLatLon()

        // The permission is checked first, in recheck().
        @SuppressLint("MissingPermission")
        override suspend fun current(): LatLon? {
            recheck()
            if (!permission.value) return null
            val known = newest()
            if (known != null && known.age() < FRESH) return known.toLatLon()
            val provider = provider() ?: return known?.takeIf { it.age() < USABLE }?.toLatLon()
            val fix =
                withTimeoutOrNull(TIMEOUT.toMillis()) {
                    suspendCancellableCoroutine { waiting ->
                        val signal = CancellationSignal()
                        waiting.invokeOnCancellation { signal.cancel() }
                        LocationManagerCompat.getCurrentLocation(
                            manager,
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context),
                        ) { waiting.resume(it) }
                    }
                }
            return (fix ?: known?.takeIf { it.age() < USABLE })?.toLatLon()
        }

        /** The phone's combined provider where there is one, otherwise whichever is on. */
        private fun provider(): String? {
            val on = manager.getProviders(true)
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in on -> {
                    LocationManager.FUSED_PROVIDER
                }

                LocationManager.NETWORK_PROVIDER in on -> {
                    LocationManager.NETWORK_PROVIDER
                }

                LocationManager.GPS_PROVIDER in on -> {
                    LocationManager.GPS_PROVIDER
                }

                else -> {
                    null
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun newest(): Location? =
            if (hasPermission()) {
                manager
                    .getProviders(true)
                    .mapNotNull { manager.getLastKnownLocation(it) }
                    .maxByOrNull { it.elapsedRealtimeNanos }
            } else {
                null
            }

        private fun hasPermission(): Boolean =
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

        private fun Location.age(): Duration =
            Duration.ofNanos(SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos)

        private fun Location.toLatLon() = LatLon(latitude, longitude)

        private companion object {
            /** A fix this young saves waiting for another. */
            val FRESH: Duration = Duration.ofMinutes(1)

            /** A fix this young is still better than nothing when no new one comes. */
            val USABLE: Duration = Duration.ofMinutes(10)
            val TIMEOUT: Duration = Duration.ofSeconds(6)
        }
    }
