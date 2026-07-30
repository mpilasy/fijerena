#!/bin/bash
# Build the TV debug APK. No install/deploy.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew :tv:assembleDebug

APK="tv/build/outputs/apk/debug/tv-debug.apk"
echo "Built: $ROOT_DIR/$APK"
