package cz.obehy.core.data

import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.network.MotisJson
import cz.obehy.core.network.apiClient
import cz.obehy.core.network.motis.MotisApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyRepositoryTest {
    private val asked = mutableListOf<String>()

    private fun repository(status: (String) -> HttpStatusCode): JourneyRepository {
        val engine =
            MockEngine { request ->
                val from = request.url.parameters["fromPlace"]!!
                asked += from
                val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                respond("""{"itineraries":[]}""", status(from), json)
            }
        return JourneyRepository(
            MotisApi(apiClient(engine, MotisJson, "test")) { "https://motis.test/" },
            Dispatchers.Default,
        )
    }

    private val gone = Place("gone", "Old stop", null, PlaceKind.Stop, 50.0, 14.0)
    private val there = Place("here", "Stop", null, PlaceKind.Stop, 50.1, 14.1)

    @Test
    fun `a stop the timetable no longer knows is searched by its coordinates`() =
        runTest {
            val repository = repository { if (it == "gone") HttpStatusCode.NotFound else HttpStatusCode.OK }

            val page = repository.search(JourneyQuery(gone, there), SearchOptions())

            assertTrue(page.isSuccess)
            assertEquals(listOf("gone", "50.0,14.0"), asked)
        }

    @Test
    fun `a search by coordinates alone does not try again`() =
        runTest {
            val repository = repository { HttpStatusCode.NotFound }
            val address = gone.copy(kind = PlaceKind.Address)

            val page = repository.search(JourneyQuery(address, address), SearchOptions())

            assertTrue(page.isFailure)
            assertEquals(1, asked.size)
        }

    @Test
    fun `a server failure is not mistaken for an unknown stop`() =
        runTest {
            val repository = repository { HttpStatusCode.ServiceUnavailable }

            assertTrue(repository.search(JourneyQuery(gone, there), SearchOptions()).isFailure)
            assertEquals(1, asked.size)
        }
}
