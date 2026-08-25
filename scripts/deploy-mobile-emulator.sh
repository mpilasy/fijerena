#!/bin/bash
# Build the mobile debug APK and deploy it to a running Android phone emulator.
# Usage: scripts/deploy-mobile-emulator.sh [emulator-serial]
# If no serial is given, connected "emulator-*" devices are checked and the
# first one WITHOUT the Android TV (leanback) feature is used — a TV emulator
# running alongside it is skipped, not just grabbed by list order. (TV and
# mobile share the same applicationId, so installing on the wrong one
# silently overwrites it.)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SERIAL="${1:-}"
if [ -z "$SERIAL" ]; then
    for candidate in $(adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}'); do
        if ! adb -s "$candidate" shell pm has-feature android.software.leanback 2>/dev/null | grep -q true; then
            SERIAL="$candidate"
            break
        fi
    done
fi
if [ -z "$SERIAL" ]; then
    echo "No running phone emulator found. Start one, or pass its serial explicitly." >&2
    exit 1
fi

./gradlew clean :mobile:assembleDebug

adb -s "$SERIAL" install -r mobile/build/outputs/apk/debug/mobile-debug.apk

echo "Installed on $SERIAL"
