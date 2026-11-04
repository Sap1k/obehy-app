package cz.obehy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import cz.obehy.core.model.RoutingProvider
import cz.obehy.core.model.Suggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SearchHistoryRepositoryTest {
    private val store = FakeStore()
    private val settings = SettingsRepository(MemoryPreferences())
    private val history = SearchHistoryRepository(store, settings)
    private val at = Instant.parse("2026-11-04T08:00:00Z")
    private val a = Place("a", "A", null, PlaceKind.Stop, 50.0, 14.0)
    private val b = Place("b", "B", null, PlaceKind.Stop, 50.1, 14.1)

    @Test
    fun `searching again straight away counts once`() =
        runTest {
            history.record(a, b, at)
            history.record(a, b, at + Duration.ofMinutes(9))
            assertEquals(1, history.searches.first().size)

            history.record(a, b, at + Duration.ofMinutes(11))
            assertEquals(2, history.searches.first().size)
        }

    @Test
    fun `the way back is a journey of its own`() =
        runTest {
            history.record(a, b, at)
            history.record(b, a, at + Duration.ofMinutes(1))

            assertEquals(2, history.searches.first().size)
        }

    @Test
    fun `each provider has its own history, and it comes back`() =
        runTest {
            settings.setRoutingProvider(RoutingProvider.Transitous)
            history.record(a, b, at)

            settings.setRoutingProvider(RoutingProvider.entries.first { it != RoutingProvider.Transitous })
            assertEquals(emptyList<PastSearch>(), history.searches.first())

            settings.setRoutingProvider(RoutingProvider.Transitous)
            assertEquals(1, history.searches.first().size)
        }

    @Test
    fun `forgetting a place forgets the journeys through it`() =
        runTest {
            val c = b.copy(id = "c")
            history.record(a, b, at)
            history.record(c, a, at)
            history.record(b, c, at)

            history.forget(a)

            assertEquals(listOf("b"), history.searches.first().map { it.from.id })
        }

    @Test
    fun `forgetting a suggestion removes the searches behind it`() =
        runTest {
            history.record(a, b, at)
            val id =
                history.searches
                    .first()
                    .single()
                    .id

            history.forget(Suggestion(a, b, setOf(id)))

            assertEquals(emptyList<PastSearch>(), history.searches.first())
        }

    @Test
    fun `recording trims to the limit`() =
        runTest {
            history.record(a, b, at)

            assertEquals(at - Duration.ofDays(365) to 500, store.trimmed)
        }
}

private class FakeStore : SearchHistoryStore {
    private val rows = MutableStateFlow<List<Pair<String, PastSearch>>>(emptyList())
    private var nextId = 1L
    var trimmed: Pair<Instant, Int>? = null

    override fun searches(provider: String): Flow<List<PastSearch>> =
        rows.map { all -> all.filter { it.first == provider }.map { it.second } }

    override suspend fun add(
        provider: String,
        search: PastSearch,
    ) {
        rows.value = listOf(provider to search.copy(id = nextId++)) + rows.value
    }

    override suspend fun trim(
        oldest: Instant,
        keep: Int,
    ) {
        trimmed = oldest to keep
    }

    override suspend fun delete(ids: Set<Long>) {
        rows.value = rows.value.filter { it.second.id !in ids }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

private class MemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: StateFlow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
