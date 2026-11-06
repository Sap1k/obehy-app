package cz.obehy.core.map

import cz.obehy.core.model.LatLon

/** A vehicle this close to where you board is worth showing, however short the ride. */
private const val NEAR_METRES = 8_000.0

/** Or within this many times the size of what's framed: zooming out that far still leaves it readable. */
private const val NEAR_SHARE = 2.0

/** How much closer or further the vehicle gets before the framing is worth moving again. */
private const val CLOSER_SHARE = 0.5
private const val FURTHER_SHARE = 1.25

/**
 * This framing with the vehicle you're waiting for in it, when that's at [vehicle] and not too far
 * from [boarding] to fit without losing what's already framed. Anything but a focus stays as it is.
 */
fun MapFraming.takingIn(
    vehicle: LatLon?,
    boarding: LatLon?,
): MapFraming {
    if (this !is MapFraming.Focus || vehicle == null || boarding == null || points.isEmpty()) return this
    val corner = LatLon(points.minOf { it.lat }, points.minOf { it.lon })
    val across = corner.metresTo(LatLon(points.maxOf { it.lat }, points.maxOf { it.lon }))
    val near = vehicle.metresTo(boarding) <= maxOf(NEAR_METRES, across * NEAR_SHARE)
    return if (near) MapFraming.Focus(points + vehicle) else this
}

/**
 * Where to treat the vehicle as being when framing it, of the [held] place and where it is now,
 * [vehicle]. The held place lasts until it's a good deal closer or further from [boarding], so the
 * map stays still while the vehicle comes towards you across it, rather than creeping every report.
 */
fun heldVehiclePlace(
    held: LatLon?,
    vehicle: LatLon?,
    boarding: LatLon?,
): LatLon? {
    if (held == null || vehicle == null || boarding == null) return vehicle
    val then = held.metresTo(boarding)
    return if (vehicle.metresTo(boarding) in then * CLOSER_SHARE..then * FURTHER_SHARE) held else vehicle
}
