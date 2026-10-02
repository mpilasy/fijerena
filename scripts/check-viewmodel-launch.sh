#!/bin/bash
# Gate for docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-09.
#
# An exception escaping a bare `viewModelScope.launch` crashes the app (and, in an init block,
# can leave the screen spinning forever). ViewModel coroutines under core/ui/.../viewmodels use
# `launchGuarded` (core/ui/.../utils/LaunchGuarded.kt) instead: it rethrows cancellation, logs and
# records anything else in CrashLog, and hands it to an onError that sets the screen's error state.
#
# Each file's count of bare `viewModelScope.launch` calls must equal its entry in
# scripts/check-viewmodel-launch-allowlist.txt (0 when absent). More fails: use launchGuarded.
# Fewer also fails until the entry is lowered, so the list only shrinks.
#
# Escapes:
#   - a `// launch-ok: <reason>` comment on the launch line or the line right above it — a
#     coroutine that already catches everything itself, or must not be guarded;
#   - the per-file counts in the allow-list (legacy sites).
#
# Run locally: scripts/check-viewmodel-launch.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ALLOWLIST="scripts/check-viewmodel-launch-allowlist.txt"
allowed="$(sed -e 's/#.*//' -e 's/[[:space:]]*$//' -e '/^$/d' "$ALLOWLIST")"

failures=0
while read -r entry _; do
    if [ -n "$entry" ] && [ ! -f "$entry" ]; then
        echo "$ALLOWLIST: $entry no longer exists — remove it."
        failures=$((failures + 1))
    fi
done <<<"$allowed"

while IFS= read -r file; do
    count="$(awk '
        /\/\/ launch-ok:/ { marked = NR }
        /viewModelScope\.launch([^A-Za-z0-9_]|$)/ && $0 !~ /^[[:space:]]*(\/\/|\*)/ && !(marked > 0 && NR - marked <= 1) { n++ }
        END { print n + 0 }
    ' "$file")"
    limit="$(awk -v f="$file" '$1 == f { print $2 }' <<<"$allowed")"
    limit="${limit:-0}"
    if [ "$count" -gt "$limit" ]; then
        echo "$file: $count bare viewModelScope.launch, allow-list says $limit — use launchGuarded."
        failures=$((failures + 1))
    elif [ "$count" -lt "$limit" ]; then
        echo "$file: $count bare viewModelScope.launch, allow-list says $limit — lower its entry in $ALLOWLIST."
        failures=$((failures + 1))
    fi
done < <(find core/ui/src/main/java -path '*/viewmodels/*' -name '*.kt' | sort)

if [ "$failures" -gt 0 ]; then
    echo
    echo "$failures problem(s). New ViewModel coroutines use launchGuarded (core/ui/.../utils/LaunchGuarded.kt);"
    echo "mark a deliberate bare launch with '// launch-ok: <reason>' (see $ALLOWLIST)."
    exit 1
fi
echo "ViewModel launch gate: OK"
