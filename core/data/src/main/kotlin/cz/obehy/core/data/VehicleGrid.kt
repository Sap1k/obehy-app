package cz.obehy.core.data

import cz.obehy.core.model.LiveVehicle
import cz.obehy.core.model.MapBounds
import java.math.BigDecimal
import java.time.Instant
import kotlin.math.floor

// A port of the web's map grid (repo web/src/features/map/grid.ts and queries.ts). The map asks
// for vehicles by fixed cells, not by its viewport: every viewer asks for the same URLs (so
// nginx's micro-cache serves them all), and a cell stays well inside the API's largest bbox. A cell
// answering the API's most vehicles is cut into quarters, down to a quarter degree.

/** Czechia with a margin: the map's reach and the cells' clamp. */
val VEHICLE_AREA = MapBounds(west = 11.8, south = 48.4, east = 19.1, north = 51.2)

/** One cell; [x], [y] and [size] count quarter degrees, so its edges print exactly. */
data class VehicleCell(
    /** The bbox parameter, `minlon,minlat,maxlon,maxlat`. */
    val key: String,
    val x: Int,
    val y: Int,
    val size: Int,
)

private const val BASE = 4
private const val SMALLEST = 1

private fun cell(
    x: Int,
    y: Int,
    size: Int,
): VehicleCell {
    fun q(n: Int) = BigDecimal(n).divide(BigDecimal(4)).stripTrailingZeros().toPlainString()
    return VehicleCell("${q(x)},${q(y)},${q(x + size)},${q(y + size)}", x, y, size)
}

/** A cell's four quarters, or none when it is the smallest. */
fun VehicleCell.quarters(): List<VehicleCell> {
    if (size <= SMALLEST) return emptyList()
    val h = size / 2
    return listOf(cell(x, y, h), cell(x + h, y, h), cell(x, y + h, h), cell(x + h, y + h, h))
}

/** The cells covering the part of [bounds] inside [VEHICLE_AREA]; a cell [split] names is replaced by its quarters (and theirs). */
fun cellsFor(
    bounds: MapBounds,
    split: (String) -> Boolean = { false },
): List<VehicleCell> {
    val west = maxOf(bounds.west, VEHICLE_AREA.west)
    val east = minOf(bounds.east, VEHICLE_AREA.east)
    val south = maxOf(bounds.south, VEHICLE_AREA.south)
    val north = minOf(bounds.north, VEHICLE_AREA.north)
    if (west >= east || south >= north) return emptyList()
    val out = mutableListOf<VehicleCell>()

    fun add(c: VehicleCell) {
        val parts = if (split(c.key)) c.quarters() else emptyList()
        if (parts.isEmpty()) {
            out += c
        } else {
            parts.filter { it.overlaps(west, south, east, north) }.forEach(::add)
        }
    }
    var y = floor(south).toInt()
    while (y < north) {
        var x = floor(west).toInt()
        while (x < east) {
            add(cell(x * BASE, y * BASE, BASE))
            x++
        }
        y++
    }
    return out
}

private fun VehicleCell.overlaps(
    west: Double,
    south: Double,
    east: Double,
    north: Double,
) = x / 4.0 < east && (x + size) / 4.0 > west && y / 4.0 < north && (y + size) / 4.0 > south

private val LiveVehicle.fixedAt: Instant get() = at.seenAt ?: Instant.EPOCH

/**
 * One row per vehicle across cells: one on a cell's edge comes in both, the newer fix kept. A
 * vehicle [held] (drawn from an earlier answer) at a newer fix keeps that position: an older
 * answer (a cache's stale copy, a cell's cached answer after a pan) would draw it back along its
 * way, and the next answer forward again.
 */
fun mergeVehicles(
    answers: List<List<LiveVehicle>>,
    held: List<LiveVehicle> = emptyList(),
): List<LiveVehicle> {
    val byId = LinkedHashMap<String, LiveVehicle>()
    for (vehicles in answers) {
        for (v in vehicles) {
            val had = byId[v.id]
            if (had == null || v.fixedAt > had.fixedAt) byId[v.id] = v
        }
    }
    for (was in held) {
        val now = byId[was.id] ?: continue
        if (was.fixedAt > now.fixedAt) byId[was.id] = now.copy(at = was.at)
    }
    return byId.values.toList()
}
