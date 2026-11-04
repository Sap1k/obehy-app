package cz.obehy.core.data

import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.model.Suggestion
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/** Which end of a journey is being picked. */
enum class JourneyEnd { From, To }

/**
 * What the user probably wants now, from what they searched before. A search counts for more the
 * more recent it is, the closer it was to this time of day, on the same kind of day, and from
 * near where the phone is now. Nothing here leaves the phone.
 */
object JourneySuggester {
    /** The journeys worth offering now, likeliest first; none until the history says something. */
    fun suggestions(
        searches: List<PastSearch>,
        now: ZonedDateTime,
        fix: LatLon?,
    ): List<Suggestion> {
        val tallies = linkedMapOf<Pair<String, String>, Tally>()

        fun add(
            from: Place,
            to: Place,
            search: PastSearch,
            score: Double,
        ) {
            val tally = tallies.getOrPut(from.id to to.id) { Tally(from.general(), to.general()) }
            tally.score += score
            tally.searches += search.id
        }
        for (search in searches) {
            if (search.from.id == search.to.id) continue
            add(search.from, search.to, search, context(search, now) * here(search.from.position, fix))
            // The way back: you went to B, you are at B, so home again is likely. "My location"
            // has no name to go back to.
            val placesWithNames = search.from.kind != PlaceKind.MyLocation && search.to.kind != PlaceKind.MyLocation
            if (placesWithNames && fix != null && search.to.position.metresTo(fix) < NEAR_METRES) {
                add(search.to, search.from, search, context(search, now) * WAY_BACK * NEAR_FACTOR)
            }
        }
        if (fix != null) mergeIntoMyLocation(tallies, fix)
        return tallies.values
            .filter { it.score >= WORTH_OFFERING }
            .sortedByDescending { it.score }
            .take(MAX_SUGGESTIONS)
            .map { Suggestion(it.from, it.to, it.searches) }
    }

    /**
     * Where you are is the same journey whether you searched from the stop or from "My location":
     * the latter keeps it, as that's what starts your searches.
     */
    private fun mergeIntoMyLocation(
        tallies: MutableMap<Pair<String, String>, Tally>,
        fix: LatLon,
    ) {
        for ((key, tally) in tallies.toList()) {
            val fromHere = tallies[Place.myLocation().id to tally.to.id]
            if (tally.from.kind == PlaceKind.MyLocation || fromHere == null) continue
            if (tally.from.position.metresTo(fix) >= NEAR_METRES) continue
            fromHere.score += tally.score
            fromHere.searches += tally.searches
            tallies -= key
        }
    }

    /**
     * Past places for the [end] being picked, likeliest first. With [other], the journey's other
     * end, the places you went from or to there come first.
     */
    fun places(
        searches: List<PastSearch>,
        now: ZonedDateTime,
        fix: LatLon?,
        end: JourneyEnd,
        other: Place? = null,
        stopsOnly: Boolean = false,
    ): List<Place> {
        val tallies = linkedMapOf<String, Pair<Place, Double>>()

        fun add(
            place: Place,
            score: Double,
        ) {
            val usable = place.kind != PlaceKind.MyLocation && place.id != other?.id
            if (!usable || (stopsOnly && place.kind != PlaceKind.Stop)) return
            tallies[place.id] = place to (tallies[place.id]?.second ?: 0.0) + score
        }
        for (search in searches) {
            val score = context(search, now) * here(search.from.position, fix)
            val (mine, theirs) = if (end == JourneyEnd.To) search.to to search.from else search.from to search.to
            add(mine, if (theirs.id == other?.id) score * SAME_JOURNEY_FACTOR else score)
            add(theirs, score * OTHER_END_FACTOR)
        }
        return tallies.values
            .sortedByDescending { it.second }
            .take(MAX_PLACES)
            .map { it.first }
    }

    /** Everything about a search but where it started: how recent, and how like this moment. */
    private fun context(
        search: PastSearch,
        now: ZonedDateTime,
    ): Double {
        val then = search.at.atZone(now.zone)
        val age = Duration.between(then, now).toHours() / HOURS_PER_DAY
        val recency = 0.5.pow(age.coerceAtLeast(0.0) / HALF_LIFE_DAYS)
        val apart = abs(then.toLocalTime().toSecondOfDay() - now.toLocalTime().toSecondOfDay()) / SECONDS_PER_HOUR
        val hours = min(apart, HOURS_PER_DAY - apart)
        val timeOfDay = 1 + 2 * exp(-hours * hours / (2 * TIME_SPREAD_HOURS * TIME_SPREAD_HOURS))
        val dayType = if (now.isWeekend() == then.isWeekend()) SAME_DAY_TYPE_FACTOR else 1.0
        return recency * timeOfDay * dayType
    }

    private fun here(
        origin: LatLon,
        fix: LatLon?,
    ): Double {
        val metres = fix?.metresTo(origin) ?: return 1.0
        return when {
            metres < NEAR_METRES -> NEAR_FACTOR
            metres > FAR_METRES -> FAR_FACTOR
            else -> 1.0
        }
    }

    private fun ZonedDateTime.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    /** "My location" as a journey's end: not where it happened to be that day. */
    private fun Place.general(): Place = if (kind == PlaceKind.MyLocation) Place.myLocation() else this

    private class Tally(
        val from: Place,
        val to: Place,
        var score: Double = 0.0,
        val searches: MutableSet<Long> = mutableSetOf(),
    )

    private const val HALF_LIFE_DAYS = 30.0
    private const val HOURS_PER_DAY = 24.0
    private const val SECONDS_PER_HOUR = 3_600.0
    private const val TIME_SPREAD_HOURS = 1.5
    private const val SAME_DAY_TYPE_FACTOR = 1.5
    private const val NEAR_METRES = 1_000.0
    private const val FAR_METRES = 5_000.0
    private const val NEAR_FACTOR = 3.0
    private const val FAR_FACTOR = 0.3
    private const val WAY_BACK = 0.5
    private const val WORTH_OFFERING = 1.0
    private const val MAX_SUGGESTIONS = 3
    private const val SAME_JOURNEY_FACTOR = 10.0
    private const val OTHER_END_FACTOR = 0.5
    private const val MAX_PLACES = 5
}
