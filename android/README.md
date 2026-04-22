# MasterHttpRelayVPN - Android (Rust Core)

Android VPN client using the Rust-based HTTP relay proxy core for DPI bypass via Google Apps Script.

## Architecture

This is a complete rewrite of the Android client to use the Rust core (`../Rust/`) instead of the Go-based DNS tunneling approach. The architecture follows a **JNI bridge + tun2socks** pattern:

```
Android VPN (TUN interface)
    ↓
tun2socks (Go-based bridge)
    ↓
Rust core (JNI cdylib)
    ↓
Rust HTTP proxy (127.0.0.1:8085)
    ↓
Google Apps Script relay (domain fronting)
    ↓
Internet
```

### Key Components

1. **Rust JNI Bridge**: Exposes start/stop/status callbacks from Rust to Kotlin
2. **Tun2SocksManager**: Bridges Android VPN TUN interface to Rust SOCKS5 proxy
3. **MasterDnsVpnService**: Android VpnService implementation
4. **Room + DataStore**: Profiles plus global settings
5. **VpnManager**: Singleton state management for VPN status and logs

## Critical Constraint

**DO NOT modify any code in `../Rust/` folder.** The Rust core is immutable. All integration work happens in the Android/Kotlin layer.

## Prerequisites

### Development Environment

- **Android Studio**: Hedgehog (2023.1.1) or later
- **JDK**: 17
- **Android SDK**: API 35 (compileSdk)
- **Android NDK**: r26b or later
- **Rust**: 1.70+ with Android targets
- **Go**: 1.21+ (for tun2socks)

### Rust Setup

```bash
# Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Add Android targets
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add x86_64-linux-android
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

### Build Rust Binaries

The Rust binaries are automatically built during the Gradle build process via the `buildRustAndroid` task. To build manually:

```bash
cd app
./scripts/build_rust.sh
```

This cross-compiles `mhrv-rs` for:
- `arm64-v8a` (aarch64-linux-android)
- `armeabi-v7a` (armv7-linux-androideabi)
- `x86_64` (x86_64-linux-android)

Binaries are placed in `app/src/main/assets/{abi}/mhrv-rs`.

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

The app uses a simplified configuration model matching the Rust JSON schema:

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

### Required Fields

- **script_id**: Google Apps Script deployment ID
- **auth_key**: Secret authentication key

### Apps Script Setup

1. Deploy the relay script from `../Rust/apps-script/` to Google Apps Script
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

## Differences from Go Version

| Aspect | Go Version | Rust Version |
|--------|-----------|--------------|
| Core | DNS tunneling (gomobile) | HTTP relay (Rust subprocess) |
| Bridge | Direct gomobile binding | tun2socks → SOCKS5 |
| Config | Room DB with profiles | DataStore with single config |
| UI | Multi-profile selector | Single config form |
| Logs | Go callback → Kotlin | Subprocess stdout parsing |
| CA Cert | N/A | Manual install (MITM mode) |

## Known Limitations

1. **tun2socks Integration**: The `Tun2SocksManager` currently has placeholder code. Full integration with the `tun2socks.aar` API is needed.
2. **MITM CA**: The Rust core generates a CA certificate for HTTPS interception. Users must manually install it via Android Settings → Security → Install from storage.
3. **Traffic Stats**: Not yet implemented (bytesIn/bytesOut always show 0).
4. **Auto-start**: Boot receiver is present but auto-start logic is not implemented.

## Testing

### Manual Testing Checklist

- [ ] Build completes without errors
- [ ] App installs on device
- [ ] Config screen saves settings
- [ ] VPN permission prompt appears
- [ ] Rust process starts (check logs)
- [ ] tun2socks bridge starts
- [ ] VPN interface establishes
- [ ] Traffic flows through relay
- [ ] Logs display Rust output
- [ ] Disconnect works cleanly
- [ ] No memory leaks (check with Profiler)

### Debugging

Enable verbose logging:

1. Set log level to "debug" in Config screen
2. Check Logcat for `RustProcessManager`, `RustVpnService`, `Tun2SocksManager` tags
3. Check app's Logs screen for Rust output

## CI/CD

### GitHub Actions Workflows

- **android-ci.yml**: Builds debug APK on push/PR
- **release.yml**: Manual release workflow with signing

### Secrets Required

- `KEYSTORE_BASE64`: Base64-encoded keystore file
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Key alias
- `KEY_PASSWORD`: Key password

## Troubleshooting

### Rust binary not found

- Ensure `build_rust.sh` ran successfully
- Check `app/src/main/assets/{abi}/mhrv-rs` exists
- Verify NDK path is correct

### tun2socks AAR missing

- Run `./scripts/build_tun2socks.sh`
- Ensure Go and gomobile are installed
- Check `app/libs/tun2socks.aar` exists

### VPN connection fails

- Check Config screen has valid script_id and auth_key
- Verify Apps Script deployment is accessible
- Check Logs screen for Rust error messages
- Test Rust binary directly: `adb shell /data/data/com.masterhttprelay.vpn/files/native/arm64-v8a/mhrv-rs --help`

### Gradle build fails

- Clean build: `./gradlew clean`
- Invalidate caches in Android Studio
- Check NDK version matches `build_rust.sh`

## License

MIT (same as parent project)

## Credits

- Rust core: [MasterHttpRelayVPN-Rust](../Rust/)
- tun2socks: [xjasonlyu/tun2socks](https://github.com/xjasonlyu/tun2socks)
- Original Go version: [MasterDnsVPN](../go/)
