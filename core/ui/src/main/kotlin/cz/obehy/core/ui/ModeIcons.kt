package cz.obehy.core.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsBusFilled
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.ui.graphics.vector.ImageVector
import cz.obehy.core.model.Mode
import cz.obehy.core.model.TransportType

/** The icon that stands for [Mode] everywhere: line badges, the journey bar, stop results. */
val Mode.icon: ImageVector
    get() =
        when (this) {
            Mode.Walk -> {
                Icons.AutoMirrored.Rounded.DirectionsWalk
            }

            Mode.Subway -> {
                Icons.Rounded.Subway
            }

            Mode.Tram -> {
                Icons.Rounded.Tram
            }

            Mode.RegionalRail, Mode.SuburbanRail, Mode.LongDistanceRail, Mode.HighSpeedRail, Mode.NightRail -> {
                Icons.Rounded.Train
            }

            Mode.Bus -> {
                Icons.Rounded.DirectionsBus
            }

            Mode.Coach -> {
                Icons.Rounded.DirectionsBusFilled
            }

            Mode.Ferry -> {
                Icons.Rounded.DirectionsBoat
            }

            Mode.Funicular, Mode.AerialLift, Mode.Other -> {
                Icons.Rounded.Commute
            }
        }

/**
 * One icon per kind of transport for a stop's [modes]: a station served by long-distance,
 * regional and night trains shows one train. (Icons can't be compared, as each read builds a new one.)
 */
fun distinctModeIcons(modes: Collection<Mode>): List<ImageVector> =
    modes
        .map { it.family }
        .distinct()
        .sorted()
        .map { it.icon }

/** The mode that stands for this mode's kind of transport. */
private val Mode.family: Mode
    get() =
        when (this) {
            Mode.RegionalRail, Mode.SuburbanRail, Mode.LongDistanceRail, Mode.HighSpeedRail, Mode.NightRail -> {
                Mode.RegionalRail
            }

            Mode.Funicular, Mode.AerialLift, Mode.Other -> {
                Mode.Other
            }

            else -> {
                this
            }
        }

/** The spoken name of [Mode], for content descriptions. */
@get:StringRes
val Mode.label: Int
    get() =
        when (this) {
            Mode.Walk -> {
                R.string.mode_walk
            }

            Mode.Subway -> {
                R.string.mode_subway
            }

            Mode.Tram -> {
                R.string.mode_tram
            }

            Mode.RegionalRail, Mode.SuburbanRail, Mode.LongDistanceRail, Mode.HighSpeedRail, Mode.NightRail -> {
                R.string.mode_train
            }

            Mode.Bus -> {
                R.string.mode_bus
            }

            Mode.Coach -> {
                R.string.mode_coach
            }

            Mode.Ferry -> {
                R.string.mode_ferry
            }

            Mode.Funicular, Mode.AerialLift, Mode.Other -> {
                R.string.mode_other
            }
        }

val TransportType.icon: ImageVector
    get() =
        when (this) {
            TransportType.Metro -> Mode.Subway.icon
            TransportType.Tram -> Mode.Tram.icon
            TransportType.RegionalRail -> Mode.RegionalRail.icon
            TransportType.LongDistanceRail -> Mode.LongDistanceRail.icon
            TransportType.RegionalBus -> Mode.Bus.icon
            TransportType.LongDistanceBus -> Mode.Coach.icon
        }

@get:StringRes
val TransportType.label: Int
    get() =
        when (this) {
            TransportType.Metro -> R.string.type_metro
            TransportType.Tram -> R.string.type_tram
            TransportType.RegionalRail -> R.string.type_regional_rail
            TransportType.LongDistanceRail -> R.string.type_long_distance_rail
            TransportType.RegionalBus -> R.string.type_regional_bus
            TransportType.LongDistanceBus -> R.string.type_long_distance_bus
        }
