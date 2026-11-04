package cz.obehy.core.model

import kotlin.math.abs

/** How far along an answer about a train's composition is. */
enum class CompositionStatus { Fresh, Stale, Pending, NotAvailable }

enum class UnitKind { Locomotive, Coach, MultipleUnit }

/** A stop a unit or a segment starts or ends at. */
data class CompositionCall(
    val sequence: Int,
    val name: String,
)

/** Something a coach has, with [icon] one of the API's names. */
data class CoachFeature(
    val id: String,
    val text: String,
    val icon: String?,
)

/** One vehicle of a train, running from [fromCall] to [toCall]. */
data class CompositionUnit(
    val kind: UnitKind,
    val vehicleType: String?,
    val coachNumber: String?,
    val classes: List<Int>,
    val features: List<CoachFeature>,
    /** A through coach's own destination. */
    val destination: String?,
    val fromCall: CompositionCall,
    val toCall: CompositionCall,
    val description: String? = null,
    val notes: String? = null,
    /** Where a through coach leaves this train, and as which train it goes on. */
    val leavesAt: String? = null,
    val continuesAs: String? = null,
    /** Which end has a cab: `front`, `rear`, `both`, or `true` when the source doesn't say. */
    val cab: String? = null,
) {
    /** "1", "2", "1/2", or null (a locomotive, a coach without seats). */
    val classLabel: String? get() = classes.sorted().joinToString("/").ifEmpty { null }
}

/** A piece of the trip between changes to the train; [units] are positions in [Composition.units]. */
data class CompositionSegment(
    val fromCall: CompositionCall,
    val toCall: CompositionCall,
    val units: List<Int>,
)

/** A train's formation. */
data class Composition(
    val status: CompositionStatus,
    val retryAfterSeconds: Int?,
    val units: List<CompositionUnit>,
    val segments: List<CompositionSegment>,
    /** Who says so. */
    val sources: List<String>,
) {
    /** The answer is an old one, or none yet, and a newer one is on its way. */
    val checking: Boolean get() = status == CompositionStatus.Pending || status == CompositionStatus.Stale

    /** The segment holding the stop [sequence]; a stop that ends one segment and starts the next is in the next. */
    fun segmentAt(sequence: Int?): Int {
        if (segments.isEmpty()) return -1
        if (sequence == null) return 0
        val at = segments.indexOfFirst { it.fromCall.sequence <= sequence && sequence < it.toCall.sequence }
        return when {
            at >= 0 -> at
            sequence < segments.first().fromCall.sequence -> 0
            else -> segments.lastIndex
        }
    }

    /** A segment's units in train order. */
    fun unitsOf(segment: CompositionSegment): List<CompositionUnit> = segment.units.mapNotNull(units::getOrNull)

    /** The stretches to show: neighbouring segments running the same train are one. */
    fun stretches(): List<CompositionStretch> =
        buildList {
            segments.forEachIndexed { index, segment ->
                val previous = lastOrNull()
                val before = previous?.let { segments[it.segments.last()] }
                if (previous != null && before != null && sameTrain(unitsOf(before), unitsOf(segment))) {
                    set(lastIndex, previous.copy(toCall = segment.toCall, segments = previous.segments + index))
                } else {
                    add(CompositionStretch(segment.fromCall, segment.toCall, listOf(index)))
                }
            }
        }
}

/** A stretch of the trip as shown: adjacent [segments] (positions) whose train is the same. */
data class CompositionStretch(
    val fromCall: CompositionCall,
    val toCall: CompositionCall,
    val segments: List<Int>,
)

/** Whether [unit] joins or leaves within [segment]. */
fun runsPartOf(
    unit: CompositionUnit,
    segment: CompositionSegment,
): Boolean = unit.fromCall.sequence > segment.fromCall.sequence || unit.toCall.sequence < segment.toCall.sequence

/** Which ends of a vehicle are a cab: [front] toward the head of the train. */
data class CabEnds(
    val front: Boolean,
    val rear: Boolean,
)

/**
 * The cab ends of a train's vehicles in order. Where the source names a car's cab side, that is
 * it. A locomotive's cab is at its end of the train. A cab without a side is placed: on a multiple
 * unit, the first such car faces forward and the next closes it; a control car shows it at the end
 * of the train it is at, inside the train away from its set's locomotive.
 */
fun cabEnds(units: List<CompositionUnit>): List<CabEnds> {
    val last = units.lastIndex
    val locomotives = units.indices.filter { units[it].kind == UnitKind.Locomotive }
    var open = false // inside a multiple unit whose front cab has been drawn
    return units.mapIndexed { n, unit ->
        val cab = unit.cab
        if (unit.kind != UnitKind.MultipleUnit) open = false
        when {
            cab == "front" || cab == "rear" || cab == "both" -> {
                if (unit.kind == UnitKind.MultipleUnit) open = cab == "front"
                CabEnds(front = cab != "rear", rear = cab != "front")
            }

            unit.kind == UnitKind.Locomotive -> {
                CabEnds(front = n == 0, rear = n == last)
            }

            cab != "true" -> {
                CabEnds(front = false, rear = false)
            }

            unit.kind == UnitKind.MultipleUnit -> {
                open = !open
                CabEnds(front = open, rear = !open)
            }

            else -> {
                val nearest = locomotives.minByOrNull { abs(it - n) }
                val front = n == 0 || (n != last && (if (nearest == null) n <= last / 2.0 else nearest > n))
                CabEnds(front = front, rear = !front)
            }
        }
    }
}

/** What makes two vehicles the same to a rider, not when or from where they were stated. */
private fun CompositionUnit.signature() =
    listOf(kind, vehicleType, coachNumber, classes, features.map { it.id }.sorted(), destination)

/** The same vehicles in the same order, or reversed: ČD answers from some stations back to front. */
private fun sameTrain(
    a: List<CompositionUnit>,
    b: List<CompositionUnit>,
): Boolean {
    val x = a.map { it.signature() }
    val y = b.map { it.signature() }
    return x == y || x == y.reversed()
}
