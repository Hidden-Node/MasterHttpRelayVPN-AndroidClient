#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bash "$SCRIPT_DIR/app/scripts/build_tun2socks.sh"
bash "$SCRIPT_DIR/app/scripts/build_rust.sh"

echo "Rust mobile artifacts are ready in android/app/libs and android/app/src/main/assets"
