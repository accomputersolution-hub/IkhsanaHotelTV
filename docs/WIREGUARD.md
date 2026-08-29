# Embedded WireGuard (GoBackend) + peer registration

Corporate Android TV builds establish a native WireGuard tunnel via
[`com.wireguard.android:tunnel`](https://github.com/WireGuard/wireguard-android)
(**libwg-go** / `GoBackend`), after registering the device with the Node peer API.

## Flow

1. **Keygen** — `KeyPair()` (official crypto; wraps `Key.generatePrivateKey()`)
2. **Client IP** — fixed `10.0.0.3/32` (`WireGuardCredentials.CLIENT_ADDRESS`)
3. **POST** `http://103.29.99.61:3000/api/add-peer`
   ```json
   { "publicKey": "<device public key>", "clientIp": "10.0.0.3" }
   ```
4. **On HTTP 200** — `WireGuardController.connect(...)` with:
   - `address = 10.0.0.3/32`
   - locally generated private key
   - server public key (`WireGuardCredentials.SERVER_PUBLIC_KEY` or API `serverPublicKey`)
   - `endpoint = 103.29.99.61:51820`
   - `allowedIps = 0.0.0.0/0`

| Component | Role |
|-----------|------|
| `GoBackend$VpnService` | Official TUN owner |
| `WireGuardKeyStore` | Persist local keypair + registration flag |
| `WireGuardPeerApi` | HTTP add-peer |
| `WireGuardProvisioner` | Orchestrates keygen → API → connect |
| `WireGuardEngine` | `GoBackend.setState(UP/DOWN)` |

## Server public key

Baked into `WireGuardCredentials.SERVER_PUBLIC_KEY`:

`eGIDnt4o1QVDVxm/t0jqeWpPrvy3QKY8RHhJIucGhmU=`

If add-peer returns `{ "serverPublicKey": "…" }`, that value is preferred and persisted over the baked default.

## Cleartext

`network_security_config.xml` allows HTTP cleartext only to `103.29.99.61` for the peer API.
