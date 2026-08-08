package `in`.pcncloud.hotel.admin

/**
 * In-memory Admin / Staff session only.
 * Never persisted — cleared on exit, auto-dismiss, or process death.
 */
object AdminSession {

    @Volatile
    private var unlocked: Boolean = false

    /** Temporarily held after a successful PIN unlock; wiped by [clear]. */
    @Volatile
    private var masterPinInMemory: String? = null

    val isAuthenticated: Boolean
        get() = unlocked

    fun unlock(verifiedPin: String) {
        masterPinInMemory = verifiedPin
        unlocked = true
    }

    /** Drop Master PIN + auth flag from memory (call on every admin enter/exit path). */
    fun clear() {
        masterPinInMemory = null
        unlocked = false
    }
}
