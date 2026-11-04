package cz.obehy.core.model

import java.net.URI

/** The MOTIS instances the app knows; [name] also tells their search histories apart. */
enum class RoutingProvider(
    val baseUrl: String,
) {
    /** The community instance with international coverage, Oběhy's Czech feeds included. */
    Transitous("https://api.transitous.org/"),

    /** Oběhy's own instance: Czech data only, the freshest Oběhy feeds. */
    Obehy("https://motis.obehy.cz/"),
}

/** Who plans the journeys, and whether Oběhy adds what it knows to them. */
enum class AppMode(
    val provider: RoutingProvider,
    /** Oběhy's live map and its detail on Czech trips. */
    val usesObehy: Boolean,
) {
    /** Oběhy for both: Czechia only, with the freshest timetables. */
    Obehy(RoutingProvider.Obehy, usesObehy = true),

    /** Transitous plans all over Europe and Oběhy fills in the Czech trips. */
    TransitousEnriched(RoutingProvider.Transitous, usesObehy = true),

    /** Nothing but a MOTIS server: Transitous, or the user's own ([AppSettings.customMotisUrl]). */
    MotisOnly(RoutingProvider.Transitous, usesObehy = false),
}

/** Whether the app is light or dark. */
enum class ThemeMode {
    /** Whatever the phone is set to. */
    System,
    Light,
    Dark,
}

/**
 * The user's choices. Onboarding saves the mode as it is picked and sets [onboardingFinished] only
 * when the user confirms, so the app stays in onboarding until then.
 */
data class AppSettings(
    val mode: AppMode? = null,
    /** The user's own server. Kept when leaving [AppMode.MotisOnly], but used only there. */
    val customMotisUrl: String? = null,
    val onboardingFinished: Boolean = false,
    val theme: ThemeMode = ThemeMode.System,
) {
    val isOnboarded: Boolean get() = onboardingFinished && mode != null

    private val provider get() = mode?.provider ?: RoutingProvider.Transitous
    private val customServer get() = customMotisUrl.takeIf { mode == AppMode.MotisOnly }

    /** Where journeys are planned. */
    val motisUrl: String get() = customServer ?: provider.baseUrl

    /** Which server this is: stop IDs and search history belong to one server. */
    val routingServer: String get() = customServer ?: provider.name
}

/**
 * [text] as a server address that request paths can be added to, or null if it can't be one.
 * Only https will do, as searches give away where the user is; no scheme is taken to mean https.
 */
fun motisUrlOf(text: String): String? {
    val url = text.trim().let { if ("://" in it) it else "https://$it" }
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val isSecure = uri.scheme?.lowercase() == "https" && uri.host != null
    return if (isSecure && uri.query == null && uri.fragment == null) url.trimEnd('/') + "/" else null
}
