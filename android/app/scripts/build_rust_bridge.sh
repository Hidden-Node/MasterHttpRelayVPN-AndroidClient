#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BRIDGE_DIR="$(cd "$SCRIPT_DIR/../../rust-jni-bridge" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../src/main/jniLibs"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
    NDK_VERSION=$(ls -1 "$ANDROID_HOME/ndk" | sort -V | tail -n1)
    ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
  fi
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
  echo "Error: ANDROID_NDK_HOME not set or invalid"
  exit 1
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/aarch64-linux-android21-clang"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$TOOLCHAIN/armv7a-linux-androideabi21-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$TOOLCHAIN/x86_64-linux-android21-clang"

export CC_aarch64_linux_android="$TOOLCHAIN/aarch64-linux-android21-clang"
export CC_armv7_linux_androideabi="$TOOLCHAIN/armv7a-linux-androideabi21-clang"
export CC_x86_64_linux_android="$TOOLCHAIN/x86_64-linux-android21-clang"

export AR_aarch64_linux_android="$TOOLCHAIN/llvm-ar"
export AR_armv7_linux_androideabi="$TOOLCHAIN/llvm-ar"
export AR_x86_64_linux_android="$TOOLCHAIN/llvm-ar"

rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android 2>/dev/null || true

cd "$BRIDGE_DIR"

cargo build --release --target aarch64-linux-android
cargo build --release --target armv7-linux-androideabi
cargo build --release --target x86_64-linux-android

mkdir -p "$OUTPUT_DIR/arm64-v8a" "$OUTPUT_DIR/armeabi-v7a" "$OUTPUT_DIR/x86_64"
cp "target/aarch64-linux-android/release/librust_jni_bridge.so" "$OUTPUT_DIR/arm64-v8a/"
cp "target/armv7-linux-androideabi/release/librust_jni_bridge.so" "$OUTPUT_DIR/armeabi-v7a/"
cp "target/x86_64-linux-android/release/librust_jni_bridge.so" "$OUTPUT_DIR/x86_64/"

echo "Built Rust JNI bridge libraries in $OUTPUT_DIR"
