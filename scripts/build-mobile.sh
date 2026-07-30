#!/bin/bash
# Build the mobile debug APK. No install/deploy.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew :mobile:assembleDebug

APK="mobile/build/outputs/apk/debug/mobile-debug.apk"
echo "Built: $ROOT_DIR/$APK"
