package cz.obehy.core.model

/**
 * How much of Oběhy the app shows. Both modes plan journeys, show departures and enrich Czech
 * legs; Enhanced adds the live map and the operational detail (compositions, oběhy).
 */
enum class AppMode {
    /** Everything Oběhy knows: for people travelling in Czechia. */
    Enhanced,

    /** A streamlined international journey planner. */
    Transitous,
}

/** The MOTIS instance journeys are planned with. */
enum class RoutingProvider(val baseUrl: String) {
    /** The community instance with international coverage, Oběhy's Czech feeds included. */
    Transitous("https://api.transitous.org/"),

    /** Oběhy's own instance: Czech data only, the freshest Oběhy feeds. */
    Obehy("https://motis.obehy.cz/"),
}

/** The user's choices; both are null until onboarding is finished. */
data class AppSettings(
    val mode: AppMode? = null,
    val routingProvider: RoutingProvider? = null,
) {
    val isOnboarded: Boolean get() = mode != null && routingProvider != null
}
