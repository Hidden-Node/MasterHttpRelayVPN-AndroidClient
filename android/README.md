# MasterHttpRelayVPN - Android (Python Core)

Android VPN client using the Python-based HTTP relay proxy core via Chaquopy for DPI bypass via Google Apps Script.

## Architecture

The Android app uses the unified Python core via Chaquopy, bridging Kotlin and Python. The architecture follows a **Chaquopy + tun2socks** pattern:

```
Android VPN (TUN interface)
    ↓
tun2socks (Go-based bridge)
    ↓
Python HTTP proxy (127.0.0.1:8085)
    ↓
Python domain fronter (DPI bypass)
    ↓
Google Apps Script relay (domain fronting)
    ↓
Internet
```

### Key Components

1. **PythonBridge.kt**: Chaquopy-based bridge exposes start/stop/status callbacks from Python to Kotlin
2. **Tun2SocksManager**: Bridges Android VPN TUN interface to Python SOCKS5 proxy
3. **MasterDnsVpnService**: Android VpnService implementation
4. **Room + DataStore**: Profiles plus global settings
5. **VpnManager**: Singleton state management for VPN status and logs
6. **vpn_core.py**: Android-adapted Python VPN core wrapper

## Prerequisites

### Development Environment

- **Android Studio**: Hedgehog (2023.1.1) or later
- **JDK**: 17
- **Android SDK**: API 35 (compileSdk)
- **Android NDK**: r26b or later (for tun2socks compilation only)
- **Python**: 3.11+ (for local development, Chaquopy bundles Python)
- **Go**: 1.21+ (for tun2socks)

### Python Setup (Optional - for local development)

```bash
# Install Python 3.11+
# Download from https://www.python.org/downloads/

# Or use conda/pyenv
conda create -n mhrv python=3.11
conda activate mhrv

# Install dependencies
pip install cryptography h2 brotli zstandard
```

### Android NDK Setup

Set `ANDROID_NDK_HOME` environment variable:

```bash
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
```

Or let the build scripts auto-detect it from `$ANDROID_HOME/ndk/`.

### Go Setup (for tun2socks)

```bash
# Install Go
# Download from https://go.dev/dl/

# Install gomobile
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
```

## Building

### Python Core Files

Python files are automatically bundled by Chaquopy during the Gradle build process. Core files are located in:

```
android/app/src/main/python/
├── vpn_core.py              # Android wrapper for Python core
├── main.py                  # Original Python entry point
└── src/                     # Core Python modules
    ├── proxy_server.py      # HTTP proxy implementation
    ├── domain_fronter.py    # Domain fronting (DPI bypass)
    ├── mitm.py              # HTTPS interception
    ├── cert_installer.py    # Certificate management
    ├── h2_transport.py       # HTTP/2 support
    ├── codec.py             # Compression codecs
    ├── logging_utils.py      # Logging utilities
    ├── constants.py         # Configuration constants
    └── lan_utils.py         # LAN utilities
```

**No compilation needed** - Python files are interpreted at runtime via Chaquopy.

### Build tun2socks

```bash
cd app
./scripts/build_tun2socks.sh
```

