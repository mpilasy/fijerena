#!/bin/bash
# Build the TV APK once and deploy it to one or more TVs reachable over the network
# (Shield, Bravia, ...).
# Usage: scripts/deploy-tv-ip.sh [--release] <ip>[:port] [<ip>[:port] ...]
set -euo pipefail

# --release: the localRelease build (the release code, not debuggable, signed with the debug key,
# so it installs over a debug build and keeps the app's data) instead of the debug build.
VARIANT=debug
ARGS=()
for arg in "$@"; do
    case "$arg" in
        --release) VARIANT=localRelease ;;
        *) ARGS+=("$arg") ;;
    esac
done
set -- "${ARGS[@]+"${ARGS[@]}"}"

if [ $# -lt 1 ]; then
    echo "Usage: $0 [--release] <ip>[:port] [<ip>[:port] ...]" >&2
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
# target actually reports itself as a TV, so a phone never gets the TV app.
#
# A TV in standby that sleeps its network (a Bravia with Remote start on) is woken with a
# Wake-on-LAN packet first; its screen stays off. Its MAC address comes from the ARP cache, or from
# scripts/wake-macs.local ("<ip> <mac>" per line, not in git).
WAKE_MACS_FILE="${FIJERENA_WAKE_MACS:-$ROOT_DIR/scripts/wake-macs.local}"

mac_of() {
    local ip="${1%%:*}" mac=""
    if [ -f "$WAKE_MACS_FILE" ]; then
        mac="$(awk -v ip="$ip" '$1 == ip { print $2; exit }' "$WAKE_MACS_FILE")"
    fi
    if [ -z "$mac" ]; then
        mac="$(ip neigh show "$ip" 2>/dev/null | awk '/lladdr/ { for (i = 1; i <= NF; i++) if ($i == "lladdr") print $(i + 1); exit }')"
    fi
    echo "$mac"
}

# Wakes $1 with Wake-on-LAN and waits up to a minute for adb. A TV in deep standby wakes its
# network only for a moment, so the packets go on every 2 s, to this network's broadcast address
# too, until the script ends (the install included). Returns whether adb answers.
WAKE_PIDS=()
trap 'for pid in "${WAKE_PIDS[@]+"${WAKE_PIDS[@]}"}"; do kill "$pid" 2>/dev/null || true; done' EXIT
wake_target() {
    local target="$1" mac broadcast
    mac="$(mac_of "$target")"
    [ -n "$mac" ] || return 1
    broadcast="${target%%:*}"
    broadcast="${broadcast%.*}.255"
    echo "Device $target not reachable — sending Wake-on-LAN to $mac..."
    python3 - "$mac" "$broadcast" <<'PY' &
import socket, sys, time
mac = bytes.fromhex(sys.argv[1].replace(":", "").replace("-", ""))
packet = b"\xff" * 6 + mac * 16
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
end = time.time() + 900
while time.time() < end:
    for address in (sys.argv[2], "255.255.255.255"):
        for port in (9, 7):
            s.sendto(packet, (address, port))
    time.sleep(2)
PY
    WAKE_PIDS+=("$!")
    for _ in $(seq 1 30); do
        sleep 2
        adb disconnect "$target" >/dev/null 2>&1 || true
        adb connect "$target" >/dev/null 2>&1 || true
        [ "$(adb -s "$target" get-state 2>/dev/null)" = "device" ] && return 0
    done
    return 1
}

REACHABLE=()
for TARGET in "${TARGETS[@]}"; do
    adb connect "$TARGET" >/dev/null 2>&1 || true
    if [ "$(adb -s "$TARGET" get-state 2>/dev/null)" != "device" ] && ! wake_target "$TARGET"; then
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
    # </dev/null: adb shell reads stdin, and would eat the answer to the question below.
    if adb -s "$TARGET" shell pidof org.njarasoa.fijerena </dev/null >/dev/null 2>&1; then
        # Inspect MediaSession playback state (state=3 corresponds to PlaybackState.STATE_PLAYING)
        if adb -s "$TARGET" shell "dumpsys media_session | grep -A 8 'package=org.njarasoa.fijerena'" </dev/null 2>/dev/null | grep -q "state=PlaybackState {state=3"; then
            IS_STREAMING=true
        fi
    fi

    if [ "$IS_STREAMING" = true ]; then
        echo "⚠️  Fijerena is currently playing a stream on $TARGET."
        # No answer (end of input) is a no, said out loud rather than a silent exit.
        read -rp "Are you sure you want to interrupt playback and deploy? [y/N]: " CONFIRM || CONFIRM=""
        if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
            echo "Deployment aborted by user."
            exit 0
        fi
    fi
done

command -v sqlite3 >/dev/null || { echo "ERROR: sqlite3 is needed to back up the TVs' user data (apt install sqlite3)." >&2; exit 1; }
./gradlew ":tv:assemble${VARIANT^}"
mkdir -p "$BACKUP_DIR"
# Keep a week of backups: anything older than 7 days goes before this run adds its own.
find "$BACKUP_DIR" -maxdepth 1 \( -name '*.tar' -o -name '*.tar.gz' \) -mtime +7 -print -delete

# Installs run in parallel — safe once the build (which shares an output directory) has
# finished: each install only reads the finished APK and targets its own device serial, so there's no shared mutable state between them. Each
# install is preceded by its own backup so the two stay paired per device under `&`.
PIDS=()
for TARGET in "${REACHABLE[@]}"; do
    (
        # `adb install -r` is not guaranteed to preserve app data — a signing-key mismatch (or
        # other cause) can make it install fresh with no warning, silently wiping providers/
        # favorites/watch history (this happened for real: a deploy that reported "Success" on
        # every device had actually wiped all of them). Back up first, every time, unprompted.
        SAFE_NAME="${TARGET//[:.]/_}"
        if ! adb -s "$TARGET" shell dumpsys package org.njarasoa.fijerena 2>/dev/null | grep -q "pkgFlags=.*DEBUGGABLE" &&
            adb -s "$TARGET" shell pm path org.njarasoa.fijerena >/dev/null 2>&1; then
            # A build that isn't debuggable (--release) can't be read with run-as, so it can't be
            # backed up; an install signed with the same key keeps its data.
            echo "WARNING: the Fijerena on $TARGET is not debuggable, so its data can't be backed up first."
        elif adb -s "$TARGET" shell pm path org.njarasoa.fijerena >/dev/null 2>&1; then
            # User data only (settings, sources, profiles, watch state, favourites; not the
            # catalogue) — see scripts/backup-app-data.sh. Restore: scripts/restore-app-data.sh.
            BACKUP_FILE="$BACKUP_DIR/${SAFE_NAME}-$(date +%Y%m%d-%H%M%S).tar.gz"
            if ! "$ROOT_DIR/scripts/backup-app-data.sh" "$TARGET" "$BACKUP_FILE"; then
                echo "ERROR: backup failed for $TARGET, which has an existing install — aborting its install rather than risk an unprotected wipe." >&2
                exit 1
            fi
        else
            echo "No existing Fijerena install on $TARGET — nothing to back up."
        fi
        adb -s "$TARGET" install -r "tv/build/outputs/apk/$VARIANT/tv-$VARIANT.apk"
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
