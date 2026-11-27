package cz.obehy.feature.map

import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * How tall a sheet is when it peeks, and how what's in it sits there. One for each thing a sheet
 * shows; the screen reads [height], the sheet's [SheetFrame] and its parts tell it what they
 * measure.
 *
 * The peek ends just past the part [marked][mark] for it (a vehicle's next stops, a stop's first
 * departures), measured on this screen, so no row is ever cut off. It's measured only with the
 * sheet scrolled to its top and the part as it first opens. Within one window of what's marked it
 * only grows, so a row that changes as it goes (a stop name taking two lines) can't make it
 * bounce; a new window (the vehicle has moved on, fewer stops are left) is measured afresh.
 *
 * Opened out further ([opened]: more stops shown) the peek keeps its height and what's in it
 * scrolls in place, the [held] part (where the vehicle is) staying where it was: what opens above
 * it opens out of sight, to be scrolled to.
 */
@Stable
internal class SheetPeek {
    /** Where the peek ends, from the sheet's top; null until it has been measured. */
    var height: Dp? by mutableStateOf(null)
        private set

    /** Opened out past what the peek is measured for: at the peek, it scrolls in place. */
    var opened: Boolean by mutableStateOf(false)

    internal var scroll: ScrollState? = null
    internal var density: Density? = null

    /** Room left under the marked part, the navigation bar the sheet runs under included. */
    internal var below: Dp = 0.dp

    private var frame: LayoutCoordinates? = null
    private var marked: LayoutCoordinates? = null
    private var marking = false
    private var window: Any? = null
    private var settleUntil = 0L

    /** Where the held part's top is, from the frame's top, with nothing opened out or scrolled. */
    private var heldAt: Float? = null
    private var held: LayoutCoordinates? = null
    private var pinnedUntil = 0L

    internal fun frameAt(coordinates: LayoutCoordinates) {
        frame = coordinates
        measure()
    }

    /** Marks the part the peek shows all of, as it stands in [window]; not while it's [opened]. */
    fun mark(window: Any): Modifier =
        Modifier.onGloballyPositioned { coordinates ->
            marking = !opened
            if (opened) return@onGloballyPositioned
            if (window != this.window) {
                this.window = window
                // Rows unfold and fold for a moment: until they've settled, every measure counts.
                settleUntil = SystemClock.uptimeMillis() + SETTLE_MS
            }
            marked = coordinates
            measure()
        }

    /** Marks the part that stays put as more opens around it. */
    fun hold(): Modifier =
        Modifier.onGloballyPositioned { coordinates ->
            held = coordinates
            val top = topOf(coordinates) ?: return@onGloballyPositioned
            val scrolled = scroll?.value ?: 0
            if (!opened && scrolled == 0) heldAt = top
            // While rows open or fold away above it, it's kept where it was.
            if (SystemClock.uptimeMillis() < pinnedUntil) {
                val off = top - (heldAt ?: return@onGloballyPositioned)
                if (abs(off) >= 1f) scroll?.dispatchRawDelta(off)
            }
        }

    /** Keeps the held part where it rests while what's around it opens or folds. */
    fun pin() {
        pinnedUntil = SystemClock.uptimeMillis() + SETTLE_MS
    }

    /** Scrolls back to how the peek shows it: the top, or with more opened above, the held part where it rests. */
    suspend fun backToRest() {
        val scroll = scroll ?: return
        val top = held?.let(::topOf)
        val rest = heldAt
        val target = if (!opened || top == null || rest == null) 0 else (scroll.value + top - rest).toInt()
        scroll.animateScrollTo(target.coerceAtLeast(0))
    }

    private fun topOf(coordinates: LayoutCoordinates): Float? {
        val frame = frame?.takeIf { it.isAttached } ?: return null
        if (!coordinates.isAttached) return null
        return frame.localBoundingBoxOf(coordinates, clipBounds = false).top
    }

    private fun measure() {
        val frame = frame?.takeIf { it.isAttached } ?: return
        val marked = marked?.takeIf { it.isAttached && marking } ?: return
        val density = density ?: return
        if ((scroll?.value ?: 0) > 0) return
        val line = with(density) { frame.localBoundingBoxOf(marked, clipBounds = false).bottom.toDp() } + below
        val old = height
        val settling = SystemClock.uptimeMillis() < settleUntil
        // A change smaller than a pixel or so is only rounding.
        val change = old?.let { (line - it).value }
        if (change == null || (settling && abs(change) >= NOISE) || change >= NOISE) height = line
    }
}

/** How long rows take to unfold or fold away. */
private const val SETTLE_MS = 1_200L

/** Less than this many dp of change is noise. */
private const val NOISE = 1f
