#!/usr/bin/env bash
# Build libtailscale.aar from tailscale/tailscale-android using gomobile.
#
# Prerequisites:
#   - Go 1.22+ (https://go.dev/dl/)
#   - Android SDK + NDK 23.x (ANDROID_HOME or ANDROID_SDK_ROOT)
#   - git, make, zip, curl
#
# Usage:
#   ./scripts/build-libtailscale.sh
#   TAILSCALE_ANDROID_REF=v1.90.0 ./scripts/build-libtailscale.sh
#
# Output:
#   app/libs/libtailscale.aar

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="${TAILSCALE_ANDROID_SRC:-$ROOT/.tailscale-android-src}"
REF="${TAILSCALE_ANDROID_REF:-main}"
OUT_AAR="$ROOT/app/libs/libtailscale.aar"

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${ANDROID_SDK_ROOT}" ]]; then
  echo "ERROR: Set ANDROID_HOME or ANDROID_SDK_ROOT to your Android SDK." >&2
  exit 1
fi

if [[ ! -d "$SRC_DIR/.git" ]]; then
  echo "Cloning tailscale-android (ref=$REF) into $SRC_DIR ..."
  git clone --depth 1 --branch "$REF" https://github.com/tailscale/tailscale-android.git "$SRC_DIR"
else
  echo "Updating $SRC_DIR ..."
  git -C "$SRC_DIR" fetch --depth 1 origin "$REF"
  git -C "$SRC_DIR" checkout "$REF"
  git -C "$SRC_DIR" pull --ff-only origin "$REF" || true
fi

cd "$SRC_DIR"

# Factory build: no patches to libtailscale or tailscale.com (vanilla upstream).
echo "Resetting tailscale-android to clean upstream state ..."
git checkout HEAD -- libtailscale/ go.mod 2>/dev/null || true
rm -rf .patched-deps
go mod edit -dropreplace=tailscale.com 2>/dev/null || true
go mod download tailscale.com

echo "Installing Android SDK packages (platform, NDK, build-tools) ..."
make androidsdk

echo "Building libtailscale.aar (gomobile bind — may take several minutes) ..."
make clean 2>/dev/null || true
make libtailscale

mkdir -p "$(dirname "$OUT_AAR")"
cp -f android/libs/libtailscale.aar "$OUT_AAR"
echo "OK: $OUT_AAR"
unzip -l "$OUT_AAR" | head -20
