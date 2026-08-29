package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.util.Log
import com.wireguard.crypto.KeyPair

/**
 * Persists a locally generated WireGuard [KeyPair] (private + public base64).
 *
 * Generation uses the official tunnel library: `KeyPair()` →
 * `Key.generatePrivateKey()` + `Key.generatePublicKey()` (package API).
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

        // Official WireGuard Android crypto: KeyPair() generates a Curve25519 pair.
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

    fun isPeerRegistered(): Boolean = prefs.getBoolean(KEY_PEER_REGISTERED, false)

    fun markPeerRegistered(serverPublicKey: String? = null) {
        val editor = prefs.edit().putBoolean(KEY_PEER_REGISTERED, true)
        if (!serverPublicKey.isNullOrBlank()) {
            editor.putString(KEY_SERVER_PUBLIC, serverPublicKey.trim())
        }
        editor.apply()
    }

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
    }
}
