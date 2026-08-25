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

# Check if the app is currently running and actively playing a media stream
IS_STREAMING=false
if adb -s "$SERIAL" shell pidof org.njarasoa.fijerena >/dev/null 2>&1; then
    # Inspect MediaSession playback state (state=3 corresponds to PlaybackState.STATE_PLAYING)
    if adb -s "$SERIAL" shell "dumpsys media_session | grep -A 8 'package=org.njarasoa.fijerena'" 2>/dev/null | grep -q "state=PlaybackState {state=3"; then
        IS_STREAMING=true
    fi
fi

if [ "$IS_STREAMING" = true ]; then
    echo "⚠️  Fijerena is currently playing a stream on $SERIAL."
    read -rp "Are you sure you want to interrupt playback and deploy? [y/N]: " CONFIRM
    if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
        echo "Deployment aborted by user."
        exit 0
    fi
fi

./gradlew clean :tv:assembleDebug

adb -s "$SERIAL" install -r tv/build/outputs/apk/debug/tv-debug.apk

echo "Installed on $SERIAL"
