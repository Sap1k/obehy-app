package cz.obehy.core.data

import cz.obehy.core.model.LatLon
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.MotisPlanRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What is tried on a server, in this order. */
enum class ServerCheckStep {
    /** It answers, and as MOTIS. */
    Server,

    /** It finds stops by name. */
    Places,

    /** It lists the stops around a place. */
    Stops,

    /** It plans a journey and looks it up again. */
    Journeys,
}

sealed interface ServerCheckResult {
    data class Passed(
        val version: String,
    ) : ServerCheckResult

    /** The server didn't manage [step]. */
    data class Failed(
        val step: ServerCheckStep,
    ) : ServerCheckResult
}

/**
 * Tries out a MOTIS server the way the app will use it. Servers don't publish their API description,
 * so this asks them for real data, wherever their coverage is, and reads it with the app's own
 * models. A journey search that finds nothing still counts, as the two stops may not be connected.
 */
class MotisServerChecker(
    private val computation: CoroutineDispatcher,
    private val server: (url: String) -> MotisApi,
) {
    /** Checks the server at [url], telling [onStep] as each step starts. */
    suspend fun check(
        url: String,
        onStep: (ServerCheckStep) -> Unit,
    ): ServerCheckResult =
        withContext(computation) {
            val motis = server(url)
            var step = ServerCheckStep.Server
            onStep(step)
            try {
                val start = motis.initial()
                step = ServerCheckStep.Places
                onStep(step)
                val found = motis.someStops(start.lat, start.lon)
                step = ServerCheckStep.Stops
                onStep(step)
                val first = found.first()
                val (min, max) = boxAround(LatLon(first.lat, first.lon), SEARCH_METRES)
                val around = motis.mapStops(min, max).mapNotNull { it.stopId }.distinct()
                step = ServerCheckStep.Journeys
                onStep(step)
                val ends = around.takeIf { it.size >= 2 } ?: found.map { it.id }
                val plan = motis.plan(MotisPlanRequest(ends.first(), ends.last()))
                plan.itineraries.firstNotNullOfOrNull { it.id }?.let { motis.refreshItinerary(it) }
                ServerCheckResult.Passed(start.serverConfig.motisVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ServerCheckResult.Failed(step)
            }
        }

    /** At least two stops near `lat,lon`, under one of the words most timetables have. */
    private suspend fun MotisApi.someStops(
        lat: Double,
        lon: Double,
    ) = SEARCHES.firstNotNullOf { text ->
        geocode(text, stopsOnly = true, bias = "$lat,$lon").takeIf { it.size >= 2 }
    }

    private companion object {
        val SEARCHES = listOf("bus", "station", "bahn", "central")
        const val SEARCH_METRES = 2_000.0
    }
}
