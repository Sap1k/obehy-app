package cz.obehy.core.model

/**
 * How late something is as a rider sees it, in the Oběhy API's bands (`api-v1.toml`
 * `[punctuality]`, `api/punctuality.py`): a MOTIS time (which carries Oběhy's realtime) reads as
 * the web's boards do.
 */
enum class Punctuality {
    Early,
    OnTime,
    Late,
    VeryLate,
    ;

    /** Late enough to say so (the error colour, "+4 min"). */
    val late: Boolean get() = this == Late || this == VeryLate

    companion object {
        /** The band of a delay in shown minutes: early ≤ −2, on time −1…+2, late +3…+9. */
        fun of(delayMinutes: Long): Punctuality =
            when {
                delayMinutes <= EARLY_MAX -> Early
                delayMinutes <= ON_TIME_MAX -> OnTime
                delayMinutes <= LATE_MAX -> Late
                else -> VeryLate
            }

        /** Whether a delay in shown minutes is late enough to say so; null is not. */
        fun isLate(delayMinutes: Long?): Boolean = delayMinutes != null && of(delayMinutes).late

        private const val EARLY_MAX = -2L
        private const val ON_TIME_MAX = 2L
        private const val LATE_MAX = 9L
    }
}
