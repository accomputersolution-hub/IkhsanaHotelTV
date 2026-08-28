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

PATCH_FILE="$ROOT/scripts/patches/libtailscale-headscale-initial-controlurl.patch"
if [[ -f "$PATCH_FILE" ]]; then
  echo "Applying libtailscale patch: $PATCH_FILE"
  # Reset patched files so re-runs apply cleanly (partial applies break gomobile bind).
  git -C "$SRC_DIR" checkout HEAD -- libtailscale/interfaces.go libtailscale/backend.go 2>/dev/null || true
  rm -f "$SRC_DIR/libtailscale/interfaces.go.rej" "$SRC_DIR/libtailscale/backend.go.rej"
  patch -p1 --forward -d "$SRC_DIR" < "$PATCH_FILE" || {
    if grep -q "headscaleAssumeNetworkUpForEmbeddedControl" "$SRC_DIR/libtailscale/backend.go"; then
      echo "Patch already applied."
    else
      echo "ERROR: failed to apply $PATCH_FILE" >&2
      exit 1
    fi
  }
fi

TAILSCALE_PATCH="$ROOT/scripts/patches/tailscale-http-controlurl.patch"
PATCHED_TAILSCALE_DIR="$SRC_DIR/.patched-deps/tailscale.com"
if [[ -f "$TAILSCALE_PATCH" ]]; then
  echo "Patching tailscale.com module for HTTP control URLs ..."
  go mod download tailscale.com
  MOD_DIR="$(go list -m -f '{{.Dir}}' tailscale.com)"
  MOD_VERSION="$(go list -m -f '{{.Version}}' tailscale.com)"
  STAMP_FILE="$PATCHED_TAILSCALE_DIR/.patch-stamp"
  if [[ -f "$STAMP_FILE" ]] && [[ "$(cat "$STAMP_FILE")" == "$MOD_VERSION" ]] \
      && grep -q "Plain-HTTP control planes" "$PATCHED_TAILSCALE_DIR/control/controlclient/direct.go" 2>/dev/null; then
    echo "tailscale.com $MOD_VERSION already patched at $PATCHED_TAILSCALE_DIR"
  else
    rm -rf "$PATCHED_TAILSCALE_DIR"
    mkdir -p "$PATCHED_TAILSCALE_DIR"
    tar -C "$MOD_DIR" -cf - . | tar -C "$PATCHED_TAILSCALE_DIR" -xf -
    chmod -R u+w "$PATCHED_TAILSCALE_DIR"
    patch -p1 --forward -d "$PATCHED_TAILSCALE_DIR" < "$TAILSCALE_PATCH" || {
      if grep -q "Plain-HTTP control planes" "$PATCHED_TAILSCALE_DIR/control/controlclient/direct.go"; then
        echo "HTTP control URL patch already applied."
      else
        echo "ERROR: failed to apply $TAILSCALE_PATCH" >&2
        exit 1
      fi
    }
    echo "$MOD_VERSION" > "$STAMP_FILE"
  fi
  go mod edit -dropreplace=tailscale.com 2>/dev/null || true
  go mod edit -replace="tailscale.com=$PATCHED_TAILSCALE_DIR"
  echo "go.mod replace: tailscale.com => $PATCHED_TAILSCALE_DIR"
fi

echo "Installing Android SDK packages (platform, NDK, build-tools) ..."
make androidsdk

echo "Building libtailscale.aar (gomobile bind — may take several minutes) ..."
make libtailscale

mkdir -p "$(dirname "$OUT_AAR")"
cp -f android/libs/libtailscale.aar "$OUT_AAR"
echo "OK: $OUT_AAR"
unzip -l "$OUT_AAR" | head -20