This builds `tun2socks.aar` from [xjasonlyu/tun2socks](https://github.com/xjasonlyu/tun2socks) and places it in `app/libs/`.

### Build APK

```bash
./gradlew assembleDebug
```

Chaquopy will:
1. Include Python 3.11 runtime
2. Bundle Python files from `android/app/src/main/python/`
3. Install pip dependencies (cryptography, h2, brotli, zstandard)
4. Package everything into the APK

Or for release:

```bash
export ANDROID_SIGNING_ENABLED=true
export ANDROID_KEYSTORE_PATH=/path/to/keystore.jks
export ANDROID_KEYSTORE_PASSWORD=your_password
export ANDROID_KEY_ALIAS=your_alias
export ANDROID_KEY_PASSWORD=your_key_password

./gradlew assembleRelease
```

## Configuration

The app uses the same configuration model as the Python core, matching the JSON schema:

```json
{
  "mode": "apps_script",
  "google_ip": "216.239.38.120",
  "front_domain": "www.google.com",
  "script_id": "YOUR_DEPLOYMENT_ID",
  "auth_key": "YOUR_SECRET_KEY",
  "listen_host": "127.0.0.1",
  "listen_port": 8085,
  "socks5_port": 8086,
  "log_level": "info",
  "verify_ssl": true,
  "hosts": {},
  "sni_hosts": ["www.google.com", "drive.google.com"]
}
```

### Python Dependencies (Chaquopy)

Automatically installed via Chaquopy during build:
- `cryptography>=41.0.0` - MITM certificate generation
- `h2>=4.1.0` - HTTP/2 support  
- `brotli>=1.1.0` - Brotli decompression
- `zstandard>=0.22.0` - Zstandard decompression

All dependencies are pure Python or have pre-built wheels for Android.

### Required Fields

- **script_id**: Google Apps Script deployment ID
- **auth_key**: Secret authentication key

### Apps Script Setup

1. Deploy the relay script from `../apps_script/` to Google Apps Script
2. Get the deployment ID (looks like `AKfycby...`)
3. Set a strong auth key in the script
4. Enter both in the app's Config screen

## UI Structure

The app uses Jetpack Compose with a bottom navigation bar:

1. **Home**: Connect/disconnect button, status indicator, traffic stats
2. **Config**: Configuration form (script_id, auth_key, ports, SNI hosts, etc.)
3. **Logs**: Color-coded log viewer with auto-scroll
4. **Info**: About screen with version info and usage instructions

### Design Language

The UI follows the "Stitch" aesthetic from the original Go version:
- Primary color: `#4A90E2` (Stitch Blue)
- Material 3 components
- Clean, minimal design

## Differences from Previous Implementations

| Aspect | Rust Version | Python Version (Current) |
|--------|-------------|--------------------------|
| Core | Rust binary (JNI) | Python 3.11 (Chaquopy) |
| Bridge | RustBridge.kt (JNI) | PythonBridge.kt (Chaquopy) |
| Build | Rust cross-compilation | Chaquopy bundling |
| Dependencies | None (except tun2socks) | cryptography, h2, brotli, zstandard |
| Startup Speed | Fast (~100ms) | Slower (~500ms Python init) |
| APK Size | ~60 MB | ~80 MB (+Python runtime) |
| Memory Usage | Lower | Higher (+25 MB Python) |
| Maintainability | Moderate | High (pure Python, easier debugging) |
| Code Sharing | Separate | Unified (same code desktop/mobile) |

## Known Limitations & Status

### Completed
- ✅ Chaquopy integration (Python 3.11)
- ✅ Python core bundling and build system
- ✅ PythonBridge.kt implementation
- ✅ Callback mechanism for state/log reporting
- ✅ VpnService integration
- ✅ tun2socks bridge

### Current Limitations
1. **Startup Time**: Python initialization adds ~500ms vs Rust (~100ms)
2. **APK Size**: Larger due to Python runtime (~20MB increase)
3. **Memory**: Higher memory usage (~25MB increase) due to Python interpreter
4. **MITM CA**: Users must manually install CA certificate via Android Settings → Security → Install from storage
5. **Traffic Stats**: Not yet implemented (bytesIn/bytesOut always show 0)

## Testing

### Manual Testing Checklist

- [ ] Build completes without errors (`./gradlew assembleDebug`)
- [ ] App installs on device
- [ ] App launches successfully
- [ ] Config screen saves settings
- [ ] VPN permission prompt appears
- [ ] "Python bridge loaded" message in Logcat
- [ ] Python core starts (check logs for proxy server startup)
- [ ] tun2socks bridge starts
- [ ] VPN interface establishes
- [ ] Traffic flows through relay
- [ ] HTTPS sites load (certificate interception working)
- [ ] Logs display Python output
- [ ] Disconnect works cleanly
- [ ] App doesn't crash on reconnect

### Debugging

Enable verbose logging:

1. Set log level to "debug" in Config screen
2. Check Logcat for `MasterHttpRelayVPN`, `PythonBridge`, `vpn_core`, `Tun2SocksManager` tags:
   ```bash
   adb logcat | grep -E "MasterHttpRelayVPN|PythonBridge|vpn_core|Tun2Socks"
   ```
3. Check app's Logs screen for Python output
4. Python exceptions will appear in Logcat with traceback

## CI/CD

### GitHub Actions Workflows

- **android-ci.yml**: Builds debug APK on push/PR (Python 3.11 setup included)
- **release-manual.yml**: Manual release workflow with signing (Python 3.11 setup included)

### Secrets Required

- `KEYSTORE_BASE64`: Base64-encoded keystore file
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Key alias
- `KEY_PASSWORD`: Key password

### CI/CD Setup Notes

- Python 3.11 is automatically set up in CI workflows
- Chaquopy handles Python bundling during APK build
- No cross-compilation required (unlike Rust)
- Build time is slightly longer due to Python processing

## Troubleshooting

### Python runtime not loading

- Check `android/app/build.gradle.kts` for correct Chaquopy plugin config
- Ensure Python files are in `android/app/src/main/python/`
- Check Logcat for `Chaquopy` errors
- Re-run build with `./gradlew clean assembleDebug`

### "Python bridge not loaded" error

- Verify `PythonBridge.kt` is correctly implemented
- Check that `vpn_core.py` exists and has proper syntax
- Look for Python import errors in Logcat
- Run python syntax check: `python3 -m py_compile android/app/src/main/python/vpn_core.py`

### tun2socks AAR missing

- Run `./scripts/build_tun2socks.sh`
- Ensure Go and gomobile are installed
- Check `app/libs/tun2socks.aar` exists
- Verify NDK path is correct

### VPN connection fails

- Check Config screen has valid script_id and auth_key
- Verify Apps Script deployment is accessible
- Check Logs screen for Python error messages
- Verify Python dependencies installed (check Logcat for import errors)

### Gradle build fails

- Clean build: `./gradlew clean`
- Invalidate caches in Android Studio
- Check that Chaquopy plugin version is compatible with your Gradle version
- Verify Python 3.11 availability on build machine

## License

MIT (same as parent project)

## Credits

- Python core: [MasterHttpRelayVPN-Python](../)
- Chaquopy: [Chaquopy project](https://chaquo.com/chaquopy/)
- tun2socks: [xjasonlyu/tun2socks](https://github.com/xjasonlyu/tun2socks)
- Apps Script relay: Google Apps Script

## Documentation

- **[PYTHON_INTEGRATION.md](./PYTHON_INTEGRATION.md)** - Detailed Python-Android integration guide
- **[../IMPLEMENTATION_SUMMARY.md](../IMPLEMENTATION_SUMMARY.md)** - Complete migration summary
- **[../ANDROID_MIGRATION.md](../ANDROID_MIGRATION.md)** - Migration details and rationale
- **[../README.md](../README.md)** - Main project README
