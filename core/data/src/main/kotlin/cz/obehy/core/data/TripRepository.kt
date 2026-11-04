package cz.obehy.core.data

import cz.obehy.core.model.Composition
import cz.obehy.core.model.Leg
import cz.obehy.core.model.StopCall
import cz.obehy.core.model.TripDetail
import cz.obehy.core.model.TripInfo
import cz.obehy.core.model.TripStop
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.toTrip
import cz.obehy.core.network.obehy.EnrichLegRequest
import cz.obehy.core.network.obehy.EnrichOutcome
import cz.obehy.core.network.obehy.EnrichedLeg
import cz.obehy.core.network.obehy.ObehyApi
import cz.obehy.core.network.obehy.addedTo
import cz.obehy.core.network.obehy.toDomain
import cz.obehy.core.network.obehy.toTripInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** A ride on a journey: the trip, and the MOTIS stops it's ridden between. */
data class TripRequest(
    val motisTripId: String,
    val fromStopId: String?,
    val toStopId: String?,
)

/** The trip of this ride, as asked of a repository. */
val Leg.Transit.tripRequest: TripRequest? get() = tripId?.let { TripRequest(it, from.stopId, to.stopId) }

/**
 * Whole trips, in two steps. The plain trip comes from MOTIS, which answers for every trip on every
 * server; it's fetched ahead for the journey being looked at, so a trip opens with it in place.
 * Oběhy's details come on top once the trip is opened, in the modes with Oběhy, wherever it knows
 * the trip. The mode without Oběhy never asks it anything.
 */
@Singleton
class TripRepository
    @Inject
    constructor(
        private val motis: MotisApi,
        private val obehy: ObehyApi,
        private val settings: SettingsRepository,
        @param:Named("computation") private val computation: CoroutineDispatcher,
    ) {
        private class Fetched(
            val trip: TripDetail,
            val at: Long,
        )

        private val fetched =
            object : LinkedHashMap<TripRequest, Fetched>(KEPT, 1f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<TripRequest, Fetched>) = size > KEPT
            }

        /** The plain trip of [request], if it's already here and recent. */
        fun fetchedAhead(request: TripRequest): TripDetail? =
            synchronized(fetched) { fetched[request] }
                ?.takeIf { System.currentTimeMillis() - it.at < KEPT_MS }
                ?.trip

        /** The plain trips of [rides] fetched ahead, leaving out those that couldn't be. */
        suspend fun fetchAhead(rides: List<Leg.Transit>): List<TripDetail> =
            coroutineScope {
                rides
                    .mapNotNull { it.tripRequest }
                    .map { async { plain(it).getOrNull() } }
                    .awaitAll()
                    .filterNotNull()
            }

        /** The trip of [request] as MOTIS has it. */
        suspend fun plain(request: TripRequest): Result<TripDetail> =
            fetchedAhead(request)?.let { Result.success(it) }
                ?: runCatching {
                    withContext(computation) {
                        val ride = ride(request.motisTripId)
                        val stops = TripDetail.stopsOf(ride)
                        TripDetail(ride, stops, stops.span(request.fromStopId, request.toStopId))
                    }
                }.onSuccess { trip ->
                    synchronized(fetched) { fetched[request] = Fetched(trip, System.currentTimeMillis()) }
                }

        /** [plain] with what only Oběhy knows, or as it is if that's nothing: it can do without. */
        suspend fun withDetails(
            plain: TripDetail,
            request: TripRequest,
        ): TripDetail =
            if (settings.current().mode?.usesObehy == true) {
                runCatching { withContext(computation) { withObehy(plain, request) } }.getOrNull() ?: plain
            } else {
                plain
            }

        /** [trip] with the latest times from MOTIS and, if it had any, details from Oběhy. */
        suspend fun refresh(
            trip: TripDetail,
            motisTripId: String,
        ): Result<TripDetail> =
            runCatching {
                withContext(computation) {
                    val ride = ride(motisTripId)
                    val stops = TripDetail.stopsOf(ride)
                    val info = trip.info
                    val fresh = info?.let { runCatching { obehy.trip(it.tripId, it.serviceDate) }.getOrNull() }
                    trip.copy(
                        ride = trip.ride.copy(cancelled = ride.cancelled, alerts = ride.alerts),
                        stops = fresh?.addedTo(stops) ?: stops.map { it.keepingFrom(trip) },
                        info =
                            fresh?.toTripInfo(appLanguage())?.copy(
                                boardingLocationId = info.boardingLocationId,
                                boardingSequence = info.boardingSequence,
                            ) ?: info,
                    )
                }
            }

        /** This stop with what [trip]'s stop of the same place already had from Oběhy. */
        private fun TripStop.keepingFrom(trip: TripDetail): TripStop {
            val old = trip.stops.firstOrNull { it.call.stopId == call.stopId } ?: return this
            return copy(sequence = old.sequence, flags = flags + old.flags)
        }

        /** What's known of the train of [info] from where the journey boards; may still be on its way. */
        suspend fun composition(info: TripInfo): Result<Composition> =
            runCatching {
                withContext(computation) {
                    obehy
                        .composition(info.tripId, info.serviceDate, info.boardingLocationId)
                        .toDomain(appLanguage())
                }
            }

        private suspend fun ride(motisTripId: String): Leg.Transit =
            requireNotNull(motis.trip(motisTripId, appLanguage()).toTrip(motisTripId)) { "no such trip" }

        private suspend fun withObehy(
            plain: TripDetail,
            request: TripRequest,
        ): TripDetail? {
            val leg = resolved(request) ?: return null
            val ref = leg.trip ?: return null
            val trip = obehy.trip(ref.tripId, ref.serviceDate)
            val stops = trip.addedTo(plain.stops)
            val boarding = leg.calls.firstOrNull()?.takeIf { leg.legCalls }
            return plain.copy(stops = stops, info = trip.toTripInfo(appLanguage(), boarding))
        }

        private suspend fun resolved(request: TripRequest): EnrichedLeg? =
            obehy
                .enrich(listOf(EnrichLegRequest(request.motisTripId, request.fromStopId, request.toStopId)))
                .legs
                .firstOrNull()
                ?.takeIf { it.outcome == EnrichOutcome.Resolved }

        private fun List<TripStop>.span(
            fromStopId: String?,
            toStopId: String?,
        ): IntRange? {
            val first = indexOfFirst { it.call.isStop(fromStopId) }
            val last = indexOfLast { it.call.isStop(toStopId) }
            return (first..last).takeIf { first in 0 until last }
        }

        private fun StopCall.isStop(id: String?) = id != null && stopId == id

        private companion object {
            /** Plain trips kept: a few journeys' worth. */
            const val KEPT = 24

            /** Long enough to look at a journey; its live times come when the trip opens anyway. */
            const val KEPT_MS = 10 * 60 * 1_000L
        }
    }
