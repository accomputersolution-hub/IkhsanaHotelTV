package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * End-to-end corporate WireGuard bring-up:
 * 1. Generate (or load) local keypair via official [com.wireguard.crypto.KeyPair]
 * 2. Assign client IP `10.0.0.3/32`
 * 3. POST public key to Node add-peer API
 * 4. On HTTP 200 → [WireGuardController.connect]
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
                    "fresh=${keys.freshlyGenerated} registered=${store.isPeerRegistered()}",
            )

            if (!store.isPeerRegistered()) {
                val api = WireGuardPeerApi()
                val result = api.addPeer(
                    publicKey = keys.publicKeyBase64,
                    clientIp = WireGuardCredentials.CLIENT_IP,
                )
                if (!result.success) {
                    Log.e(
                        TAG,
                        "add-peer failed http=${result.httpCode} msg=${result.message}",
                    )
                    return false
                }
                store.markPeerRegistered(result.serverPublicKey)
                Log.i(TAG, "add-peer OK — proceeding to connect")
            } else {
                Log.i(TAG, "Peer already registered — reconnecting tunnel")
            }

            val serverPub = store.serverPublicKeyOrDefault().trim()
            if (serverPub.isBlank()) {
                Log.e(
                    TAG,
                    "Missing server public key — set WireGuardCredentials.SERVER_PUBLIC_KEY " +
                        "(full key starting with eGIDnt4o…) or return serverPublicKey from add-peer",
                )
                return false
            }

            val config = WireGuardTunnelConfig(
                address = WireGuardCredentials.CLIENT_ADDRESS,
                privateKey = keys.privateKeyBase64,
                peerPublicKey = serverPub,
                endpoint = WireGuardCredentials.ENDPOINT,
                allowedIps = WireGuardCredentials.ALLOWED_IPS,
                dns = WireGuardCredentials.DNS,
                persistentKeepalive = WireGuardCredentials.PERSISTENT_KEEPALIVE,
            )
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
