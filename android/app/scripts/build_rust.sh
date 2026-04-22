#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUST_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../src/main/assets"

echo "Building Rust binaries for Android..."
echo "Rust source: $RUST_DIR"
echo "Output dir: $OUTPUT_DIR"

# Ensure Rust Android targets are installed
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android 2>/dev/null || true

# Set up Android NDK paths
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -n "${ANDROID_HOME:-}" ]; then
        ANDROID_NDK_HOME="$ANDROID_HOME/ndk"
        # Find latest NDK version
        if [ -d "$ANDROID_NDK_HOME" ]; then
            NDK_VERSION=$(ls -1 "$ANDROID_NDK_HOME" | sort -V | tail -n1)
            ANDROID_NDK_HOME="$ANDROID_NDK_HOME/$NDK_VERSION"
        fi
    fi
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "Error: ANDROID_NDK_HOME not set or invalid"
    echo "Please set ANDROID_NDK_HOME to your Android NDK installation"
    exit 1
fi

echo "Using NDK: $ANDROID_NDK_HOME"

# Set up cargo config for cross-compilation
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi21-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android21-clang"

# Set up C compiler for ring crate (required for ring crypto library)
export CC_aarch64_linux_android="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"
export CC_armv7_linux_androideabi="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi21-clang"
export CC_x86_64_linux_android="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android21-clang"

# Set up archiver for ring crate
export AR_aarch64_linux_android="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android-ar"
export AR_armv7_linux_androideabi="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi-ar"
export AR_x86_64_linux_android="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android-ar"

# Build for each target
cd "$RUST_DIR"

echo "Building for arm64-v8a..."
cargo build --release --target aarch64-linux-android --bin mhrv-rs
mkdir -p "$OUTPUT_DIR/arm64-v8a"
cp target/aarch64-linux-android/release/mhrv-rs "$OUTPUT_DIR/arm64-v8a/"

echo "Building for armeabi-v7a..."
cargo build --release --target armv7-linux-androideabi --bin mhrv-rs
mkdir -p "$OUTPUT_DIR/armeabi-v7a"
cp target/armv7-linux-androideabi/release/mhrv-rs "$OUTPUT_DIR/armeabi-v7a/"

echo "Building for x86_64..."
cargo build --release --target x86_64-linux-android --bin mhrv-rs
mkdir -p "$OUTPUT_DIR/x86_64"
cp target/x86_64-linux-android/release/mhrv-rs "$OUTPUT_DIR/x86_64/"

echo "Rust binaries built successfully!"
ls -lh "$OUTPUT_DIR"/**/mhrv-rs
