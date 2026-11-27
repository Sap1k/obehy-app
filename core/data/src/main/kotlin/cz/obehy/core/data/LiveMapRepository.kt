package cz.obehy.core.data

import cz.obehy.core.model.BoardSide
import cz.obehy.core.model.LatLon
import cz.obehy.core.model.LiveMapSource
import cz.obehy.core.model.LiveStop
import cz.obehy.core.model.LiveVehicle
import cz.obehy.core.model.MapBounds
import cz.obehy.core.model.RailvizSegment
import cz.obehy.core.model.StopBoard
import cz.obehy.core.model.VehicleFacts
import cz.obehy.core.model.VehicleHit
import cz.obehy.core.network.motis.MotisApi
import cz.obehy.core.network.motis.toDepartures
import cz.obehy.core.network.motis.toLiveStops
import cz.obehy.core.network.motis.toSegment
import cz.obehy.core.network.obehy.ObehyApi
import cz.obehy.core.network.obehy.toBoard
import cz.obehy.core.network.obehy.toHits
import cz.obehy.core.network.obehy.toLiveStops
import cz.obehy.core.network.obehy.toLiveVehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * What a live map shows, from the source its mode has: Oběhy's vehicles, stops and boards in the
 * modes with Oběhy, MOTIS railviz, stops and stop times without. Decoding and merging run off the
 * main thread.
 */
@Singleton
class LiveMapRepository
    @Inject
    constructor(
        private val motis: MotisApi,
        private val obehy: ObehyApi,
        @param:Named("computation") private val computation: CoroutineDispatcher,
    ) {
        /** Cells whose last answer was full: asked as quarters from then on. */
        private val full = ConcurrentHashMap.newKeySet<String>()

        /**
         * Oběhy's vehicles in [bounds], by the web's grid cells. [held] are those on the map now: a
         * cell that fails keeps its vehicles there, and none is drawn back to an older position.
         */
        suspend fun obehyVehicles(
            bounds: MapBounds,
            held: List<LiveVehicle>,
        ): List<LiveVehicle> =
            withContext(computation) {
                val cells = cellsFor(bounds) { it in full }
                val answers =
                    coroutineScope {
                        cells
                            .map { cell -> async { cell to runCatchingNotCancelled { obehy.vehicles(cell.key) } } }
                            .awaitAll()
                    }
                if (cells.isNotEmpty() && answers.all { it.second.isFailure }) {
                    throw answers.first().second.exceptionOrNull()!!
                }
                val lists =
                    answers.map { (cell, answer) ->
                        answer.fold(
                            onSuccess = { found ->
                                if (found.vehicles.size >=
                                    ObehyApi.VEHICLES_LIMIT
                                ) {
                                    full += cell.key
                                } else {
                                    full -= cell.key
                                }
                                found.vehicles.mapNotNull { it.toLiveVehicle() }
                            },
                            onFailure = { held.filter { it.at.position in cell.bounds } },
                        )
                    }
                mergeVehicles(lists, held)
            }

        /** Railviz's stretches in [bounds] under way between [start] and [end], as MOTIS shows them at [zoom]. */
        suspend fun railviz(
            bounds: MapBounds,
            zoom: Int,
            start: Instant,
            end: Instant,
        ): List<RailvizSegment> =
            withContext(computation) {
                motis
                    .mapTrips(corner(bounds.south, bounds.west), corner(bounds.north, bounds.east), zoom, start, end)
                    .mapNotNull { it.toSegment() }
            }

        /** Vehicles found by line, train number or fleet number, nearest [near] first. Oběhy only. */
        suspend fun search(
            query: String,
            near: LatLon?,
        ): List<VehicleHit> =
            withContext(computation) {
                obehy.searchVehicles(query, near?.rounded()?.let { "${it.lat},${it.lon}" }).toHits(query)
            }

        /** What a vehicle's own record adds: its type and who reports its position. Oběhy only. */
        suspend fun facts(vehicleId: String): VehicleFacts =
            withContext(computation) {
                val detail = obehy.vehicle(vehicleId)
                val language = appLanguage()
                VehicleFacts(
                    model =
                        detail.registry
                            ?.let { listOfNotNull(it.manufacturer, it.model).joinToString(" ") }
                            ?.ifEmpty { null }
                            ?: detail.vehicle
                                ?.traits
                                ?.let {
                                    listOfNotNull(
                                        it.manufacturer,
                                        it.model,
                                    ).joinToString(" ")
                                }?.ifEmpty { null },
                    positionFrom =
                        detail.reports
                            .map { report ->
                                report.label?.let { if (language == "cs") it.cs else it.en }
                                    ?: report.source
                            }.distinct(),
                )
            }

        /** The stops in [bounds] from [source]; none when the box is wider than the source answers. */
        suspend fun stops(
            bounds: MapBounds,
            source: LiveMapSource,
        ): List<LiveStop> =
            withContext(computation) {
                when (source) {
                    LiveMapSource.Obehy -> {
                        if (bounds.area > ObehyApi.STOPS_BBOX_MAX_DEG2) {
                            emptyList()
                        } else {
                            obehy.stops(bounds.bbox).toLiveStops()
                        }
                    }

                    LiveMapSource.Railviz -> {
                        motis
                            .mapStops(corner(bounds.south, bounds.west), corner(bounds.north, bounds.east))
                            .toLiveStops()
                    }

                    LiveMapSource.None -> {
                        emptyList()
                    }
                }
            }

        /**
         * [stop]'s next departures or arrivals ([side]) from [source]: [rows] of them, from
         * [cursor] on (a board's `next`) or from now.
         */
        suspend fun board(
            stop: LiveStop,
            source: LiveMapSource,
            side: BoardSide = BoardSide.Departures,
            cursor: String? = null,
            rows: Int = BOARD_ROWS,
        ): StopBoard =
            withContext(computation) {
                val arrivals = side == BoardSide.Arrivals
                when (source) {
                    LiveMapSource.Obehy -> {
                        obehy.departures(stop.id, rows, arrivals, cursor).toBoard(appLanguage(), side)
                    }

                    else -> {
                        val times = motis.stopTimes(stop.id, rows, appLanguage(), arrivals, cursor)
                        StopBoard(stop, times.toDepartures(arrivals), Instant.now(), side, times.nextPageCursor)
                    }
                }
            }

        private companion object {
            const val BOARD_ROWS = 15
        }
    }

private val VehicleCell.bounds: MapBounds
    get() = MapBounds(x / 4.0, y / 4.0, (x + size) / 4.0, (y + size) / 4.0)

private val MapBounds.area: Double get() = (east - west) * (north - south)

/** `minlon,minlat,maxlon,maxlat`. */
private val MapBounds.bbox: String
    get() = String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", west, south, east, north)

private fun corner(
    lat: Double,
    lon: Double,
) = String.format(Locale.ROOT, "%.5f,%.5f", lat, lon)

private inline fun <T> runCatchingNotCancelled(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
