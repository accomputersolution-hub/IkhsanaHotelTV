# Embedded WireGuard (GoBackend) + peer registration

Corporate Android TV builds establish a native WireGuard tunnel via
[`com.wireguard.android:tunnel`](https://github.com/WireGuard/wireguard-android)
(**libwg-go** / `GoBackend`), after registering the device with the Node peer API.

## Flow

1. **Keygen** — `KeyPair()` (official crypto)
2. **POST** `http://103.29.99.61:3001/api/add-peer` with
   `{ "publicKey": "…", "deviceId": "<ANDROID_ID or UUID>" }`  
   Server remembers `deviceId` so app reinstalls keep the same `10.0.0.x`;
   otherwise reads `/etc/wireguard/wg0.conf`, takes the highest `10.0.0.x`, assigns **next** IP
3. **On HTTP 200** — persist returned `clientIp` / `address` / `dns`, then `WireGuardController.connect`:
   - `address` = server-assigned (e.g. `10.0.0.4/32`)
   - `DNS = 8.8.8.8, 8.8.4.4` (required for Android TV name resolution on full tunnel)
   - `PersistentKeepalive = 25`
   - local private key
   - server public key `eGIDnt4o1QVDVxm/t0jqeWpPrvy3QKY8RHhJIucGhmU=`
   - `endpoint = 103.29.99.61:51820`
   - `allowedIps = 0.0.0.0/0`
   - **Split tunnel** `IncludedApplications` = this app (`context.packageName`) + Pro TV
     (`KioskLockTask.LIVE_TV_PACKAGE` / `com.ektv.pro`) — all other apps use direct internet

See `wireguard-server/` for the Node implementation of auto IP assignment.

## Boot / cold-start (network race)

On TV reboot, Wi-Fi/Ethernet may take 10–15s before UDP to the VPN endpoint works.
Auto-connect ([WireGuardController.ensureRunning]) runs on **Dispatchers.IO**:

1. Wait for `ConnectivityManager` **validated** internet (poll up to 2 min)
2. **11s** routing settle delay (10–12s window)
3. add-peer + `setState(UP)`

| Component | Role |
|-----------|------|
| `GoBackend$VpnService` | Official TUN owner |
| `WireGuardKeyStore` | Persist local keypair + assigned IP |
| `WireGuardPeerApi` | HTTP add-peer |
| `WireGuardProvisioner` | Keygen → add-peer → connect |
| `WireGuardAutoConnect` | IO thread: network gate → provision/connect |
| `WireGuardNetworkGate` | Validated internet + routing settle |
| `WireGuardEngine` | `GoBackend.setState(UP/DOWN)` |
| `WireGuardNetworkMonitor` | `ConnectivityManager.NetworkCallback` → connect on VALIDATED, disconnect on lost |

## Cleartext

`network_security_config.xml` allows HTTP cleartext only to `103.29.99.61` for the peer API.

## libwg-go UAPI path (corporate)

The published `tunnel` AAR embeds `libwg-go.so` with UAPI socket dir
`/data/data/com.wireguard.android/cache/wireguard`, which our app cannot create
(`UAPIOpen: mkdir … permission denied` on boot).

Corporate builds override native libs in `app/src/corporate/jniLibs/` rebuilt with:

```bash
./scripts/build-libwg-go.sh
```

That sets `ipc.socketDirectory=/data/data/in.pcncloud.corporate/cache/wireguard`.
`WireGuardEngine.init()` also creates `cache/wireguard` before `GoBackend` loads.
