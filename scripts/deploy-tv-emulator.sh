#!/bin/bash
# Build the TV debug APK and deploy it to a running Android TV emulator.
# Usage: scripts/deploy-tv-emulator.sh [emulator-serial]
# If no serial is given, connected "emulator-*" devices are checked for the
# Android TV (leanback) feature and the first match is used — a plain phone
# emulator running alongside it is skipped, not just grabbed by list order.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SERIAL="${1:-}"
if [ -z "$SERIAL" ]; then
    for candidate in $(adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}'); do
        if adb -s "$candidate" shell pm has-feature android.software.leanback 2>/dev/null | grep -q true; then
            SERIAL="$candidate"
            break
        fi
    done
fi
if [ -z "$SERIAL" ]; then
    echo "No running TV emulator found. Start one, or pass its serial explicitly." >&2
    exit 1
fi

./gradlew :tv:assembleDebug

adb -s "$SERIAL" install -r tv/build/outputs/apk/debug/tv-debug.apk

echo "Installed on $SERIAL"
