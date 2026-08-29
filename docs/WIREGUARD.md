# Embedded WireGuard (GoBackend) + peer registration

Corporate Android TV builds establish a native WireGuard tunnel via
[`com.wireguard.android:tunnel`](https://github.com/WireGuard/wireguard-android)
(**libwg-go** / `GoBackend`), after registering the device with the Node peer API.

## Flow

1. **Keygen** — `KeyPair()` (official crypto)
2. **POST** `http://103.29.99.61:3000/api/add-peer` with `{ "publicKey": "…" }` only  
   Server reads `/etc/wireguard/wg0.conf`, takes the highest `10.0.0.x`, assigns **next** IP
3. **On HTTP 200** — persist returned `clientIp` / `address` / `dns`, then `WireGuardController.connect`:
   - `address` = server-assigned (e.g. `10.0.0.4/32`)
   - `DNS = 8.8.8.8, 8.8.4.4` (required for Android TV name resolution on full tunnel)
   - `PersistentKeepalive = 25`
   - local private key
   - server public key `eGIDnt4o1QVDVxm/t0jqeWpPrvy3QKY8RHhJIucGhmU=`
   - `endpoint = 103.29.99.61:51820`
   - `allowedIps = 0.0.0.0/0`

See `wireguard-server/` for the Node implementation of auto IP assignment.

| Component | Role |
|-----------|------|
| `GoBackend$VpnService` | Official TUN owner |
| `WireGuardKeyStore` | Persist local keypair + assigned IP |
| `WireGuardPeerApi` | HTTP add-peer |
| `WireGuardProvisioner` | Orchestrates keygen → API → connect |
| `WireGuardEngine` | `GoBackend.setState(UP/DOWN)` |

## Cleartext

`network_security_config.xml` allows HTTP cleartext only to `103.29.99.61` for the peer API.
