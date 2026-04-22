#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BRIDGE_DIR="$ANDROID_DIR/mobilebridge"
OUTPUT_DIR="$ANDROID_DIR/app/libs"

MOBILE_TOOLS_VERSION="${MOBILE_TOOLS_VERSION:-v0.0.0-20260312152759-81488f6aeb60}"

echo "Building tun2socks AAR from local mobile bridge..."

if [ ! -f "$BRIDGE_DIR/go.mod" ]; then
    echo "Error: mobile bridge module not found: $BRIDGE_DIR/go.mod"
    exit 1
fi

cd "$ANDROID_DIR"

# Install pinned gomobile toolchain (same strategy as go project)
go install "golang.org/x/mobile/cmd/gomobile@${MOBILE_TOOLS_VERSION}"
go install "golang.org/x/mobile/cmd/gobind@${MOBILE_TOOLS_VERSION}"

# Ensure gomobile bind dependencies are available
(
  cd "$BRIDGE_DIR"
  GO111MODULE=on go mod download
  GO111MODULE=on go get "golang.org/x/mobile@${MOBILE_TOOLS_VERSION}"
  GO111MODULE=on go get "golang.org/x/mobile/bind@${MOBILE_TOOLS_VERSION}"
)

export PATH="$(go env GOPATH)/bin:$PATH"
GO111MODULE=on gomobile init

mkdir -p "$OUTPUT_DIR"

# Bind local package to a deterministic Java package.
GO111MODULE=on gomobile bind \
  -v \
  -target=android/arm64,android/arm,android/amd64,android/386 \
  -androidapi=21 \
  -javapkg=com.masterhttprelay.tun2socks \
  -o "$OUTPUT_DIR/tun2socks.aar" \
  ./mobilebridge

echo "Built $OUTPUT_DIR/tun2socks.aar"
ls -lh "$OUTPUT_DIR/tun2socks.aar"
