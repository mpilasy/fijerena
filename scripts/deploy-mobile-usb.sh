#!/bin/bash
# Build the mobile debug APK and deploy it to a phone connected over USB.
# Usage: scripts/deploy-mobile-usb.sh [serial]
# If no serial is given, the sole connected USB device (not an emulator, not a
# network/IP device) is used; if there's more than one, pass the serial explicitly.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SERIAL="${1:-}"
if [ -z "$SERIAL" ]; then
    mapfile -t CANDIDATES < <(adb devices | awk '$2=="device" && $1 !~ /^emulator-/ && $1 !~ /:/ {print $1}')
    if [ "${#CANDIDATES[@]}" -eq 0 ]; then
        echo "No USB device found. Connect a phone (and accept the debugging prompt), or pass its serial explicitly." >&2
        exit 1
    elif [ "${#CANDIDATES[@]}" -gt 1 ]; then
        echo "Multiple USB devices found, pass one explicitly:" >&2
        printf '  %s\n' "${CANDIDATES[@]}" >&2
        exit 1
    fi
    SERIAL="${CANDIDATES[0]}"
fi

./gradlew :mobile:assembleDebug

adb -s "$SERIAL" install -r mobile/build/outputs/apk/debug/mobile-debug.apk

echo "Installed on $SERIAL"
