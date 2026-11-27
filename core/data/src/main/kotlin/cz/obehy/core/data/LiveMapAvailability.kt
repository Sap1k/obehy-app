package cz.obehy.core.data

import cz.obehy.core.model.AppMode
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.LiveMapSource
import cz.obehy.core.network.motis.MotisApi
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether there's a live map, and what it shows: Oběhy's vehicles in the modes with Oběhy; in the
 * mode without, railviz if the MOTIS server answers it. Servers don't say, so each is tried (once
 * per app start, and whenever it changes) and the answer is remembered with its address. A server
 * that can't be reached keeps the answer it had.
 */
@Singleton
class LiveMapAvailability
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val motis: MotisApi,
    ) {
        val source: Flow<LiveMapSource> =
            combine(settings.settings, settings.railvizServers) { current, railviz ->
                sourceOf(current.mode, current.motisUrl, railviz)
            }.distinctUntilChanged()

        /** Tries every server the settings name, as they change; runs as long as it's collected from. */
        suspend fun keepChecked() {
            settings.settings
                .map { it.mode to it.motisUrl }
                .distinctUntilChanged()
                .collect { (mode, url) -> if (mode == AppMode.MotisOnly) check(url) }
        }

        /** Asks the server at [url] (the one [motis] talks to) for railviz around its own centre. */
        private suspend fun check(url: String) {
            val answers =
                try {
                    val centre = motis.initial()
                    val (min, max) = boxAround(LatLon(centre.lat, centre.lon), PROBE_METRES)
                    val now = Instant.now()
                    motis.mapTrips(min, max, PROBE_ZOOM, now, now.plus(PROBE_WINDOW))
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: ResponseException) {
                    false
                } catch (_: SerializationException) {
                    false
                } catch (_: Exception) {
                    return
                }
            settings.setRailviz(url, answers)
        }

        private companion object {
            const val PROBE_METRES = 500.0
            const val PROBE_ZOOM = 15
            val PROBE_WINDOW: Duration = Duration.ofMinutes(1)
        }
    }

/** What the map shows for [mode] on the MOTIS server at [motisUrl], given the servers known to answer railviz. */
internal fun sourceOf(
    mode: AppMode?,
    motisUrl: String,
    railviz: Set<String>,
): LiveMapSource =
    when {
        mode == null -> LiveMapSource.None
        mode.usesObehy -> LiveMapSource.Obehy
        motisUrl in railviz -> LiveMapSource.Railviz
        else -> LiveMapSource.None
    }
