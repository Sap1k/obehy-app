package cz.obehy.core.data

import cz.obehy.core.model.Itinerary
import cz.obehy.core.model.JourneyPage
import cz.obehy.core.model.JourneyQuery
import cz.obehy.core.model.SearchOptions
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.motisPlanRequest
import cz.obehy.core.network.motis.toDomain
import cz.obehy.core.network.motis.toJourneyPage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** Journey search through the current routing provider. */
@Singleton
class JourneyRepository
    @Inject
    constructor(
        private val motis: MotisApi,
    ) {
        /**
         * Itineraries for [query] (from and to set) with [options]. [cursor] pages to earlier or
         * later results of the same search.
         */
        suspend fun search(
            query: JourneyQuery,
            options: SearchOptions,
            cursor: String? = null,
        ): Result<JourneyPage> =
            runCatching {
                motis.plan(motisPlanRequest(query, options, appLanguage(), cursor)).toJourneyPage()
            }

        /**
         * The latest times of [journeys], by [Itinerary.key], each looked up again (in parallel).
         * A journey whose answer isn't the same journey ([Itinerary.isRefreshedBy]) or that can't
         * be looked up is left out, keeping what it had; the refresh fails only when none worked.
         */
        suspend fun refresh(
            journeys: List<Itinerary>,
            options: SearchOptions,
        ): Result<Map<String, Itinerary>> =
            runCatching {
                val language = appLanguage()
                val limit = Semaphore(PARALLEL_REFRESHES)
                val answers =
                    coroutineScope {
                        journeys
                            .map { journey ->
                                async {
                                    journey to
                                        runCatching {
                                            val id = requireNotNull(journey.refreshId) { "no id to refresh with" }
                                            limit
                                                .withPermit {
                                                    motis.refreshItinerary(
                                                        id,
                                                        options.routedTransfers,
                                                        language,
                                                    )
                                                }.toDomain()
                                        }
                                }
                            }.awaitAll()
                    }
                val fresh =
                    answers.mapNotNull { (old, new) ->
                        new.getOrNull()?.takeIf(old::isRefreshedBy)?.let {
                            old.key to
                                it
                        }
                    }
                if (fresh.isEmpty()) {
                    throw answers.firstNotNullOfOrNull { it.second.exceptionOrNull() }
                        ?: NotRefreshable()
                }
                fresh.toMap()
            }

        /** Every journey came back as something else. */
        class NotRefreshable : Exception("the refresh answered with different journeys")

        private companion object {
            /** Journeys looked up at once when refreshing. */
            const val PARALLEL_REFRESHES = 6
        }
    }
