package `in`.pcncloud.hotel.tailscale.embed

import kotlinx.serialization.Serializable

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
        var CorpDNS: Boolean = false,
        var RouteAll: Boolean = false,
    )

    @Serializable
    data class Options(
        var AuthKey: String? = null,
        var UpdatePrefs: Prefs? = null,
    )

    @Serializable
    data class Notify(
        val State: Int? = null,
        val ErrMessage: String? = null,
    )
}
