#!/bin/bash
# Build the TV debug APK and deploy it to a TV reachable over the network (Shield, Bravia, ...).
# Usage: scripts/deploy-tv-ip.sh <ip>[:port]
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <ip>[:port]" >&2
    exit 1
fi

TARGET="$1"
if [[ "$TARGET" != *:* ]]; then
    TARGET="$TARGET:5555"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

adb connect "$TARGET" >/dev/null 2>&1 || true

# Check if the app is currently running and actively playing a media stream
IS_STREAMING=false
if adb -s "$TARGET" shell pidof org.njarasoa.fijerena >/dev/null 2>&1; then
    # Inspect MediaSession playback state (state=3 corresponds to PlaybackState.STATE_PLAYING)
    if adb -s "$TARGET" shell "dumpsys media_session | grep -A 8 'package=org.njarasoa.fijerena'" 2>/dev/null | grep -q "state=PlaybackState {state=3"; then
        IS_STREAMING=true
    fi
fi

if [ "$IS_STREAMING" = true ]; then
    echo "⚠️  Fijerena is currently playing a stream on $TARGET."
    read -rp "Are you sure you want to interrupt playback and deploy? [y/N]: " CONFIRM
    if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
        echo "Deployment aborted by user."
        exit 0
    fi
fi

./gradlew clean :tv:assembleDebug

adb -s "$TARGET" install -r tv/build/outputs/apk/debug/tv-debug.apk

echo "Installed on $TARGET"
