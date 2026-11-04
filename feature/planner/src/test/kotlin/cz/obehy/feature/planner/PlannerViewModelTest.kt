package cz.obehy.feature.planner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import cz.obehy.core.data.JourneyRepository
import cz.obehy.core.data.LocationRepository
import cz.obehy.core.data.PlaceRepository
import cz.obehy.core.data.SettingsRepository
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.model.TransportType
import cz.obehy.core.network.MotisJson
import cz.obehy.core.network.apiClient
import cz.obehy.core.network.motis.MotisApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val requests = mutableListOf<HttpRequestData>()
    private var failing = false

    /** Refreshes answer with the ride stripped of its trip, as MOTIS sometimes does. */
    private var stripped = false
    private var clock = Instant.parse("2026-11-03T08:00:00Z")
    private val location = FakeLocation()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): Pair<PlannerViewModel, SettingsRepository> {
        // The engine runs on the test's clock, so advanceUntilIdle() waits for answers too.
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = this@PlannerViewModelTest.dispatcher
                    addHandler { request ->
                        requests += request
                        val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        when {
                            failing -> {
                                respond("{}", HttpStatusCode.ServiceUnavailable)
                            }

                            request.url.encodedPath.endsWith("map/stops") -> {
                                respond(MAP_STOPS, HttpStatusCode.OK, json)
                            }

                            request.url.encodedPath.endsWith("refresh-itinerary") -> {
                                val tripId = request.url.parameters["itineraryId"]!!
                                respond(
                                    itinerary(tripId, minute = 10, late = 2, stripped = stripped),
                                    HttpStatusCode.OK,
                                    json,
                                )
                            }

                            else -> {
                                val page = request.url.parameters["pageCursor"] ?: "first"
                                respond(plan(page), HttpStatusCode.OK, json)
                            }
                        }
                    }
                },
            )
        val motis = MotisApi(apiClient(engine, MotisJson, "test")) { "https://motis.test/" }
        val settings = SettingsRepository(MemoryStore())
        val viewModel = PlannerViewModel(settings, PlaceRepository(motis), JourneyRepository(motis), location)
        viewModel.now = { clock }
        backgroundScope.launch(dispatcher) { viewModel.state.collect {} }
        return viewModel to settings
    }

    private fun stop(id: String) = Place(id, id, null, PlaceKind.Stop, 0.0, 0.0)

    private fun PlannerViewModel.fill(vararg vias: String) {
        act(PlannerAction.Pick(PlaceTarget.From))
        act(PlannerAction.Choose(stop("A")))
        vias.forEachIndexed { i, via ->
            act(PlannerAction.Pick(PlaceTarget.Via(i)))
            act(PlannerAction.Choose(stop(via)))
        }
        act(PlannerAction.Pick(PlaceTarget.To))
        act(PlannerAction.Choose(stop("D")))
    }

    private fun allowLocation(fix: LatLon? = HERE) {
        location.fix = fix
        location.allowed.value = true
    }

    @Test
    fun `with the location allowed a search starts from my location, and a new search does too`() =
        runTest(dispatcher) {
            allowLocation()
            val (vm, _) = viewModel()
            advanceUntilIdle()
            assertEquals(
                PlaceKind.MyLocation,
                vm.state.value.query.from
                    ?.kind,
            )
            assertFalse(vm.state.value.canReset)

            vm.act(PlannerAction.Pick(PlaceTarget.From))
            vm.act(PlannerAction.Choose(stop("A")))
            advanceUntilIdle()
            assertTrue(vm.state.value.canReset)

            vm.act(PlannerAction.Reset)
            advanceUntilIdle()
            assertEquals(
                PlaceKind.MyLocation,
                vm.state.value.query.from
                    ?.kind,
            )
        }

    @Test
    fun `without the location nothing is filled in`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            advanceUntilIdle()

            assertEquals(null, vm.state.value.query.from)
        }

    @Test
    fun `my location swapped to the destination is not filled in again as the start`() =
        runTest(dispatcher) {
            allowLocation()
            val (vm, _) = viewModel()
            advanceUntilIdle()

            vm.act(PlannerAction.Swap)
            vm.act(PlannerAction.Reset)
            vm.act(PlannerAction.Swap)
            location.allowed.value = false
            location.allowed.value = true
            advanceUntilIdle()

            assertEquals(null, vm.state.value.query.from)
            assertEquals(
                PlaceKind.MyLocation,
                vm.state.value.query.to
                    ?.kind,
            )
        }

    @Test
    fun `a search from my location sends a fresh fix, and later pages keep that start`() =
        runTest(dispatcher) {
            allowLocation()
            val (vm, _) = viewModel()
            advanceUntilIdle()
            vm.act(PlannerAction.Pick(PlaceTarget.To))
            vm.act(PlannerAction.Choose(stop("D")))

            vm.act(PlannerAction.Search)
            advanceUntilIdle()
            location.fix = LatLon(1.0, 2.0)
            vm.act(PlannerAction.Later)
            advanceUntilIdle()

            val sent = requests.filter { it.url.parameters["fromPlace"] != null }
            assertEquals(listOf("50.08,14.43", "50.08,14.43"), sent.map { it.url.parameters["fromPlace"] })
        }

    @Test
    fun `no fix says so instead of searching`() =
        runTest(dispatcher) {
            allowLocation(fix = null)
            val (vm, _) = viewModel()
            advanceUntilIdle()
            vm.act(PlannerAction.Pick(PlaceTarget.To))
            vm.act(PlannerAction.Choose(stop("D")))

            vm.act(PlannerAction.Search)
            advanceUntilIdle()

            assertEquals(Results.NoLocation, vm.state.value.results)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `the picker shows nearby stops, or asks for what is missing`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.act(PlannerAction.Pick(PlaceTarget.From))
            advanceUntilIdle()
            assertEquals(
                NearbyStatus.NeedsPermission,
                vm.state.value.placeSearch
                    ?.nearbyStatus,
            )

            allowLocation()
            vm.act(PlannerAction.CheckLocation)
            advanceUntilIdle()
            val search = vm.state.value.placeSearch
            assertEquals(NearbyStatus.Ready, search?.nearbyStatus)
            assertEquals(listOf("Muzeum"), search?.nearby?.map { it.place.name })

            vm.act(PlannerAction.ClosePlaceSearch)
            location.enabled = false
            vm.act(PlannerAction.Pick(PlaceTarget.From))
            advanceUntilIdle()
            assertEquals(
                NearbyStatus.Off,
                vm.state.value.placeSearch
                    ?.nearbyStatus,
            )
        }

    @Test
    fun `swapping reverses the route without searching before the first search`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.fill("B", "C")

            vm.act(PlannerAction.Swap)
            advanceUntilIdle()

            val query = vm.state.value.query
            assertEquals(
                listOf("D", "C", "B", "A"),
                listOfNotNull(query.from).map { it.id } + query.vias.map { it.id } +
                    listOfNotNull(query.to).map { it.id },
            )
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `a search loads journeys, later ones are added and the vias are sent`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.fill("B")

            vm.act(PlannerAction.Search)
            advanceUntilIdle()
            vm.act(PlannerAction.Later)
            advanceUntilIdle()

            val results = vm.state.value.results as Results.Loaded
            assertEquals(2, results.itineraries.size)
            assertEquals("next-next-first", results.laterCursor)
            assertEquals("B", requests.first().url.parameters["via"])
            assertEquals("next-first", requests.last().url.parameters["pageCursor"])
        }

    @Test
    fun `options are remembered and journeys on screen are searched again with them`() =
        runTest(dispatcher) {
            val (vm, settings) = viewModel()
            vm.fill()
            vm.act(PlannerAction.Search)
            advanceUntilIdle()

            val options = SearchOptions(lowFloor = true, types = setOf(TransportType.Tram))
            vm.act(PlannerAction.SetOptions(options))
            advanceUntilIdle()

            assertEquals(options, settings.searchOptions.first())
            assertEquals(options, vm.state.value.options)
            assertEquals(2, requests.size)
            assertEquals("WHEELCHAIR", requests.last().url.parameters["pedestrianProfile"])
        }

    @Test
    fun `a failed search says so`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.fill()
            failing = true

            vm.act(PlannerAction.Search)
            advanceUntilIdle()

            assertEquals(Results.Failed, vm.state.value.results)
        }

    @Test
    fun `a new search clears the places and results but keeps the options`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.act(PlannerAction.SetOptions(SearchOptions(lowFloor = true)))
            vm.fill("B")
            vm.act(PlannerAction.Search)
            advanceUntilIdle()

            vm.act(PlannerAction.Reset)
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(JourneyQuery(), state.query)
            assertEquals(Results.Idle, state.results)
            assertTrue(state.options.lowFloor)
            assertTrue(!state.canReset)
        }

    @Test
    fun `refreshing the open journey brings fresh times to it and to its card`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.fill()
            vm.act(PlannerAction.Search)
            advanceUntilIdle()
            val picked = (vm.state.value.results as Results.Loaded).itineraries.first()
            vm.act(PlannerAction.Select(picked))
            clock = clock.plusSeconds(90)

            vm.act(PlannerAction.Refresh)
            advanceUntilIdle()

            val open = vm.state.value.open!!
            assertEquals(2L, open.itinerary.start.delayMinutes)
            assertEquals(clock, open.fetchedAt)
            assertEquals(picked.refreshId, requests.last().url.parameters["itineraryId"])
            val card = (vm.state.value.results as Results.Loaded).itineraries.first()
            assertEquals(2L, card.start.delayMinutes)
        }

    @Test
    fun `a failed refresh keeps the journey and says it is not fresh`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.fill()
            vm.act(PlannerAction.Search)
            advanceUntilIdle()
            val picked = (vm.state.value.results as Results.Loaded).itineraries.first()
            vm.act(PlannerAction.Select(picked))
            failing = true

            vm.act(PlannerAction.Refresh)
            advanceUntilIdle()

            val open = vm.state.value.open!!
            assertEquals(picked, open.itinerary)
            assertTrue(open.failed)
            assertEquals(Instant.parse("2026-11-03T08:00:00Z"), open.fetchedAt)
        }

    @Test
    fun `a refresh that comes back stripped of its trip is not taken`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.fill()
            vm.act(PlannerAction.Search)
            advanceUntilIdle()
            val picked = (vm.state.value.results as Results.Loaded).itineraries.first()
            vm.act(PlannerAction.Select(picked))
            stripped = true

            vm.act(PlannerAction.Refresh)
            advanceUntilIdle()

            val open = vm.state.value.open!!
            assertEquals(picked, open.itinerary)
            assertTrue(open.failed)
        }

    @Test
    fun `refreshing the results looks each journey up again and moves their age`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.fill()
            vm.act(PlannerAction.Search)
            advanceUntilIdle()
            clock = clock.plusSeconds(300)

            vm.act(PlannerAction.RefreshResults)
            advanceUntilIdle()

            val results = vm.state.value.results as Results.Loaded
            assertEquals(clock, results.fetchedAt)
            assertTrue(!results.refreshing)
            assertEquals(
                2L,
                results.itineraries
                    .single()
                    .start.delayMinutes,
            )
            assertTrue(
                requests
                    .last()
                    .url.encodedPath
                    .endsWith("refresh-itinerary"),
            )
        }

    private fun plan(page: String): String =
        """{"itineraries":[${itinerary("trip-$page", minute = if (page == "first") 10 else 20)}],
            "previousPageCursor":"prev-$page","nextPageCursor":"next-$page"}"""

    /** A journey on one tram, trip [tripId], at 08:[minute], [late] minutes late by realtime data. */
    private fun itinerary(
        tripId: String,
        minute: Int,
        late: Int = 0,
        stripped: Boolean = false,
    ): String {
        val start = "2026-11-03T08:$minute:00Z"
        val end = "2026-11-03T08:${minute + 30}:00Z"
        val actual = "2026-11-03T08:${minute + late}:00Z"
        return """
            {"id":"$tripId","duration":1800,"startTime":"$actual","endTime":"$end","transfers":0,"legs":[
              {"mode":"TRAM","from":{"name":"A","lat":0,"lon":0},"to":{"name":"D","lat":0,"lon":0},"duration":1800,
               "startTime":"$actual","endTime":"$end","scheduledStartTime":"$start","scheduledEndTime":"$end",
               "realTime":${late > 0},${if (stripped) "" else "\"tripId\":\"$tripId\","}"displayName":"22","routeShortName":"22"}]}
            """.trimIndent()
    }
}

/** DataStore in memory: writes land before the test's clock moves on. */
private val HERE = LatLon(50.08, 14.43)

private const val MAP_STOPS =
    """[{"name":"Muzeum","stopId":"cz-PID_U400Z1P","lat":50.0795,"lon":14.4305,"modes":["SUBWAY"]}]"""

private class FakeLocation : LocationRepository {
    var fix: LatLon? = null
    override val allowed = MutableStateFlow(false)
    override var enabled = true

    override fun recheck() = Unit

    override fun lastKnown(): LatLon? = fix

    override suspend fun current(): LatLon? = fix
}

private class MemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: StateFlow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also {
            state.value =
                it
        }
}
