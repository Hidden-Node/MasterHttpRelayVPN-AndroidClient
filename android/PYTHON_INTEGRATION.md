# Python Core Integration

This Android app uses a Python-based VPN core integrated via [Chaquopy](https://chaquo.com/chaquopy/).

## Architecture

```
Android UI (Kotlin/Compose)
    ↓
PythonBridge.kt
    ↓
Chaquopy (Python-Java bridge)
    ↓
vpn_core.py (wrapper)
    ↓
Python Core (main.py + src/)
```

## Python Core Location

The Python core files are located in:
```
android/app/src/main/python/
├── vpn_core.py          # Android wrapper module
├── main.py              # Original Python entry point (not used directly)
└── src/                 # Core Python modules
    ├── proxy_server.py
    ├── domain_fronter.py
    ├── mitm.py
    ├── cert_installer.py
    └── ... (other modules)
```

## How to Update the Python Core

The Python core is **immutable** from the Android app's perspective. To update it:

1. **Replace Python files** in `android/app/src/main/python/`:
   ```bash
   # From the project root
   cp -r src android/app/src/main/python/
   cp main.py android/app/src/main/python/
   ```

2. **Update dependencies** (if needed) in `android/app/build.gradle.kts`:
   ```kotlin
   python {
       pip {
           install("cryptography>=41.0.0")
           install("h2>=4.1.0")
           // Add new dependencies here
       }
   }
   ```

3. **Rebuild the APK**:
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

**Important**: Do NOT modify the Python core files directly in `android/app/src/main/python/`. Always update the source files in the project root and copy them over.

## Certificate Management

The Python core generates MITM CA certificates on first run. These are stored in:
```
/data/data/com.masterhttprelay.vpn/files/ca/
├── ca.crt
└── ca.key
```

Users need to install `ca.crt` as a trusted certificate on their device for HTTPS interception to work.

## Configuration

The Android app generates a JSON configuration and passes it to the Python core via `vpn_core.start()`. The config format matches the original Python core's `config.json`:

```json
{
  "mode": "apps_script",
  "google_ip": "216.239.38.120",
  "front_domain": "www.google.com",
  "auth_key": "...",
  "script_id": "...",
  "listen_host": "127.0.0.1",
  "listen_port": 8085,
  "socks5_port": 8086,
  "log_level": "info"
}
```

## Callback Interface

The Python core communicates with Android via the `RustBridgeCallback` interface:

```kotlin
interface RustBridgeCallback {
    fun onStateChanged(state: Int, message: String?)
    fun onLog(level: Int, message: String)
    fun onFatal(message: String)
}
```

State codes:
- `1` = CONNECTING
- `2` = CONNECTED
- `3` = DISCONNECTED
- `4` = ERROR

Log levels:
- `1` = DEBUG
- `2` = INFO
- `3` = WARNING
- `4` = ERROR

## Dependencies

Python dependencies are managed by Chaquopy and installed automatically during build:

- `cryptography>=41.0.0` - For MITM certificate generation
- `h2>=4.1.0` - For HTTP/2 support
- `brotli>=1.1.0` - For Brotli decompression
- `zstandard>=0.22.0` - For Zstandard decompression

All dependencies are pure Python or have pre-built wheels for Android.

## Build Requirements

- Python 3.8+ (for Chaquopy build process)
- Android SDK with NDK 26.3.11579264 (for tun2socks)
- Go 1.25+ (for tun2socks)

## Troubleshooting

### Python module not found
- Ensure all Python files are in `android/app/src/main/python/`
- Check that `src/` directory is properly copied

### Import errors
- Verify all dependencies are listed in `build.gradle.kts`
- Clean and rebuild: `./gradlew clean assembleDebug`

### Certificate errors
- Check that CA directory is writable: `/data/data/com.masterhttprelay.vpn/files/ca/`
- Ensure certificate generation succeeded (check logs)

### Callback not working
- Verify `RustBridgeCallback` interface is implemented correctly
- Check Python logs for callback errors

## Performance Notes

- Python core runs in a background thread with its own asyncio event loop
- Memory usage is ~20-30MB higher than Rust implementation
- Performance is acceptable for VPN proxy use case
- APK size increases by ~15-25MB due to Python runtime

## Testing

To test the Python integration locally:

1. Build debug APK:
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

2. Install on device/emulator:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. Check logs:
   ```bash
   adb logcat | grep -E "MasterHttpRelayVPN|Python"
   ```

## CI/CD

The GitHub Actions workflows have been updated to:
- Remove Rust toolchain setup
- Add Python setup (for build validation)
- Keep Go setup (for tun2socks)
- Remove Rust artifact uploads

See `.github/workflows/android-ci.yml` and `.github/workflows/release-manual.yml`.
