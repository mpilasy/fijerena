#!/bin/bash
# Build the mobile APK and deploy it to a phone connected over USB.
# Usage: scripts/deploy-mobile-usb.sh [--debug] [serial]
# If no serial is given, the sole connected USB device (not an emulator, not a
# network/IP device) is used; if there's more than one, pass the serial explicitly.
set -euo pipefail

# Phones get the localRelease build by default (user, 2026-10-21): the release code, not
# debuggable, signed with the debug key, so it installs over a debug build and keeps the app's
# data. It cannot be backed up with run-as; accepted. --debug: the debug build instead.
# --release is still accepted (the default).
VARIANT=localRelease
ARGS=()
for arg in "$@"; do
    case "$arg" in
        --release) VARIANT=localRelease ;;
        --debug) VARIANT=debug ;;
        *) ARGS+=("$arg") ;;
    esac
done
set -- "${ARGS[@]+"${ARGS[@]}"}"

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

./gradlew ":mobile:assemble${VARIANT^}"

# `install -r` can wipe app data with no warning (see deploy-tv-ip.sh), so back up the phone's
# user data first, into the same folder and with the same 7-day retention.
BACKUP_DIR="$ROOT_DIR/backups"
mkdir -p "$BACKUP_DIR"
find "$BACKUP_DIR" -maxdepth 1 \( -name '*.tar' -o -name '*.tar.gz' \) -mtime +7 -print -delete
if ! adb -s "$SERIAL" shell dumpsys package org.njarasoa.fijerena.mobile 2>/dev/null | grep -q "pkgFlags=.*DEBUGGABLE" &&
    adb -s "$SERIAL" shell pm path org.njarasoa.fijerena.mobile >/dev/null 2>&1; then
    # A build that isn't debuggable (localRelease) can't be read with run-as, so it can't be
    # backed up; an install signed with the same key keeps its data.
    echo "WARNING: the Fijerena on $SERIAL is not debuggable, so its data can't be backed up first."
elif adb -s "$SERIAL" shell pm path org.njarasoa.fijerena.mobile >/dev/null 2>&1; then
    if ! scripts/backup-app-data.sh "$SERIAL" "$BACKUP_DIR/${SERIAL//[:.]/_}-$(date +%Y%m%d-%H%M%S).tar.gz"; then
        echo "ERROR: backup failed for $SERIAL, which has an existing install — not installing rather than risk an unprotected wipe." >&2
        exit 1
    fi
else
    echo "No existing Fijerena install on $SERIAL — nothing to back up."
fi

adb -s "$SERIAL" install -r "mobile/build/outputs/apk/$VARIANT/mobile-$VARIANT.apk"

echo "Installed on $SERIAL"
