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
BACKUP_DIR="$ROOT_DIR/backups"

TARGETS=()
for arg in "$@"; do
    if [[ "$arg" != *:* ]]; then
        arg="$arg:5555"
    fi
    TARGETS+=("$arg")
done

# Connect + reachability check for every target before building, so an unreachable device is
# skipped up front instead of discovered only after paying for the build. Also verify each
# target actually reports itself as a TV — the tv-debug.apk and mobile-debug.apk share an
# applicationId, so installing the wrong one silently overwrites whatever's there.
REACHABLE=()
for TARGET in "${TARGETS[@]}"; do
    adb connect "$TARGET" >/dev/null 2>&1 || true
    if [ "$(adb -s "$TARGET" get-state 2>/dev/null)" != "device" ]; then
        echo "Device $TARGET not reachable — skipping. Check it's powered on and on the network." >&2
        continue
    fi
    CHARACTERISTICS="$(adb -s "$TARGET" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r')"
    if [[ "$CHARACTERISTICS" != *tv* ]]; then
        echo "Device $TARGET does not report a TV characteristic (got '$CHARACTERISTICS') — skipping, this is the TV deploy script." >&2
        continue
    fi
    REACHABLE+=("$TARGET")
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
mkdir -p "$BACKUP_DIR"

# Installs run in parallel — safe now that the build (the part that was actually racing before,
# via a shared output directory) has already finished: each install only reads the finished APK
# and targets its own device serial, so there's no shared mutable state between them. Each
# install is preceded by its own backup so the two stay paired per device under `&`.
PIDS=()
for TARGET in "${REACHABLE[@]}"; do
    (
        # `adb install -r` is not guaranteed to preserve app data — a signing-key mismatch (or
        # other cause) can make it install fresh with no warning, silently wiping providers/
        # favorites/watch history (this happened for real: a deploy that reported "Success" on
        # every device had actually wiped all of them). Back up first, every time, unprompted.
        SAFE_NAME="${TARGET//[:.]/_}"
        if adb -s "$TARGET" shell pm path org.njarasoa.fijerena >/dev/null 2>&1; then
            BACKUP_FILE="$BACKUP_DIR/${SAFE_NAME}-$(date +%Y%m%d-%H%M%S).tar"
            if adb -s "$TARGET" exec-out "run-as org.njarasoa.fijerena tar -c -C /data/data/org.njarasoa.fijerena shared_prefs databases/providers.db databases/providers.db-wal databases/providers.db-shm" > "$BACKUP_FILE" 2>/dev/null \
                && [ -s "$BACKUP_FILE" ]; then
                echo "Backed up $TARGET data -> $BACKUP_FILE"
            else
                rm -f "$BACKUP_FILE"
                echo "ERROR: backup failed for $TARGET, which has an existing install — aborting its install rather than risk an unprotected wipe." >&2
                exit 1
            fi
        else
            echo "No existing Fijerena install on $TARGET — nothing to back up."
        fi
        adb -s "$TARGET" install -r tv/build/outputs/apk/debug/tv-debug.apk
        echo "Installed on $TARGET"
    ) &
    PIDS+=("$!")
done

FAILED=0
for PID in "${PIDS[@]}"; do
    wait "$PID" || FAILED=1
done

if [ "$FAILED" -eq 1 ]; then
    echo "One or more installs failed." >&2
    exit 1
fi
