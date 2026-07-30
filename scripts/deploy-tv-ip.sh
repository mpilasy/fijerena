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

./gradlew :tv:assembleDebug

adb connect "$TARGET"
adb -s "$TARGET" install -r tv/build/outputs/apk/debug/tv-debug.apk

echo "Installed on $TARGET"
