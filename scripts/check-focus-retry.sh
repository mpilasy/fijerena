#!/bin/bash
# Gate for docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-05.
#
# Since Compose UI 1.10 a FocusRequester whose target isn't attached yet prints a warning and
# returns false; it no longer throws IllegalStateException. So
# `try { requester.requestFocus() } catch (_: IllegalStateException) { retry }` retries nothing,
# and code after the call treats a failed request as a success. TV code lands focus with
# `requestFocusWithRetry` (tv/.../ui/components/input/FocusRetry.kt), which retries on the
# returned Boolean; core:ui loops on `requestFocus(FocusDirection.Enter)` itself.
#
# Fails when a Kotlin main source under tv/ or core/ui/ catches IllegalStateException within 6
# lines after a `requestFocus(` call (comment lines are ignored).
#
# Run locally: scripts/check-focus-retry.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

hits="$(find tv/src/main core/ui/src/main -name '*.kt' -print0 | sort -z | xargs -0 awk '
    FNR == 1 { last = 0 }
    /^[[:space:]]*(\/\/|\*|\/\*)/ { next }
    /requestFocus\(/ { last = FNR }
    (/catch[[:space:]]*\([[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*:[[:space:]]*IllegalStateException/ ||
     /^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*:[[:space:]]*IllegalStateException,?[[:space:]]*\)?/) &&
        last > 0 && FNR - last <= 6 {
        print FILENAME ":" FNR ": catches IllegalStateException after requestFocus( on line " last
    }
')"

if [ -n "$hits" ]; then
    echo "$hits"
    echo
    echo "requestFocus() no longer throws when its target isn't attached: it returns false. Use"
    echo "requestFocusWithRetry (tv/.../ui/components/input/FocusRetry.kt) and act on its result."
    exit 1
fi
echo "Focus retry gate: OK"
