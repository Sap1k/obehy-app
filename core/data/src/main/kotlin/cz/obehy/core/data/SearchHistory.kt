package cz.obehy.core.data

import cz.obehy.core.model.Place
import cz.obehy.core.model.Suggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A journey searched for. "My location" keeps where the phone was at the time. */
data class PastSearch(
    val id: Long,
    val at: Instant,
    val from: Place,
    val to: Place,
)

/** Where past searches are kept, per routing provider: stop IDs mean nothing to another one. */
interface SearchHistoryStore {
    /** The provider's searches, newest first. */
    fun searches(provider: String): Flow<List<PastSearch>>

    suspend fun add(
        provider: String,
        search: PastSearch,
    )

    /** Drops what is older than [oldest] and everything past the latest [keep]. */
    suspend fun trim(
        oldest: Instant,
        keep: Int,
    )

    suspend fun delete(ids: Set<Long>)

    suspend fun clear()
}

/**
 * The journeys searched for, on this phone only. Switching server shows that server's history;
 * switching back brings the first one back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SearchHistoryRepository
    @Inject
    constructor(
        private val store: SearchHistoryStore,
        private val settings: SettingsRepository,
    ) {
        val searches: Flow<List<PastSearch>> =
            settings.settings
                .map { it.routingServer }
                .distinctUntilChanged()
                .flatMapLatest { store.searches(it) }

        /** Remembers a search that found journeys; changing an option and searching again doesn't count twice. */
        suspend fun record(
            from: Place,
            to: Place,
            at: Instant,
        ) {
            val latest = searches.first().firstOrNull { it.from.id == from.id && it.to.id == to.id }
            if (latest != null && Duration.between(latest.at, at) < SAME_SEARCH) return
            store.add(settings.current().routingServer, PastSearch(0, at, from, to))
            store.trim(at - KEPT_FOR, KEPT_SEARCHES)
        }

        /** Stops offering this journey, the searches behind it gone. */
        suspend fun forget(suggestion: Suggestion) = store.delete(suggestion.searches)

        /** Forgets every search from or to [place]. */
        suspend fun forget(place: Place) {
            val ids = searches.first().filter { it.from.id == place.id || it.to.id == place.id }
            store.delete(ids.mapTo(mutableSetOf()) { it.id })
        }

        suspend fun clear() = store.clear()

        private companion object {
            val SAME_SEARCH: Duration = Duration.ofMinutes(10)
            val KEPT_FOR: Duration = Duration.ofDays(365)
            const val KEPT_SEARCHES = 500
        }
    }
