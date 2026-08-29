# Embedded Tailscale (single APK, Headscale, per-app VPN)

This project embeds **libtailscale** (Tailscale's Go engine) directly in the corporate APK.
No separate `com.tailscale.ipn` app is required.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Hotel TV APK (in.pcncloud.corporate)                   │
│  ┌─────────────────┐   ┌──────────────────────────────┐ │
│  │ EmbeddedTailscale│   │ EmbeddedTailscaleVpnService  │ │
│  │ Engine           │──▶│ (VpnService + IPNService)    │ │
│  │ Libtailscale.start│   │ addAllowedApplication(ektv) │ │
│  └────────┬────────┘   └──────────────────────────────┘ │
│           │ libtailscale.aar (gomobile)                 │
│           ▼                                             │
│     Headscale https://b6ba5e93d09be1.lhr.life              │
└─────────────────────────────────────────────────────────┘
```

**Split tunnel:** only `com.ektv.pro` traffic uses the Tailscale TUN (`VpnService.Builder.addAllowedApplication`).

**Credentials:** `EmbeddedTailscaleCredentials.kt` (control URL + auth key).

---

## CI build (no local Windows build)

GitHub Actions workflow **`.github/workflows/build-tailscale.yml`** builds `libtailscale.aar` on Ubuntu and commits it to the repo.

**Manual run:** GitHub → Actions → **Build libtailscale AAR** → **Run workflow**  
Optional input: `tailscale_android_ref` (default `main`).

Also runs on pushes to `main` that change the build script, and weekly (Monday 06:00 UTC).

After the workflow completes, pull `main` (or your branch) — `app/libs/libtailscale.aar` will be present for Android Studio on Windows without installing Go or NDK.

---

## Step 1 — Prerequisites (local build only)

| Tool | Version |
|------|---------|
| Go | 1.22+ |
| Android SDK | API 36 platform, Build-tools 35+ |
| Android NDK | 23.1.7779620 (used by tailscale-android Makefile) |
| git, make, zip | any recent |

Set environment:

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

---

## Step 2 — Build `libtailscale.aar`

From the repo root:

```bash
chmod +x scripts/build-libtailscale.sh
./scripts/build-libtailscale.sh
```

This clones `https://github.com/tailscale/tailscale-android` into `.tailscale-android-src/`, runs `make androidsdk` + `make libtailscale`, and copies:

```
app/libs/libtailscale.aar
```

**Pin a release tag (recommended for production):**

```bash
TAILSCALE_ANDROID_REF=v1.90.0 ./scripts/build-libtailscale.sh
```

**Manual build (inside tailscale-android clone):**

```bash
git clone https://github.com/tailscale/tailscale-android.git
cd tailscale-android
export ANDROID_SDK_ROOT=$ANDROID_HOME
make androidsdk      # installs platform, NDK, build-tools
make libtailscale      # gomobile bind → android/libs/libtailscale.aar
cp android/libs/libtailscale.aar /path/to/IkhsanaHotelTV/app/libs/
```

`gomobile bind` compiles the Go `libtailscale` package into `libgojni.so` for `arm64-v8a` / `armeabi-v7a` / `x86` / `x86_64`.

---

## Step 3 — Gradle integration (already configured)

`app/build.gradle.kts`:

- `implementation(files("libs/libtailscale.aar"))` — fails fast if AAR missing
- `kotlinx-serialization-json` — LocalAPI JSON
- `androidx.security:security-crypto` — encrypted state prefs for libtailscale

Sync Gradle after placing the AAR.

---

## Step 4 — Kotlin bridge (in this repo)

| File | Role |
|------|------|
| `EmbeddedTailscaleAppContext.kt` | Implements `libtailscale.AppContext` (Android ↔ Go bridge) |
| `EmbeddedTailscaleVpnService.kt` | `VpnService` + `libtailscale.IPNService`; **only `com.ektv.pro` allowed** |
| `TailscaleVpnBuilderAdapters.kt` | Maps `VpnService.Builder` → `libtailscale.VPNServiceBuilder` |
| `EmbeddedTailscaleEngine.kt` | `Libtailscale.start()`, Headscale login, starts VPN service |
| `EmbeddedTailscaleLocalApi.kt` | `POST /localapi/v0/start` + `login-interactive` |
| `EmbeddedTailscaleNotifier.kt` | Watches IPN state from Go |
| `EmbeddedTailscaleKeepAliveService.kt` | FGS keep-alive on boot |
| `TailscaleController.kt` | Corporate entry point from `Application` / `BootReceiver` |

### Boot flow

1. `HotelTvApplication` → `TailscaleController.init()` → `Libtailscale.start()`
2. Device Owner → `setAlwaysOnVpnPackage(self)` → `EmbeddedTailscaleVpnService`
3. `TailscaleController.ensureRunning()` → LocalAPI login with auth key → `Libtailscale.requestVPN(service)`
4. TUN established with per-app split for Live TV

### Headscale login (programmatic)

```kotlin
// EmbeddedTailscaleEngine.ensureRunning()
localApi.start(
    Options(
        AuthKey = "8cc35884186f1ca5cddd595431a0c7994e1c38f66dbf4435",
        UpdatePrefs = Prefs(
            ControlURL = "https://b6ba5e93d09be1.lhr.life",
            WantRunning = true,
        ),
    ),
)
localApi.startLoginInteractive()  // consumes auth key — no browser on TV
```

---

## Step 5 — AndroidManifest

`EmbeddedTailscaleVpnService` is declared with:

```xml
<service
    android:name=".tailscale.embed.EmbeddedTailscaleVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="specialUse">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

---

## Step 6 — Device Owner + first VPN consent

```bash
adb shell dpm set-device-owner in.pcncloud.corporate/in.pcncloud.hotel.kiosk.MyDeviceAdminReceiver
```

First boot may still need **one** `VpnService.prepare()` consent unless Always-On is already pinned to your package.

---

## Step 7 — Build APK

```bash
./gradlew :app:assembleCorporateDebug
```

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `Missing libtailscale.aar` | Run `scripts/build-libtailscale.sh` |
| `gomobile bind` fails | Install NDK 23.x; check `ANDROID_SDK_ROOT` |
| Stuck `NeedsLogin`, logcat `control: authRoutine: awaiting unpause` | Ensure VPN consent granted; TV has network before login. With HTTPS Headscale, use a valid TLS URL (no custom Go patches). |
| `not-in-map-poll` / coordination server unreachable | Confirm TV can reach Headscale (`https://b6ba5e93d09be1.lhr.life`); rotate auth key if expired |
| Headscale TLS errors on `https://` URL | Use plain `http://` for LAN Headscale, or install CA for self-signed HTTPS |
| Live TV not on VPN | Confirm `com.ektv.pro` installed; check `addAllowedApplication` logs |
| Auth key rejected | Rotate key in Headscale; update `EmbeddedTailscaleCredentials` |

---

## Updating Tailscale engine

1. Set `TAILSCALE_ANDROID_REF` to new tag
2. Re-run `./scripts/build-libtailscale.sh`
3. Rebuild APK and regression-test VPN + Live TV

---

## License note

`libtailscale` is BSD-3-Clause (Tailscale Inc). Review Tailscale and Headscale terms for your deployment.
