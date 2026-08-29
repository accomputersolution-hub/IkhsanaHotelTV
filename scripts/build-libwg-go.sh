#!/usr/bin/env bash
# Rebuild libwg-go.so for the corporate applicationId so UAPI sockets use a writable path.
# Official tunnel AAR ships libwg-go built for com.wireguard.android → UAPIOpen permission denied.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/23.1.7779620}"
HOST_PREBUILT="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
PKG="${ANDROID_PACKAGE_NAME:-in.pcncloud.corporate}"
SRC="$ROOT/scripts/libwg-go-build/libwg-go"
OUT="$ROOT/app/src/corporate/jniLibs"
API_LEVEL="${ANDROID_API_LEVEL:-24}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

if [[ ! -d "$SRC" ]]; then
  echo "Missing $SRC — download wireguard-android tunnel/tools/libwg-go first."
  exit 1
fi

build_abi() {
  local arch_name=$1 target=$2 out_abi=$3
  local dest="$OUT/$out_abi"
  mkdir -p "$dest"
  echo "=== libwg-go $out_abi package=$PKG ==="
  make -C "$SRC" -j"$(nproc)" \
    ANDROID_ARCH_NAME="$arch_name" \
    ANDROID_PACKAGE_NAME="$PKG" \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    CC="$HOST_PREBUILT/bin/${target}-clang" \
    CFLAGS="-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security -fPIC" \
    LDFLAGS="-Wl,--build-id=none -Wl,--no-undefined -Wl,--fatal-warnings -Wl,--gc-sections" \
    SYSROOT="$HOST_PREBUILT/sysroot" \
    TARGET="$target" \
    DESTDIR="$dest" \
    BUILDDIR="$SRC/build-$out_abi"
}

build_abi arm64 "aarch64-linux-android${API_LEVEL}" arm64-v8a
build_abi arm "armv7a-linux-androideabi${API_LEVEL}" armeabi-v7a
build_abi x86_64 "x86_64-linux-android${API_LEVEL}" x86_64
build_abi x86 "i686-linux-android${API_LEVEL}" x86

echo "Done. Socket path: /data/data/$PKG/cache/wireguard"
