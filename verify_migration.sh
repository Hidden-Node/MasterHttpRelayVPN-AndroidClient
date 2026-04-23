#!/bin/bash
# Verification script for Rust to Python migration

echo "=== Verifying Python Core Migration ==="
echo ""

# Check Python files
echo "1. Checking Python core files..."
if [ -f "android/app/src/main/python/vpn_core.py" ]; then
    echo "   ✓ vpn_core.py exists"
else
    echo "   ✗ vpn_core.py missing"
fi

if [ -d "android/app/src/main/python/src" ]; then
    echo "   ✓ src/ directory exists"
    echo "     - Files: $(ls android/app/src/main/python/src | wc -l)"
else
    echo "   ✗ src/ directory missing"
fi

# Check bridge files
echo ""
echo "2. Checking bridge layer..."
if [ -f "android/app/src/main/java/com/masterhttprelay/vpn/bridge/PythonBridge.kt" ]; then
    echo "   ✓ PythonBridge.kt exists"
else
    echo "   ✗ PythonBridge.kt missing"
fi

if [ -f "android/app/src/main/java/com/masterhttprelay/vpn/bridge/RustBridge.kt" ]; then
    echo "   ✗ RustBridge.kt still exists (should be deleted)"
else
    echo "   ✓ RustBridge.kt removed"
fi

# Check Rust artifacts removed
echo ""
echo "3. Checking Rust artifacts removed..."
if [ -d "android/rust-jni-bridge" ]; then
    echo "   ✗ rust-jni-bridge/ still exists"
else
    echo "   ✓ rust-jni-bridge/ removed"
fi

if [ -f "android/build_rust_mobile.sh" ]; then
    echo "   ✗ build_rust_mobile.sh still exists"
else
    echo "   ✓ build_rust_mobile.sh removed"
fi

if [ -f "android/app/scripts/build_rust_bridge.sh" ]; then
    echo "   ✗ build_rust_bridge.sh still exists"
else
    echo "   ✓ build_rust_bridge.sh removed"
fi

# Check Gradle configuration
echo ""
echo "4. Checking Gradle configuration..."
if grep -q "com.chaquo.python" android/build.gradle.kts; then
    echo "   ✓ Chaquopy plugin added to root build.gradle.kts"
else
    echo "   ✗ Chaquopy plugin missing from root build.gradle.kts"
fi

if grep -q "com.chaquo.python" android/app/build.gradle.kts; then
    echo "   ✓ Chaquopy plugin applied in app build.gradle.kts"
else
    echo "   ✗ Chaquopy plugin not applied in app build.gradle.kts"
fi

if grep -q "python {" android/app/build.gradle.kts; then
    echo "   ✓ Python configuration block exists"
else
    echo "   ✗ Python configuration block missing"
fi

if grep -q "buildRustJniBridge" android/app/build.gradle.kts; then
    echo "   ✗ Rust build task still referenced"
else
    echo "   ✓ Rust build tasks removed"
fi

# Check CI/CD workflows
echo ""
echo "5. Checking CI/CD workflows..."
if grep -q "Set up Python" .github/workflows/android-ci.yml; then
    echo "   ✓ Python setup in android-ci.yml"
else
    echo "   ✗ Python setup missing from android-ci.yml"
fi

if grep -q "Set up Rust" .github/workflows/android-ci.yml; then
    echo "   ✗ Rust setup still in android-ci.yml"
else
    echo "   ✓ Rust setup removed from android-ci.yml"
fi

if grep -q "Set up Python" .github/workflows/release-manual.yml; then
    echo "   ✓ Python setup in release-manual.yml"
else
    echo "   ✗ Python setup missing from release-manual.yml"
fi

if grep -q "Set up Rust" .github/workflows/release-manual.yml; then
    echo "   ✗ Rust setup still in release-manual.yml"
else
    echo "   ✓ Rust setup removed from release-manual.yml"
fi

# Check service updates
echo ""
echo "6. Checking VPN service updates..."
if grep -q "PythonBridge" android/app/src/main/java/com/masterhttprelay/vpn/service/MasterDnsVpnService.kt; then
    echo "   ✓ MasterDnsVpnService uses PythonBridge"
else
    echo "   ✗ MasterDnsVpnService doesn't use PythonBridge"
fi

if grep -q "import com.masterhttprelay.vpn.bridge.RustBridge" android/app/src/main/java/com/masterhttprelay/vpn/service/MasterDnsVpnService.kt; then
    echo "   ✗ MasterDnsVpnService still imports RustBridge"
else
    echo "   ✓ RustBridge import removed from MasterDnsVpnService"
fi

# Check documentation
echo ""
echo "7. Checking documentation..."
if [ -f "android/PYTHON_INTEGRATION.md" ]; then
    echo "   ✓ PYTHON_INTEGRATION.md exists"
else
    echo "   ✗ PYTHON_INTEGRATION.md missing"
fi

if [ -f "ANDROID_MIGRATION.md" ]; then
    echo "   ✓ ANDROID_MIGRATION.md exists"
else
    echo "   ✗ ANDROID_MIGRATION.md missing"
fi

if [ -f "MIGRATION_COMPLETE.md" ]; then
    echo "   ✓ MIGRATION_COMPLETE.md exists"
else
    echo "   ✗ MIGRATION_COMPLETE.md missing"
fi

echo ""
echo "=== Verification Complete ==="
echo ""
echo "Next steps:"
echo "1. Review any ✗ items above"
echo "2. Build the app: cd android && ./gradlew assembleDebug"
echo "3. Test on device/emulator"
echo "4. Check logs: adb logcat | grep -E 'MasterHttpRelayVPN|Python'"
