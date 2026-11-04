package cz.obehy.core.data

import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.MotisMatch
import cz.obehy.core.network.motis.toPlace
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos

/** The place search: stops, addresses and places from the current routing provider. */
@Singleton
class PlaceRepository
    @Inject
    constructor(
        private val motis: MotisApi,
    ) {
        /** Places matching [text]; only stops with [stopsOnly] (vias must be stops). */
        suspend fun search(
            text: String,
            stopsOnly: Boolean = false,
        ): Result<List<Place>> =
            runCatching {
                val matches = motis.geocode(text.trim(), appLanguage(), stopsOnly, CZECHIA_CENTRE)
                mergeDuplicateStops(matches)
            }

        private companion object {
            /** Until the app can ask for the location, rank Czech matches first. */
            const val CZECHIA_CENTRE = "49.8,15.5"
        }
    }

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

/** Whether the feed this stop comes from (the ID's `cz-…` tag) is from the stop's country. */
private fun MotisMatch.isDomestic(): Boolean {
    val country = country?.lowercase() ?: return false
    return id.startsWith("$country-") || id.startsWith("${country}_")
}

/** "Praha hl. n.", "Praha hl.n." and "Praha, hlavní nádraží" are one station. */
private fun stationKey(name: String): String =
    Normalizer
        .normalize(name.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("\\bhl\\.\\s*n\\.?"), "hlavni nadrazi")
        .replace(Regex("[^a-z0-9]"), "")

private fun closeTo(
    a: MotisMatch,
    b: MotisMatch,
): Boolean {
    val metresPerDegree = 111_000.0
    val dLat = abs(a.lat - b.lat) * metresPerDegree
    val dLon = abs(a.lon - b.lon) * metresPerDegree * cos(Math.toRadians(a.lat))
    return dLat * dLat + dLon * dLon < 1_000.0 * 1_000.0
}
