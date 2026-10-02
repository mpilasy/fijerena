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

# `install -r` can wipe app data with no warning (see deploy-tv-ip.sh), so back up the phone's
# user data first, into the same folder and with the same 7-day retention.
BACKUP_DIR="$ROOT_DIR/backups"
mkdir -p "$BACKUP_DIR"
find "$BACKUP_DIR" -maxdepth 1 \( -name '*.tar' -o -name '*.tar.gz' \) -mtime +7 -print -delete
if adb -s "$SERIAL" shell pm path org.njarasoa.fijerena >/dev/null 2>&1; then
    if ! scripts/backup-app-data.sh "$SERIAL" "$BACKUP_DIR/${SERIAL//[:.]/_}-$(date +%Y%m%d-%H%M%S).tar.gz"; then
        echo "ERROR: backup failed for $SERIAL, which has an existing install — not installing rather than risk an unprotected wipe." >&2
        exit 1
    fi
else
    echo "No existing Fijerena install on $SERIAL — nothing to back up."
fi

adb -s "$SERIAL" install -r mobile/build/outputs/apk/debug/mobile-debug.apk

echo "Installed on $SERIAL"
