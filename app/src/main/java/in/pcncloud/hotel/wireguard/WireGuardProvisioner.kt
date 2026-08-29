package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * End-to-end corporate WireGuard bring-up:
 * 1. Generate (or load) local keypair via official [com.wireguard.crypto.KeyPair]
 * 2. POST public key to Node add-peer (server assigns next 10.0.0.x from wg0.conf)
 * 3. On HTTP 200 → [WireGuardController.connect] with returned address
 */
object WireGuardProvisioner {
    private const val TAG = "WireGuardProvisioner"

    private val mutex = Mutex()
    private val inFlight = AtomicBoolean(false)

    suspend fun provisionAndConnect(context: Context): Boolean = mutex.withLock {
        val app = context.applicationContext
        if (!inFlight.compareAndSet(false, true)) {
            Log.d(TAG, "provision already in flight")
            return false
        }
        try {
            if (!WireGuardEngine.isVpnPrepared(app)) {
                Log.w(TAG, "VPN consent missing — cannot provision yet")
                return false
            }

            val store = WireGuardKeyStore(app)
            val keys = store.getOrCreateKeyPair()
            Log.i(
                TAG,
                "Device key public=${keys.publicKeyBase64.take(8)}… " +
                    "fresh=${keys.freshlyGenerated} registered=${store.isPeerRegistered()} " +
                    "ip=${store.assignedClientIp()}",
            )

            if (!store.isPeerRegistered()) {
                val api = WireGuardPeerApi()
                val result = api.addPeer(publicKey = keys.publicKeyBase64)
                if (!result.success || result.clientIp.isNullOrBlank()) {
                    Log.e(
                        TAG,
                        "add-peer failed http=${result.httpCode} msg=${result.message}",
                    )
                    return false
                }
                store.markPeerRegistered(
                    clientIp = result.clientIp,
                    address = result.address,
                    dns = result.dns ?: WireGuardCredentials.DNS,
                    serverPublicKey = result.serverPublicKey,
                )
                Log.i(
                    TAG,
                    "add-peer OK — assigned ${result.address ?: result.clientIp + "/32"} " +
                        "dns=${result.dns ?: WireGuardCredentials.DNS}",
                )
            } else {
                Log.i(TAG, "Peer already registered — reconnecting tunnel")
            }

            val address = store.assignedAddress()
            if (address.isNullOrBlank()) {
                Log.e(TAG, "No assigned client address after registration")
                return false
            }

            val serverPub = store.serverPublicKeyOrDefault().trim()
            if (serverPub.isBlank()) {
                Log.e(
                    TAG,
                    "Missing server public key — set WireGuardCredentials.SERVER_PUBLIC_KEY " +
                        "or return serverPublicKey from add-peer",
                )
                return false
            }

            val dns = store.assignedDns()
            val config = WireGuardTunnelConfig(
                address = address,
                privateKey = keys.privateKeyBase64,
                peerPublicKey = serverPub,
                endpoint = WireGuardCredentials.ENDPOINT,
                allowedIps = WireGuardCredentials.ALLOWED_IPS,
                dns = dns,
                persistentKeepalive = WireGuardCredentials.PERSISTENT_KEEPALIVE,
            )
            Log.i(TAG, "Connecting tunnel address=$address dns=$dns")
            WireGuardController.connect(app, config)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "provisionAndConnect failed", t)
            false
        } finally {
            inFlight.set(false)
        }
    }
}
