# Embedded WireGuard (GoBackend)

Corporate Android TV builds can establish a native WireGuard tunnel via the
official [`com.wireguard.android:tunnel`](https://github.com/WireGuard/wireguard-android)
library (userspace **libwg-go**).

## Architecture

| Component | Role |
|-----------|------|
| `GoBackend$VpnService` | Official `VpnService` that owns the TUN (`Builder.establish`) |
| `WireGuardEngine` | Builds `Config` and calls `GoBackend.setState(UP/DOWN)` |
| `WireGuardController` | Corporate-gated facade used by Splash / Main |
| `WireGuardKeepAliveService` | Foreground keep-alive (not the TUN owner) |
| `WireGuardCredentials` | Baked PrivateKey / Address / Endpoint / AllowedIPs |

```
Activity (VPN consent)
        │
        ▼
WireGuardController.ensureRunning()
        │
        ▼
WireGuardEngine ──▶ GoBackend.setState(UP, Config)
                        │
                        ▼
              GoBackend$VpnService (TUN + libwg-go)
```

## Configure

Edit `WireGuardCredentials.kt`:

```kotlin
const val ADDRESS = "10.0.0.2/32"
const val PRIVATE_KEY = "<interface private key base64>"
const val PEER_PUBLIC_KEY = "<server public key base64>"
const val ENDPOINT = "vpn.example.com:51820"
const val ALLOWED_IPS = "0.0.0.0/0"
```

Or call at runtime:

```kotlin
WireGuardController.connect(
    context,
    WireGuardTunnelConfig(
        privateKey = "…",
        address = "10.0.0.2/32",
        peerPublicKey = "…",
        endpoint = "host:51820",
        allowedIps = "0.0.0.0/0",
    ),
)
```

## VPN consent

`VpnService.prepare()` must succeed from a visible Activity (Splash / Main).
Device Owner can pin Always-On VPN to this package so the system relaunches
`GoBackend$VpnService` after reboot (`GoBackend.setAlwaysOnCallback`).
