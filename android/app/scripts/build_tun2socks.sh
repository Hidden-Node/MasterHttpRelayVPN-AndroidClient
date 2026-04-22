#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../libs"
TUN2SOCKS_VERSION="v2.5.2"
TUN2SOCKS_REPO="https://github.com/xjasonlyu/tun2socks"

echo "Building tun2socks for Android..."

# Create temp directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

cd "$TEMP_DIR"

# Clone tun2socks if not already present
if [ ! -d "tun2socks" ]; then
    echo "Cloning tun2socks $TUN2SOCKS_VERSION..."
    git clone --depth 1 --branch "$TUN2SOCKS_VERSION" "$TUN2SOCKS_REPO"
fi

cd tun2socks

# Check for gomobile
if ! command -v gomobile &> /dev/null; then
    echo "Installing gomobile..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    gomobile init
fi

# Build AAR for Android
echo "Building tun2socks AAR..."
gomobile bind -v -target=android -androidapi=21 \
    -ldflags="-s -w" \
    -o "$OUTPUT_DIR/tun2socks.aar" \
    github.com/xjasonlyu/tun2socks/v2

echo "tun2socks AAR built successfully!"
ls -lh "$OUTPUT_DIR/tun2socks.aar"
