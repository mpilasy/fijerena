#!/bin/bash
# The nightly TV walk on a CI emulator (instrumented-tests.yml → TV walk): installs the debug TV
# app, seeds the test household, opens the app on the profile picker and runs the walks listed in
# scripts/focus-walks/ci-walks.txt. The fake Xtream server (tools/fake-xtream/server.py) must
# already run on the host's port 8090, and tv/build/outputs/apk/debug/tv-debug.apk must exist.
# Output goes to walk-output/: the walk log, and on a failure a screenshot, a UI dump and logcat.
# One file and not the runner's script lines: the emulator runner runs each line on its own.
# Usage: scripts/ci-tv-walk.sh [serial, default emulator-5554]
set -uo pipefail

SERIAL="${1:-emulator-5554}"
PKG=org.njarasoa.fijerena
OUT=walk-output
# A CI emulator draws in software: slower than a desktop one.
DELAY=1.0

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
mkdir -p "$OUT"

save_state() {
    adb -s "$SERIAL" exec-out screencap -p > "$OUT/screen.png" </dev/null || true
    adb -s "$SERIAL" shell uiautomator dump /sdcard/ui.xml >/dev/null </dev/null &&
        adb -s "$SERIAL" shell cat /sdcard/ui.xml > "$OUT/ui.xml" </dev/null || true
    adb -s "$SERIAL" logcat -d > "$OUT/logcat.txt" </dev/null || true
}

run() {
    adb -s "$SERIAL" install -r tv/build/outputs/apk/debug/tv-debug.apk || return 1
    scripts/seed-test-household.sh "$SERIAL" || return 1
    adb -s "$SERIAL" shell am start -n "$PKG/.MainActivity" </dev/null || return 1
    # The first start opens the databases and checks the source: let the profile picker settle.
    sleep 15
    local walks
    walks="$(sed -e 's/#.*//' -e 's/[[:space:]]*$//' -e '/^$/d' scripts/focus-walks/ci-walks.txt)"
    # shellcheck disable=SC2086 # one walk file per word
    scripts/tv-focus-walk.sh -s "$SERIAL" -d "$DELAY" $walks
}

run 2>&1 | tee "$OUT/walk.log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
    save_state
fi
exit "$status"
