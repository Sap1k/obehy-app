package cz.obehy.core.data

import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZonedDateTime

class JourneySuggesterTest {
    // A Wednesday morning.
    private val now = ZonedDateTime.parse("2026-11-04T08:00:00+01:00[Europe/Prague]")

    private val home = stop("home", 50.00, 14.00)
    private val work = stop("work", 50.10, 14.10)
    private val gym = stop("gym", 50.20, 14.20)
    private val elsewhere = LatLon(49.0, 16.0)

    private fun stop(
        id: String,
        lat: Double,
        lon: Double,
    ) = Place(id, id, null, PlaceKind.Stop, lat, lon)

    private var nextId = 1L

    private fun search(
        from: Place,
        to: Place,
        ago: Duration,
    ) = PastSearch(nextId++, now.minus(ago).toInstant(), from, to)

    private fun days(count: Long) = Duration.ofDays(count)

    private fun suggested(
        searches: List<PastSearch>,
        fix: LatLon? = null,
    ) = JourneySuggester.suggestions(searches, now, fix).map { "${it.from.id}>${it.to.id}" }

    @Test
    fun `no history, no suggestions`() {
        assertEquals(emptyList<String>(), suggested(emptyList()))
    }

    @Test
    fun `a journey made at this time of day beats one made at another`() {
        val searches =
            listOf(
                search(home, gym, days(1) + Duration.ofHours(10)),
                search(home, work, days(2)),
            )

        assertEquals(listOf("home>work", "home>gym"), suggested(searches))
    }

    @Test
    fun `a recent journey beats the same habit long ago`() {
        val searches = listOf(search(home, gym, days(90)), search(home, work, days(1)))

        assertEquals(listOf("home>work", "home>gym").first(), suggested(searches).first())
    }

    @Test
    fun `what you do at weekends counts for less on a weekday`() {
        val saturday = Duration.between(ZonedDateTime.parse("2026-10-31T08:00:00+01:00[Europe/Prague]"), now)
        val tuesday = Duration.between(ZonedDateTime.parse("2026-10-27T08:00:00+01:00[Europe/Prague]"), now)
        val searches = listOf(search(home, gym, saturday), search(home, work, tuesday))

        assertEquals("home>work", suggested(searches).first())
    }

    @Test
    fun `an old one-off is not worth offering`() {
        assertEquals(emptyList<String>(), suggested(listOf(search(home, gym, days(120)))))
    }

    @Test
    fun `being far from a journey's start hides it, being there lifts it`() {
        val searches = listOf(search(home, gym, days(1)), search(work, gym, days(20)))

        assertEquals(emptyList<String>(), suggested(listOf(search(home, gym, days(40))), elsewhere))
        assertEquals("work>gym", suggested(searches, work.position).first())
    }

    @Test
    fun `at the place you went to, the way back is offered, and only there`() {
        val searches = listOf(search(home, work, Duration.ofHours(10)))

        assertTrue("work>home" in suggested(searches, work.position))
        assertTrue("work>home" !in suggested(searches, home.position))
        assertTrue("work>home" !in suggested(searches))
    }

    @Test
    fun `there is no way back to a my location with no name`() {
        val searches = listOf(search(Place.myLocation(home.position), work, days(1)))

        val offered = suggested(searches, work.position)

        assertTrue(offered.none { it.startsWith("work>") })
    }

    @Test
    fun `my location journeys are one journey, wherever the phone was`() {
        val searches =
            listOf(
                search(Place.myLocation(home.position), work, days(1)),
                search(Place.myLocation(LatLon(50.001, 14.001)), work, days(2)),
            )

        val suggestion = JourneySuggester.suggestions(searches, now, null).single()

        assertEquals(PlaceKind.MyLocation, suggestion.from.kind)
        assertTrue(suggestion.from.lat.isNaN())
        assertEquals(2, suggestion.searches.size)
    }

    @Test
    fun `the stop you are at folds into my location, keeping its searches`() {
        val searches =
            listOf(
                search(Place.myLocation(home.position), work, days(1)),
                search(home, work, days(2)),
            )

        val suggestion = JourneySuggester.suggestions(searches, now, home.position).single()

        assertEquals(PlaceKind.MyLocation, suggestion.from.kind)
        assertEquals(searches.map { it.id }.toSet(), suggestion.searches)
    }

    @Test
    fun `at most three suggestions`() {
        val others = (1..5).map { stop("other$it", 50.0 + it, 14.0) }
        val searches = others.map { search(home, it, days(1)) }

        assertEquals(3, suggested(searches).size)
    }

    @Test
    fun `places you left from the chosen start come first`() {
        val searches =
            listOf(
                search(home, gym, days(1)),
                search(home, gym, days(2)),
                search(work, home, days(30)),
                search(work, stop("cafe", 50.3, 14.3), days(30)),
            )

        val places = JourneySuggester.places(searches, now, null, JourneyEnd.To, other = work)

        val order = places.map { it.id }
        assertTrue(order.toString(), order.indexOf("cafe") < order.indexOf("gym"))
        assertTrue("work" !in order)
    }

    @Test
    fun `a via only offers stops, and my location is never a recent place`() {
        val address = stop("flat", 50.4, 14.4).copy(kind = PlaceKind.Address)
        val searches =
            listOf(
                search(Place.myLocation(home.position), address, days(1)),
                search(home, work, days(2)),
            )

        val places = JourneySuggester.places(searches, now, null, JourneyEnd.To, stopsOnly = true)

        assertEquals(setOf("home", "work"), places.map { it.id }.toSet())
    }
}
