package cz.obehy.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SerializationTest {
    private val json = Json { allowSpecialFloatingPointValues = true }
    private val at = Instant.parse("2026-11-06T08:00:00Z")

    private fun call(
        name: String,
        time: TimePoint,
    ) = StopCall(name, "id-$name", time, time, "1", "2", position = LatLon(50.0, 14.0))

    @Test
    fun `a journey with both kinds of leg comes back whole`() {
        val start = TimePoint(at)
        val late = TimePoint(at.plusSeconds(600), at.plusSeconds(720), realtime = true)
        val journey =
            Itinerary(
                listOf(
                    Leg.Walk(call("A", start), call("B", late), start, late, 300, listOf(LatLon(50.0, 14.0))),
                    Leg.Transit(
                        Mode.Tram,
                        call("B", late),
                        call("C", late),
                        late,
                        late,
                        name = "22",
                        line = "22",
                        headsign = "C",
                        agency = null,
                        color = "FF0000",
                        textColor = null,
                        accessible = true,
                        cancelled = false,
                        tripId = "trip",
                        enrichment = LegEnrichment.Resolved("id", "2026-11-06"),
                        reservation = Reservation.Possible,
                        alerts = listOf(TripAlert("Works", null, severe = true)),
                    ),
                ),
                transfers = 0,
                refreshId = "refresh",
            )

        val back = json.decodeFromString<Itinerary>(json.encodeToString(journey))

        assertEquals(journey, back)
        assertEquals(journey.key, back.key)
    }

    @Test
    fun `my location keeps its unknown position`() {
        val query = JourneyQuery(from = Place.myLocation(), time = at, arriveBy = true)

        val back = json.decodeFromString<JourneyQuery>(json.encodeToString(query))

        assertEquals(PlaceKind.MyLocation, back.from?.kind)
        assertTrue(back.from!!.lat.isNaN())
        assertEquals(at, back.time)
    }
}
