#!/usr/bin/env bash
set -euo pipefail

echo "========================================================"
echo " [hifi-tui] Building Signed Android APK..."
echo "========================================================"

# 1. Sync latest root index.html into Android assets
cp index.html android/app/src/main/assets/index.html
echo "[OK] Synced index.html -> android/app/src/main/assets/index.html"

# 2. Generate signing keystore if not already present so APK installs cleanly on any device
if [ ! -f android/app/release-key.jks ]; then
  echo "[INFO] Generating signing keystore (android/app/release-key.jks)..."
  keytool -genkeypair -v     -keystore android/app/release-key.jks     -alias hifitui     -keyalg RSA -keysize 2048 -validity 10000     -storepass hifitui123 -keypass hifitui123     -dname "CN=apaloran, OU=HiFiTUI, O=apaloran, L=City, S=State, C=IN"
fi

# 3. Compile APK using Gradle
cd android
if [ -x "./gradlew" ]; then
  ./gradlew assembleRelease --no-daemon
elif command -v gradle >/dev/null 2>&1; then
  gradle assembleRelease --no-daemon
else
  echo "[ERR] Gradle not found locally. Install gradle ('sudo apt install gradle' / 'sudo pacman -S gradle') or push to GitHub to let GitHub Actions build the APK automatically."
  exit 1
fi
cd ..

mkdir -p dist
cp android/app/build/outputs/apk/release/app-release.apk dist/hifi-tui-1.0.0-android.apk
echo ""
echo "[OK] Signed Android APK ready to install:"
ls -lh dist/hifi-tui-1.0.0-android.apk
