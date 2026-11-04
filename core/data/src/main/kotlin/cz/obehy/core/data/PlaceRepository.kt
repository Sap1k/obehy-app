package cz.obehy.core.data

import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Mode
import cz.obehy.core.model.NearbyStop
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.MotisMatch
import cz.obehy.core.network.motis.MotisStop
import cz.obehy.core.network.motis.domainModes
import cz.obehy.core.network.motis.toPlace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.cos

/** The place search: stops, addresses and places from the current routing provider. */
@Singleton
class PlaceRepository
    @Inject
    constructor(
        private val motis: MotisApi,
        @param:Named("computation") private val computation: CoroutineDispatcher,
    ) {
        /**
         * Places matching [text], ranked around [near]; only stops with [stopsOnly] (vias must be
         * stops).
         */
        suspend fun search(
            text: String,
            stopsOnly: Boolean = false,
            near: LatLon? = null,
        ): Result<List<Place>> =
            runCatching {
                withContext(computation) {
                    val bias = near?.rounded()?.let { "${it.lat},${it.lon}" } ?: CZECHIA_CENTRE
                    val matches = motis.geocode(text.trim(), appLanguage(), stopsOnly, bias)
                    mergeDuplicateStops(matches)
                }
            }

        /** The closest stops to [at], nearest first; it looks further out where they are sparse. */
        suspend fun nearby(at: LatLon): Result<List<NearbyStop>> =
            runCatching {
                withContext(computation) {
                    val close = stopsAround(at, NEAR_METRES)
                    if (close.size >= ENOUGH_STOPS) close else stopsAround(at, FAR_METRES)
                }
            }

        private suspend fun stopsAround(
            at: LatLon,
            metres: Double,
        ): List<NearbyStop> {
            val (min, max) = boxAround(at.rounded(), metres)
            return groupNearbyStops(motis.mapStops(min, max), at).take(MAX_NEARBY)
        }

        private companion object {
            /** Without a location, rank Czech matches first. */
            const val CZECHIA_CENTRE = "49.8,15.5"
            const val NEAR_METRES = 800.0
            const val FAR_METRES = 2_500.0
            const val ENOUGH_STOPS = 3
            const val MAX_NEARBY = 6
        }
    }

/** The corners, as `lat,lon`, of the box reaching [metres] from [centre] each way. */
internal fun boxAround(
    centre: LatLon,
    metres: Double,
): Pair<String, String> {
    val dLat = metres / LatLon.METRES_PER_DEGREE
    val dLon = dLat / cos(Math.toRadians(centre.lat))
    return corner(centre.lat - dLat, centre.lon - dLon) to corner(centre.lat + dLat, centre.lon + dLon)
}

private fun corner(
    lat: Double,
    lon: Double,
) = String.format(Locale.ROOT, "%.5f,%.5f", lat, lon)

/** The language MOTIS names places in: Czech on a Czech phone, English otherwise. */
internal fun appLanguage(): String = if (Locale.getDefault().language == "cs") "cs" else "en"

/**
 * Transitous lists a big station once per feed that serves it ("Praha hl.n." from the Austrian,
 * Swiss and Czech feeds). Stops with the same name within a kilometre become one: the one from
 * the station's own country's feed when there is one, with every mode that serves any of them.
 */
internal fun mergeDuplicateStops(matches: List<MotisMatch>): List<Place> {
    val groups = mutableListOf<MutableList<MotisMatch>>()
    for (match in matches) {
        val group =
            groups.firstOrNull { group ->
                val first = group.first()
                match.type == "STOP" && first.type == "STOP" &&
                    stationKey(first.name) == stationKey(match.name) &&
                    closeTo(first, match)
            }
        if (group != null) group += match else groups += mutableListOf(match)
    }
    return groups
        .map { group ->
            val best = group.firstOrNull { it.isDomestic() } ?: group.first()
            val place = best.toPlace()
            when (place.kind) {
                PlaceKind.Stop -> place.copy(modes = group.flatMapTo(mutableSetOf()) { it.toPlace().modes })
                else -> place
            }
        }.distinctBy { it.id }
}

/**
 * MOTIS lists platforms: these become one row per stop, nearest to [at] first. Platforms belong
 * together by their parent, or by name when the feed has no parents (PID). The row routes from a
 * stop in the feed that lists most of them, so a foreign feed's copy of a station never wins; any
 * platform will do, because MOTIS also boards at its neighbours.
 */
internal fun groupNearbyStops(
    stops: List<MotisStop>,
    at: LatLon,
): List<NearbyStop> {
    val groups = mutableListOf<MutableList<MapStop>>()
    val found =
        stops
            .filter { it.stopId != null }
            .map { MapStop(it, stationKey(it.name), LatLon(it.lat, it.lon)) }
            .sortedBy { it.position.metresTo(at) }
    for (stop in found) {
        val group = groups.firstOrNull { group -> group.any { it.isSameStop(stop) } }
        if (group != null) group += stop else groups += mutableListOf(stop)
    }
    return groups
        .map { group ->
            val feed =
                group
                    .groupingBy { feedTag(it.stop) }
                    .eachCount()
                    .maxBy { it.value }
                    .key
            val best = group.first { feedTag(it.stop) == feed }.stop
            val modes: Set<Mode> = group.flatMapTo(mutableSetOf()) { it.stop.domainModes() }
            NearbyStop(
                best.toPlace(best.parentId ?: requireNotNull(best.stopId), modes),
                group
                    .first()
                    .position
                    .metresTo(at)
                    .toInt(),
            )
        }.distinctBy { it.place.id }
}

/** A stop with what comparing it needs worked out once: there can be hundreds in a city. */
private class MapStop(
    val stop: MotisStop,
    val key: String,
    val position: LatLon,
) {
    fun isSameStop(other: MapStop): Boolean =
        (stop.parentId != null && stop.parentId == other.stop.parentId) ||
            (key == other.key && position.metresTo(other.position) < SAME_STATION_METRES)
}

/** The feed tag IDs start with: `cz-PID` in `cz-PID_U850Z1P`. */
private fun feedTag(stop: MotisStop): String = stop.stopId.orEmpty().substringBefore('_')

/** Whether the feed this stop comes from (the ID's `cz-…` tag) is from the stop's country. */
private fun MotisMatch.isDomestic(): Boolean {
    val country = country?.lowercase() ?: return false
    return id.startsWith("$country-") || id.startsWith("${country}_")
}

/** "Praha hl. n.", "Praha hl.n." and "Praha, hlavní nádraží" are one station. */
private fun stationKey(name: String): String =
    Normalizer
        .normalize(name.lowercase(), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace(SHORT_MAIN_STATION, "hlavni nadrazi")
        .replace(PUNCTUATION, "")

private val MARKS = Regex("\\p{M}")
private val SHORT_MAIN_STATION = Regex("\\bhl\\.\\s*n\\.?")
private val PUNCTUATION = Regex("[^a-z0-9]")

private fun closeTo(
    a: MotisMatch,
    b: MotisMatch,
): Boolean = LatLon(a.lat, a.lon).metresTo(LatLon(b.lat, b.lon)) < SAME_STATION_METRES

private const val SAME_STATION_METRES = 1_000.0
