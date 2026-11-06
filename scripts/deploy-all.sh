#!/bin/bash
# Builds every APK the household's devices need in one Gradle run, then installs them on all the
# devices at the same time, each with its own build type.
# Usage: scripts/deploy-all.sh [targets file, default scripts/deploy-targets.local]
#
# The targets file (not in git: it holds the home network's addresses) has one device per line:
#   <app> <variant> <target>
#   app      tv | phone
#   variant  debug | localRelease
#   target   <ip>[:port] (a network TV), a USB serial, emulator:tv or emulator:phone
# "#" starts a comment. See scripts/deploy-targets.example.
#
# As the single-device scripts: a sleeping TV gets Wake-on-LAN (MACs from the ARP cache or
# scripts/wake-macs.local), a device of the wrong kind is skipped, playback is never interrupted
# without asking, and a debuggable install is backed up before it is replaced.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
TARGETS_FILE="${1:-$ROOT_DIR/scripts/deploy-targets.local}"
WAKE_MACS_FILE="${FIJERENA_WAKE_MACS:-$ROOT_DIR/scripts/wake-macs.local}"
BACKUP_DIR="$ROOT_DIR/backups"
LOG_DIR="$(mktemp -d)"

if [ ! -f "$TARGETS_FILE" ]; then
    echo "No targets file at $TARGETS_FILE. Copy scripts/deploy-targets.example there and edit it." >&2
    exit 1
fi

# --- Wake-on-LAN, as in deploy-tv-ip.sh ---

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

WAKE_PIDS=()
trap 'for pid in "${WAKE_PIDS[@]+"${WAKE_PIDS[@]}"}"; do kill "$pid" 2>/dev/null || true; done; rm -rf "$LOG_DIR"' EXIT

# Wakes network TV $1 and waits up to a minute for adb; the packets go on until the script ends.
wake_target() {
    local target="$1" mac broadcast
    mac="$(mac_of "$target")"
    [ -n "$mac" ] || return 1
    broadcast="${target%%:*}"
    broadcast="${broadcast%.*}.255"
    echo "$target not reachable — sending Wake-on-LAN to $mac..."
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

# The running emulator that is ($1 = tv) or is not ($1 = phone) an Android TV.
emulator_of() {
    local want="$1" candidate is_tv
    for candidate in $(adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}'); do
        is_tv=false
        adb -s "$candidate" shell pm has-feature android.software.leanback </dev/null 2>/dev/null | grep -q true && is_tv=true
        if { [ "$want" = tv ] && $is_tv; } || { [ "$want" = phone ] && ! $is_tv; }; then
            echo "$candidate"
            return 0
        fi
    done
    return 1
}

# --- 1. Resolve and check every target before building ---

SERIALS=()
APPS=()
VARIANTS=()
while read -r app variant target _; do
    [[ -z "${app:-}" || "$app" == \#* ]] && continue
    case "$app" in tv | phone) ;; *) echo "Skipping line: unknown app '$app'." >&2 && continue ;; esac
    case "$variant" in debug | localRelease) ;; *) echo "Skipping $target: unknown variant '$variant'." >&2 && continue ;; esac
    serial="$target"
    case "$target" in
        emulator:tv | emulator:phone)
            if ! serial="$(emulator_of "${target#emulator:}")"; then
                echo "No running ${target#emulator:} emulator — skipping." >&2
                continue
            fi
            ;;
        *.*.*.*)
            [[ "$target" == *:* ]] || serial="$target:5555"
            adb connect "$serial" >/dev/null 2>&1 || true
            if [ "$(adb -s "$serial" get-state 2>/dev/null)" != "device" ] && ! wake_target "$serial"; then
                echo "$serial not reachable — skipping. Check it's powered on and on the network." >&2
                continue
            fi
            ;;
        *)
            if [ "$(adb -s "$serial" get-state 2>/dev/null)" != "device" ]; then
                echo "$serial not connected — skipping." >&2
                continue
            fi
            ;;
    esac
    is_tv=false
    adb -s "$serial" shell getprop ro.build.characteristics </dev/null 2>/dev/null | grep -q tv && is_tv=true
    adb -s "$serial" shell pm has-feature android.software.leanback </dev/null 2>/dev/null | grep -q true && is_tv=true
    if { [ "$app" = tv ] && ! $is_tv; } || { [ "$app" = phone ] && $is_tv; }; then
        echo "$serial is not a $app device — skipping, so it never gets the wrong app." >&2
        continue
    fi
    SERIALS+=("$serial")
    APPS+=("$app")
    VARIANTS+=("$variant")
