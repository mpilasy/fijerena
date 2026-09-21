#!/bin/bash
# Build the TV debug APK once and deploy it to one or more TVs reachable over the network
# (Shield, Bravia, ...).
# Usage: scripts/deploy-tv-ip.sh <ip>[:port] [<ip>[:port] ...]
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <ip>[:port] [<ip>[:port] ...]" >&2
    exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TARGETS=()
for arg in "$@"; do
    if [[ "$arg" != *:* ]]; then
        arg="$arg:5555"
    fi
    TARGETS+=("$arg")
done

# Connect + reachability check for every target before building, so an unreachable device is
# skipped up front instead of discovered only after paying for the build.
REACHABLE=()
for TARGET in "${TARGETS[@]}"; do
    adb connect "$TARGET" >/dev/null 2>&1 || true
    if [ "$(adb -s "$TARGET" get-state 2>/dev/null)" = "device" ]; then
        REACHABLE+=("$TARGET")
    else
        echo "Device $TARGET not reachable — skipping. Check it's powered on and on the network." >&2
    fi
done

if [ ${#REACHABLE[@]} -eq 0 ]; then
    echo "No reachable devices." >&2
    exit 1
fi

# Streaming-interrupt confirmation for every reachable target, also before building — so a "no"
# here aborts before the build runs, not after.
for TARGET in "${REACHABLE[@]}"; do
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
done

./gradlew clean :tv:assembleDebug

for TARGET in "${REACHABLE[@]}"; do
    adb -s "$TARGET" install -r tv/build/outputs/apk/debug/tv-debug.apk
    echo "Installed on $TARGET"
done
