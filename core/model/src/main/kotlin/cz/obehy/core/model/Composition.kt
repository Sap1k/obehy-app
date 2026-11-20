package cz.obehy.core.model

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
) {
    /** "1", "2", "1/2", or null (a locomotive, a coach without seats). */
    val classLabel: String? get() = classes.sorted().joinToString("/").ifEmpty { null }
}

/** Which ends of a vehicle are drawn as a cab: [front] toward the head of the train. */
data class CabEnds(
    val front: Boolean,
    val rear: Boolean,
)

/** A vehicle of a segment: its position in [Composition.units], its cab ends, and whether it joins or leaves within it. */
data class SegmentVehicle(
    val unit: Int,
    val cab: CabEnds,
    val partial: Boolean,
)

/** A piece of the trip between changes to the train, with its vehicles in train order. */
data class CompositionSegment(
    val fromCall: CompositionCall,
    val toCall: CompositionCall,
    val vehicles: List<SegmentVehicle>,
)

/** A stretch of the trip as shown: adjacent [segments] (positions) whose train is the same. */
data class CompositionStretch(
    val fromCall: CompositionCall,
    val toCall: CompositionCall,
    val segments: List<Int>,
)

/**
 * A train's formation, shaped by the Oběhy API: its [stretches] to show, and where the rider is
 * ([hereSegment], [hereStretch]): at the stop it was asked from, else at the trip's next stop.
 */
data class Composition(
    val status: CompositionStatus,
    val retryAfterSeconds: Int?,
    val units: List<CompositionUnit>,
    val segments: List<CompositionSegment>,
    val stretches: List<CompositionStretch>,
    val hereSegment: Int?,
    val hereStretch: Int?,
    /** Who says so. */
    val sources: List<String>,
) {
    /** The answer is an old one, or none yet, and a newer one is on its way. */
    val checking: Boolean get() = status == CompositionStatus.Pending || status == CompositionStatus.Stale

    /** A segment's units in train order. */
    fun unitsOf(segment: CompositionSegment): List<CompositionUnit> =
        segment.vehicles.mapNotNull { units.getOrNull(it.unit) }
}
