#!/bin/bash
# Walk the TV app with D-pad keys and check which node has focus after each one.
# Usage: scripts/tv-focus-walk.sh [-s SERIAL] [-d DELAY_SEC] [-r] WALK_FILE [WALK_FILE...]
#   -s  device serial (default: the first "emulator-*" in `adb devices`)
#   -d  pause after each key, in seconds (default 0.45)
#   -r  record: send the keys, print what was focused, and write that back into the
#       walk file as its expectations instead of asserting
# Walk file: one step per line, `KEY<TAB>expected`. KEY is UP DOWN LEFT RIGHT CENTER BACK
# MENU, or `WAIT <seconds>`. `expected` is a substring of the focused node's text; leave it
# empty to send the key without checking. A first line `@start <substring>` checks focus
# before any key is sent. `#` comments and blank lines are ignored.
# After the first mismatch a check run no longer sends CENTER (the walk is off its path and OK
# would press whatever is focused); record mode replays every key, so record only from a
# start state the check run has matched.
# Focus is read the way the 2026-10-03 audit did: `uiautomator dump`, then the text and
# content-desc of the focused="true" node and its descendants (first four, joined by " / ").
# One driver per device: never run this while anything else is sending keys to it.
set -euo pipefail

usage() { echo "Usage: $0 [-s SERIAL] [-d DELAY_SEC] [-r] WALK_FILE [WALK_FILE...]" >&2; exit 2; }

SERIAL=""
DELAY=0.45
RECORD=false
while getopts "s:d:r" opt; do
    case "$opt" in
        s) SERIAL="$OPTARG" ;;
        d) DELAY="$OPTARG" ;;
        r) RECORD=true ;;
        *) usage ;;
    esac
done
shift $((OPTIND - 1))
[ $# -ge 1 ] || usage

if [ -z "$SERIAL" ]; then
    SERIAL="$(adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1; exit}')"
fi
if [ -z "$SERIAL" ]; then
    echo "No running emulator found. Start one, or pass -s SERIAL." >&2
    exit 1
fi

keycode() {
    case "$1" in
        UP|DOWN|LEFT|RIGHT|CENTER) echo "KEYCODE_DPAD_$1" ;;
        BACK) echo "KEYCODE_BACK" ;;
        MENU) echo "KEYCODE_MENU" ;;
        *) echo "Unknown key '$1'" >&2; return 1 ;;
    esac
}

UI_XML="$(mktemp)"
trap 'rm -f "$UI_XML"' EXIT

focused_text() {
    adb -s "$SERIAL" shell uiautomator dump /sdcard/ui.xml >/dev/null </dev/null
    adb -s "$SERIAL" shell cat /sdcard/ui.xml > "$UI_XML" </dev/null
    python3 - "$UI_XML" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
parts = []
for node in root.iter("node"):
    if node.get("focused") != "true":
        continue
    for n in node.iter("node"):  # the focused node itself, then its descendants
        for attr in ("text", "content-desc"):
            value = (n.get(attr) or "").strip()
            if value and value not in parts:
                parts.append(value)
sys.stdout.reconfigure(encoding="utf-8")
print(" / ".join(parts[:4]) if parts else "(nothing focused)")
PY
}

TOTAL_MISMATCH=0
for walk in "$@"; do
    echo "== $walk (on $SERIAL)"
    step=0
    mismatch=0
    recorded=""
    # After a mismatch the walk is off its path: OK would press whatever is focused instead (an
    # extra Settings row once made it press Shrink Database), so CENTER is no longer sent.
    derailed=false
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        case "$line" in
            ""|"#"*) recorded+="$line"$'\n'; continue ;;
        esac
        expected=""
        if [ "${line%% *}" = "@start" ]; then
            key="@start"
            expected="${line#@start}"; expected="${expected# }"
        else
            key="${line%%$'\t'*}"
            if [ "$key" != "$line" ]; then expected="${line#*$'\t'}"; fi
        fi

        case "$key" in
            @start) ;;
            "WAIT "*) sleep "${key#WAIT }"; step=$((step + 1)) ;;
            CENTER) if [ "$derailed" = true ]; then
                       step=$((step + 1))
                       printf '%-3s %-8s →  %s\n' "$step" "$key" "skipped (after a mismatch)"
                       continue
                   fi
                   adb -s "$SERIAL" shell input keyevent "$(keycode "$key")" </dev/null
                   sleep "$DELAY"
                   step=$((step + 1)) ;;
            *) code="$(keycode "$key")"
               adb -s "$SERIAL" shell input keyevent "$code" </dev/null
               sleep "$DELAY"
               step=$((step + 1)) ;;
        esac
        got="$(focused_text)"

        verdict=""
        if [ "$RECORD" = true ]; then
            verdict="recorded"
            if [ "$key" = "@start" ]; then
                recorded+="@start $got"$'\n'
            else
                recorded+="$key"$'\t'"$got"$'\n'
            fi
        elif [ -n "$expected" ]; then
            if [[ "$got" == *"$expected"* ]]; then
                verdict="OK"
            else
                verdict="MISMATCH (expected \"$expected\")"
                mismatch=$((mismatch + 1))
                derailed=true
            fi
        fi
        printf '%-3s %-8s →  %s%s\n' "$step" "$key" "$got" "${verdict:+  $verdict}"
    done < "$walk"

    if [ "$RECORD" = true ]; then
        printf '%s' "$recorded" > "$walk"
        echo "-- $walk: $step steps recorded"
    else
        echo "-- $walk: $step steps, $mismatch mismatches"
        TOTAL_MISMATCH=$((TOTAL_MISMATCH + mismatch))
    fi
done

if [ "$RECORD" = true ]; then
    exit 0
fi
if [ "$TOTAL_MISMATCH" -gt 0 ]; then
    echo "FAILED: $TOTAL_MISMATCH mismatches"
    exit 1
fi
echo "All walks matched"
