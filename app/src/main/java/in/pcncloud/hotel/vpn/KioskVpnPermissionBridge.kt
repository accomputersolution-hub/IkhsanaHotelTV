package `in`.pcncloud.hotel.vpn

import android.content.Intent
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds a one-shot [VpnService.prepare] Intent when boot-time start cannot
 * complete silently (permission not yet granted). Splash/MainActivity can
 * consume and launch it for result, then call [KioskVpnController.ensureRunning].
 */
object KioskVpnPermissionBridge {
    private val pending = AtomicReference<Intent?>(null)

    fun offer(prepareIntent: Intent) {
        pending.set(prepareIntent)
    }

    fun consume(): Intent? = pending.getAndSet(null)

    fun peek(): Intent? = pending.get()
}
