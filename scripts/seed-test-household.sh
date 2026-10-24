#!/bin/bash
# Sets up the test household of the nightly TV walk on a fresh install of the debug TV app: the fake
# Xtream source (tools/fake-xtream/server.py) and the profiles Admin (Expert, in use), Guest (a
# grown-up) and Kid (a child profile). Then it waits until the source's catalogue is loaded.
# Usage: scripts/seed-test-household.sh <serial> [server URL, default http://10.0.2.2:8090]
# The app refuses when it has a source already (logcat: "TestHousehold: refused"): this is for a
# fresh install on an emulator, never a device with data. Debug builds only: a release build has
# no TestHouseholdDebugReceiver and the script times out. Does not open the app.
set -euo pipefail

SERIAL="${1:?usage: $0 <serial> [server URL]}"
URL="${2:-http://10.0.2.2:8090}"
PKG=org.njarasoa.fijerena
TIMEOUT_SEC=300

case "$SERIAL" in
    emulator-*) ;;
    *) echo "Refused: $SERIAL is not an emulator. Seed only a fresh install on an emulator." >&2; exit 1 ;;
esac
if ! adb -s "$SERIAL" shell pm path "$PKG" </dev/null | grep -q '^package:'; then
    echo "$PKG is not installed on $SERIAL." >&2
    exit 1
fi

# Read only this run's lines: logcat since the device's time now.
since="$(adb -s "$SERIAL" shell date +%s </dev/null | tr -d '\r').000"
# A fresh install is in the stopped state until it is opened: without the flag it gets no broadcast.
adb -s "$SERIAL" shell am broadcast --include-stopped-packages -a org.njarasoa.fijerena.DEBUG_TEST_HOUSEHOLD \
    -p "$PKG" --es url "$URL" --es username test --es password test </dev/null >/dev/null

deadline=$((SECONDS + TIMEOUT_SEC))
while [ "$SECONDS" -lt "$deadline" ]; do
    result="$(adb -s "$SERIAL" logcat -t "$since" -s TestHousehold </dev/null | grep -E 'TestHousehold: (done|refused|failed)' | tail -n 1 || true)"
    if [ -n "$result" ]; then
        echo "$result"
        case "$result" in
            *"TestHousehold: done"*) exit 0 ;;
            *) exit 1 ;;
        esac
    fi
    sleep 2
done
echo "Timed out after ${TIMEOUT_SEC}s with no result from TestHousehold (is this a debug build?)." >&2
exit 1