done <"$TARGETS_FILE"

if [ ${#SERIALS[@]} -eq 0 ]; then
    echo "No reachable devices." >&2
    exit 1
fi

pkg_of() { if [ "$1" = tv ]; then echo org.njarasoa.fijerena; else echo org.njarasoa.fijerena.mobile; fi; }
module_of() { if [ "$1" = tv ]; then echo tv; else echo mobile; fi; }

# Ask before interrupting playback, before the build, so a "no" costs nothing.
for i in "${!SERIALS[@]}"; do
    serial="${SERIALS[$i]}"
    pkg="$(pkg_of "${APPS[$i]}")"
    if adb -s "$serial" shell pidof "$pkg" </dev/null >/dev/null 2>&1 &&
        adb -s "$serial" shell "dumpsys media_session | grep -A 8 'package=$pkg'" </dev/null 2>/dev/null | grep -q "state=PlaybackState {state=3"; then
        echo "⚠️  Fijerena is playing on $serial."
        read -rp "Interrupt playback there and deploy? [y/N]: " CONFIRM || CONFIRM=""
        if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
            echo "Deployment aborted by user."
            exit 0
        fi
    fi
done

# --- 2. One build for every APK needed ---

TASKS=()
for i in "${!SERIALS[@]}"; do
    task=":$(module_of "${APPS[$i]}"):assemble${VARIANTS[$i]^}"
    [[ " ${TASKS[*]} " == *" $task "* ]] || TASKS+=("$task")
done
echo "Building: ${TASKS[*]}"
./gradlew "${TASKS[@]}"

# --- 3. Install everywhere at once, each after its own backup ---

command -v sqlite3 >/dev/null || { echo "ERROR: sqlite3 is needed to back up user data (apt install sqlite3)." >&2; exit 1; }
mkdir -p "$BACKUP_DIR"
find "$BACKUP_DIR" -maxdepth 1 \( -name '*.tar' -o -name '*.tar.gz' \) -mtime +7 -print -delete

PIDS=()
for i in "${!SERIALS[@]}"; do
    serial="${SERIALS[$i]}"
    app="${APPS[$i]}"
    variant="${VARIANTS[$i]}"
    (
        pkg="$(pkg_of "$app")"
        module="$(module_of "$app")"
        # `install -r` can wipe the app's data with no warning (see deploy-tv-ip.sh): back up a
        # debuggable install first; a build that isn't debuggable can't be read with run-as.
        if adb -s "$serial" shell pm path "$pkg" </dev/null >/dev/null 2>&1; then
            if adb -s "$serial" shell dumpsys package "$pkg" </dev/null 2>/dev/null | grep -q "pkgFlags=.*DEBUGGABLE"; then
                FIJERENA_PKG="$pkg" "$ROOT_DIR/scripts/backup-app-data.sh" "$serial" \
                    "$BACKUP_DIR/${serial//[:.]/_}-$(date +%Y%m%d-%H%M%S).tar.gz" ||
                    { echo "ERROR: backup failed — not installing rather than risk an unprotected wipe."; exit 1; }
            else
                echo "Not debuggable: no backup possible; an install with the same key keeps the data."
            fi
        fi
        adb -s "$serial" install -r "$module/build/outputs/apk/$variant/$module-$variant.apk" </dev/null
    ) >"$LOG_DIR/$i.log" 2>&1 &
    PIDS+=("$!")
done

FAILED=0
for i in "${!PIDS[@]}"; do
    if wait "${PIDS[$i]}"; then
        echo "✔ ${SERIALS[$i]}: ${APPS[$i]} ${VARIANTS[$i]}"
    else
        FAILED=1
        echo "✘ ${SERIALS[$i]}: ${APPS[$i]} ${VARIANTS[$i]} failed:" >&2
        sed 's/^/    /' "$LOG_DIR/$i.log" >&2
    fi
done
exit "$FAILED"
