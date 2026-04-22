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
fi
if ! command -v gobind &> /dev/null; then
    go install golang.org/x/mobile/cmd/gobind@latest
fi

gomobile init

# gomobile cannot bind a main package. Resolve a bindable package from repo.
if go list ./... | grep -q '^github.com/xjasonlyu/tun2socks/v2/mobile$'; then
    BIND_PKG="github.com/xjasonlyu/tun2socks/v2/mobile"
else
    BIND_PKG="$(go list -f '{{if ne .Name "main"}}{{.ImportPath}}{{end}}' ./... | sed '/^$/d' | head -n1)"
fi

if [ -z "${BIND_PKG:-}" ]; then
    echo "Error: no bindable (non-main) Go package found for tun2socks."
    echo "Hint: tun2socks upstream may have changed mobile package layout."
    exit 1
fi

# Build AAR for Android
echo "Building tun2socks AAR from package: $BIND_PKG"
gomobile bind -v -target=android -androidapi=21 \
    -ldflags="-s -w" \
    -o "$OUTPUT_DIR/tun2socks.aar" \
    "$BIND_PKG"

echo "tun2socks AAR built successfully!"
ls -lh "$OUTPUT_DIR/tun2socks.aar"
