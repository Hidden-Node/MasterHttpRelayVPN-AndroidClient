# Quick Start Guide - Python Core Android App

## Build the App

```bash
cd android
./gradlew assembleDebug
```

**Output**: `android/app/build/outputs/apk/debug/app-debug.apk`

## Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## View Logs

```bash
adb logcat | grep -E "MasterHttpRelayVPN|Python"
```

## Update Python Core

```bash
# Copy updated Python files
cp -r src android/app/src/main/python/
cp main.py android/app/src/main/python/

# Rebuild
cd android
./gradlew assembleDebug
```

## Requirements

- JDK 17
- Android SDK (API 35)
- Android NDK 26.3.11579264
- Python 3.8+ (for Chaquopy)
- Go 1.25+ (for tun2socks)

## Verify Migration

```bash
bash verify_migration.sh
```

All checks should show ✓

## Documentation

- **Python Integration**: `android/PYTHON_INTEGRATION.md`
- **Migration Details**: `ANDROID_MIGRATION.md`
- **Implementation Summary**: `IMPLEMENTATION_SUMMARY.md`
- **Full Report**: `MIGRATION_COMPLETE.md`

## Troubleshooting

### Build fails with "Python not found"
```bash
# Install Python 3.8+
sudo apt install python3.11  # Linux
brew install python@3.11     # macOS
```

### Chaquopy errors
```bash
# Clean and rebuild
cd android
./gradlew clean
./gradlew assembleDebug
```

### App crashes on start
```bash
# Check logs for Python errors
adb logcat | grep -E "Python|Exception"
```

## Testing

1. Launch app
2. Create profile with Google Apps Script deployment ID
3. Tap Connect
4. Verify "Python bridge loaded" in logs
5. Test browsing through VPN

## Success Indicators

- ✅ App builds without errors
- ✅ "Python bridge loaded" appears in logs
- ✅ VPN connects successfully
- ✅ Traffic routes through proxy
- ✅ HTTPS sites work (with certificate installed)

## Support

For issues, check:
1. Logs: `adb logcat`
2. Documentation in `android/PYTHON_INTEGRATION.md`
3. Verification: `bash verify_migration.sh`
