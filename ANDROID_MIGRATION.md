# Android App - Python Core Migration

This document describes the migration from Rust to Python core for the Android VPN app.

## What Changed

### Before (Rust Core)
```
Android UI → RustBridge.kt → JNI → Rust Core (librust_jni_bridge.so)
```

### After (Python Core)
```
Android UI → PythonBridge.kt → Chaquopy → Python Core (vpn_core.py + src/)
```

## Key Changes

### 1. Build System
- **Removed**: Rust toolchain, Cargo, Rust build scripts
- **Added**: Chaquopy plugin, Python pip dependencies
- **Kept**: Go toolchain (for tun2socks), Android SDK/NDK

### 2. Bridge Layer
- **Removed**: `RustBridge.kt`, `android/rust-jni-bridge/`
- **Added**: `PythonBridge.kt`, `android/app/src/main/python/vpn_core.py`
- **Kept**: `RustBridgeCallback` interface (unchanged for UI compatibility)

### 3. Python Core Integration
- Python core files copied to `android/app/src/main/python/`
- Wrapper module (`vpn_core.py`) exposes lifecycle methods
- Certificate management adapted for Android storage
- Configuration passed as JSON string

### 4. CI/CD Pipelines
- Removed Rust setup and build steps
- Added Python setup (for validation)
- Removed Rust artifact uploads
- Kept signing and release logic

## Benefits

1. **Easier Updates**: Replace Python files and rebuild - no native compilation
2. **Unified Codebase**: Same Python core for desktop and Android
3. **Simpler Build**: No Rust cross-compilation complexity
4. **Maintainability**: Pure Python is easier to debug and modify

## Trade-offs

1. **APK Size**: +15-25MB (Python runtime and libraries)
2. **Memory**: +20-30MB runtime memory usage
3. **Performance**: Slightly higher CPU usage (acceptable for VPN proxy)

## UI Compatibility

**Zero UI changes required**. The Android UI remains completely identical:
- All screens work the same
- Same navigation and settings
- Same VPN lifecycle management
- Same split tunneling and proxy modes

## How to Build

```bash
cd android
./gradlew assembleDebug
```

Requirements:
- JDK 17
- Android SDK (API 35)
- Android NDK 26.3.11579264
- Python 3.8+ (for Chaquopy)
- Go 1.25+ (for tun2socks)

## How to Update Python Core

See [android/PYTHON_INTEGRATION.md](android/PYTHON_INTEGRATION.md) for detailed instructions.

Quick version:
```bash
# Copy updated Python files
cp -r src android/app/src/main/python/
cp main.py android/app/src/main/python/

# Rebuild
cd android
./gradlew assembleDebug
```

## Testing

1. Build and install:
   ```bash
   cd android
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. Test VPN connection:
   - Create a profile with Google Apps Script deployment ID
   - Connect to VPN
   - Verify traffic routing through proxy

3. Check logs:
   ```bash
   adb logcat | grep -E "MasterHttpRelayVPN|Python"
   ```

## Migration Checklist

- [x] Add Chaquopy plugin to Gradle
- [x] Copy Python core to `android/app/src/main/python/`
- [x] Create `vpn_core.py` wrapper module
- [x] Implement `PythonBridge.kt`
- [x] Update `MasterDnsVpnService.kt` to use PythonBridge
- [x] Remove Rust build scripts and dependencies
- [x] Update CI/CD workflows
- [x] Remove `android/rust-jni-bridge/` directory
- [x] Test VPN connection lifecycle
- [x] Verify certificate generation
- [x] Test split tunneling
- [x] Test proxy mode
- [x] Document Python integration

## Known Issues

None currently. If you encounter issues, check:
1. Python dependencies in `build.gradle.kts`
2. CA certificate directory permissions
3. Callback interface implementation
4. Logs for Python exceptions

## Support

For issues related to:
- **Python core**: Check main project README
- **Android integration**: See `android/PYTHON_INTEGRATION.md`
- **Build issues**: Check CI/CD workflow files
