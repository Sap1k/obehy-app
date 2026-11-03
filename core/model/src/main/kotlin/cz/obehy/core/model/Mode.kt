package cz.obehy.core.model

/** How a leg of a journey travels. */
enum class Mode {
    Walk,
    Subway,
    Tram,
    RegionalRail,
    SuburbanRail,
    LongDistanceRail,
    HighSpeedRail,
    NightRail,
    Bus,
    Coach,
    Ferry,
    Funicular,
    AerialLift,
    Other,
}

/**
 * The kinds of transport a search can leave out. Trolleybuses travel as buses. Ferries,
 * funiculars, cable cars and anything else are always allowed (see [alwaysAllowed]), so leaving
 * out one type never drops them silently.
 */
enum class TransportType(
    val modes: Set<Mode>,
) {
    Metro(setOf(Mode.Subway)),
    Tram(setOf(Mode.Tram)),
    RegionalRail(setOf(Mode.RegionalRail, Mode.SuburbanRail)),
    LongDistanceRail(setOf(Mode.LongDistanceRail, Mode.HighSpeedRail, Mode.NightRail)),
    RegionalBus(setOf(Mode.Bus)),
    LongDistanceBus(setOf(Mode.Coach)),
    ;

    companion object {
        val alwaysAllowed: Set<Mode> = setOf(Mode.Ferry, Mode.Funicular, Mode.AerialLift, Mode.Other)
    }
}
