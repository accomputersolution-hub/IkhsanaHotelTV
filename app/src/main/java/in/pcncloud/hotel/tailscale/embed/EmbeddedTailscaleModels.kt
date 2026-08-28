package `in`.pcncloud.hotel.tailscale.embed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Minimal ipn types for LocalAPI JSON — mirrors tailscale-android ui.model.Ipn. */
object EmbeddedTailscaleModels {

    enum class State(val value: Int) {
        NoState(0),
        InUseOtherUser(1),
        NeedsLogin(2),
        NeedsMachineAuth(3),
        Stopped(4),
        Starting(5),
        Running(6),
        Stopping(7);

        companion object {
            fun fromInt(value: Int): State =
                entries.firstOrNull { it.value == value } ?: NoState
        }
    }

    @Serializable
    data class Prefs(
        var ControlURL: String = "",
        var WantRunning: Boolean = false,
        var LoggedOut: Boolean = false,
        var CorpDNS: Boolean = false,
        var RouteAll: Boolean = false,
    )

    /**
     * Partial prefs for PATCH /prefs and POST /start UpdatePrefs JSON.
     * Set flags (e.g. [ControlURLSet]) must be true for the daemon to apply custom Headscale URL.
     */
    @Serializable
    data class MaskedPrefs(
        var ControlURL: String? = null,
        var ControlURLSet: Boolean? = null,
        var WantRunning: Boolean? = null,
        var WantRunningSet: Boolean? = null,
        var LoggedOut: Boolean? = null,
        var LoggedOutSet: Boolean? = null,
    )

    @Serializable
    data class Options(
        var AuthKey: String? = null,
        var UpdatePrefs: MaskedPrefs? = null,
    )

    @Serializable
    data class Notify(
        val State: Int? = null,
        val ErrMessage: String? = null,
        /** Present (often as `{}`) when headless / interactive login completes. */
        val LoginFinished: JsonElement? = null,
        val Prefs: Prefs? = null,
        val BrowseToURL: String? = null,
    )
}
