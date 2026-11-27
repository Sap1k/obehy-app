package cz.obehy.core.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A trip a map can open, named the way its source names it. IDs are opaque: an Oběhy trip is never
 * turned into a MOTIS one or back.
 */
@Serializable
sealed interface TripRef {
    /** A MOTIS trip (railviz, MOTIS stop times): opened through MOTIS `trip`. */
    @Serializable
    data class Motis(
        val tripId: String,
    ) : TripRef

    /** An Oběhy trip (live vehicles, Oběhy boards): opened through `/trips/{tripId}/{serviceDate}`. */
    @Serializable
    data class Obehy(
        val tripId: String,
        val serviceDate: String,
    ) : TripRef
}

/** Where a live map's vehicles come from, if anywhere. */
enum class LiveMapSource {
    /** Oběhy's vehicles: reported, or snapped to their route. */
    Obehy,

    /** MOTIS railviz: every vehicle where its timetable (and any shared delay) puts it. */
    Railviz,

    /** Nothing to show: no Map tab. */
    None,
}

/** The kinds of vehicle a map can show or hide. Oběhy doesn't tell regional from long-distance rail, so neither does the map. */
enum class MapMode {
    Rail,
    Metro,
    Tram,
    Bus,
    Other,
    ;

    companion object {
        fun of(mode: Mode): MapMode =
            when (mode) {
                Mode.RegionalRail, Mode.SuburbanRail, Mode.LongDistanceRail, Mode.HighSpeedRail, Mode.NightRail -> Rail
                Mode.Subway -> Metro
                Mode.Tram -> Tram
                Mode.Bus, Mode.Coach -> Bus
                Mode.Walk, Mode.Ferry, Mode.Funicular, Mode.AerialLift, Mode.Other -> Other
            }
    }
}

/** A box on the map, in degrees. */
@Serializable
data class MapBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    operator fun contains(point: LatLon): Boolean = point.lon in west..east && point.lat in south..north

    /** Whether all of [other] lies inside this box. */
    fun covers(other: MapBounds): Boolean =
        other.west >= west && other.east <= east && other.south >= south && other.north <= north

    /** This box grown by [share] of its size on every side. */
    fun grown(share: Double): MapBounds {
        val dx = (east - west) * share
        val dy = (north - south) * share
        return MapBounds(west - dx, south - dy, east + dx, north + dy)
    }

    val center: LatLon get() = LatLon((south + north) / 2, (west + east) / 2)
}

/**
 * A vehicle on a live map. [line] is what its marker says (the line, or the train's name);
 * [late] is its delay band as its source judges it, so no screen keeps its own threshold.
 */
data class LiveVehicle(
    val id: String,
    val at: VehiclePosition,
    val mode: Mode,
    val line: String?,
    val color: String?,
    val textColor: String?,
    val headsign: String?,
    val delayMinutes: Long?,
    val late: Boolean,
    val trip: TripRef?,
    /** The train number (Os 6819), when it has one apart from its line. */
    val tripShortName: String? = null,
) {
    val mapMode: MapMode get() = MapMode.of(mode)
}

/** A vehicle found by a search for [query], [metres] from where the map is. */
data class VehicleHit(
    val vehicle: LiveVehicle,
    val metres: Int?,
    val matched: HitKind,
    /** Its fleet number or plate, when that is what was matched. */
    val number: String? = null,
)

/** What a search matched on a vehicle: the API ranks them in this order. */
enum class HitKind { TrainNumber, Line, VehicleNumber, Other }

/** A stop on a live map. [id] names it to the source the map's vehicles come from. */
data class LiveStop(
    val id: String,
    val name: String,
    val position: LatLon,
    val modes: Set<Mode>,
)

/** A departure from a stop (or, on an arrivals board, an arrival), as a board shows it; [trip] opens it on the map. */
data class StopDeparture(
    val trip: TripRef,
    val mode: Mode,
    val line: String?,
    val color: String?,
    val textColor: String?,
    val headsign: String?,
    val time: TimePoint,
    val late: Boolean,
    val cancelled: Boolean,
    val platform: String?,
    /** The train's own number (Os 6819), where it has one. */
    val number: String? = null,
    /** Boarding in a wheelchair is possible: by the timetable, or the vehicle that runs it. */
    val stepFree: Boolean = false,
)

/** Which way a board looks: what leaves a stop, or what gets there. */
enum class BoardSide { Departures, Arrivals }

/**
 * A stop's next departures or arrivals ([side]), as of [at]; [next] asks for the ones after them.
 * [notices] are the stop's own alerts, for above the rows.
 */
data class StopBoard(
    val stop: LiveStop,
    val departures: List<StopDeparture>,
    val at: Instant,
    val side: BoardSide = BoardSide.Departures,
    val next: String? = null,
    val notices: List<TripAlert> = emptyList(),
)

/** What a vehicle's own record adds to its sheet: its type, and who reports where it is. */
data class VehicleFacts(
    val model: String?,
    /** The sources giving its position, in the app's language. */
    val positionFrom: List<String>,
)
