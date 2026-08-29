package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.util.Log
import com.wireguard.crypto.KeyPair

/**
 * Persists a locally generated WireGuard [KeyPair] plus the server-assigned
 * client IP from add-peer.
 */
class WireGuardKeyStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreateKeyPair(): StoredKeyPair {
        val existingPrivate = prefs.getString(KEY_PRIVATE, null)
        val existingPublic = prefs.getString(KEY_PUBLIC, null)
        if (!existingPrivate.isNullOrBlank() && !existingPublic.isNullOrBlank()) {
            return StoredKeyPair(
                privateKeyBase64 = existingPrivate,
                publicKeyBase64 = existingPublic,
                freshlyGenerated = false,
            )
        }

        val pair = KeyPair()
        val privateB64 = pair.privateKey.toBase64()
        val publicB64 = pair.publicKey.toBase64()
        prefs.edit()
            .putString(KEY_PRIVATE, privateB64)
            .putString(KEY_PUBLIC, publicB64)
            .apply()
        Log.i(TAG, "Generated new WireGuard keypair public=${publicB64.take(8)}…")
        return StoredKeyPair(
            privateKeyBase64 = privateB64,
            publicKeyBase64 = publicB64,
            freshlyGenerated = true,
        )
    }

    fun isPeerRegistered(): Boolean =
        prefs.getBoolean(KEY_PEER_REGISTERED, false) &&
            !assignedAddress().isNullOrBlank()

    fun markPeerRegistered(
        clientIp: String,
        address: String? = null,
        serverPublicKey: String? = null,
    ) {
        val bare = clientIp.trim().replace(Regex("/\\d+$"), "")
        val cidr = address?.trim()?.takeIf { it.isNotBlank() } ?: "$bare/32"
        val editor = prefs.edit()
            .putBoolean(KEY_PEER_REGISTERED, true)
            .putString(KEY_CLIENT_IP, bare)
            .putString(KEY_ADDRESS, cidr)
        if (!serverPublicKey.isNullOrBlank()) {
            editor.putString(KEY_SERVER_PUBLIC, serverPublicKey.trim())
        }
        editor.apply()
    }

    fun assignedAddress(): String? =
        prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString(KEY_CLIENT_IP, null)?.takeIf { it.isNotBlank() }?.let { "$it/32" }

    fun assignedClientIp(): String? =
        prefs.getString(KEY_CLIENT_IP, null)?.takeIf { it.isNotBlank() }

    fun serverPublicKeyOrDefault(): String =
        prefs.getString(KEY_SERVER_PUBLIC, null)
            ?.takeIf { it.isNotBlank() }
            ?: WireGuardCredentials.SERVER_PUBLIC_KEY

    data class StoredKeyPair(
        val privateKeyBase64: String,
        val publicKeyBase64: String,
        val freshlyGenerated: Boolean,
    )

    companion object {
        private const val TAG = "WireGuardKeyStore"
        private const val PREFS_NAME = "wireguard_device_identity"
        private const val KEY_PRIVATE = "private_key_b64"
        private const val KEY_PUBLIC = "public_key_b64"
        private const val KEY_PEER_REGISTERED = "peer_registered"
        private const val KEY_SERVER_PUBLIC = "server_public_key_b64"
        private const val KEY_CLIENT_IP = "client_ip"
        private const val KEY_ADDRESS = "client_address"
    }
}
