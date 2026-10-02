#!/bin/bash
# Gate for docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-27, widened by
# docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-19.
#
# In any main-source Kotlin file that contains `suspend fun` or opens a coroutine lambda
# (`launch`, `async`, `withContext`, `LaunchedEffect`, `produceState`, `flow`/`callbackFlow`,
# `collect`, ... — see SUSPEND_CODE below), a `catch (e: Exception)` (or `catch (_: Exception)`)
# must be immediately preceded, in the same try, by a
# `catch (e: CancellationException) { throw e }` clause — otherwise a cancelled job catches its
# own cancellation and carries on. For runCatching around suspend calls use
# `suspendRunCatching` (core:network).
#
# Escapes:
#   - a `// cancellation-ok: <reason>` comment on the catch line or the line right after it
#     (ktlint moves it there) — non-suspend code (including a plain function in a scanned file,
#     or a try with no suspension point), or swallowing is intended;
#   - a whole file listed in scripts/check-cancellation-allowlist.txt with a reason.
#
# Run locally: scripts/check-cancellation.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ALLOWLIST="scripts/check-cancellation-allowlist.txt"
allowed="$(sed -e 's/#.*//' -e 's/[[:space:]]*$//' -e '/^$/d' "$ALLOWLIST")"

# A file is scanned when it declares a suspend function or opens a coroutine lambda.
SUSPEND_CODE='suspend fun|\b(launch|async|withContext|runBlocking|coroutineScope|supervisorScope|withTimeout(OrNull)?|LaunchedEffect|produceState|flow|channelFlow|callbackFlow|rememberCoroutineScope|collect(Latest)?|onEach)\b *(\(|\{)'

failures=0
while IFS= read -r entry; do
    if [ -n "$entry" ] && [ ! -f "$entry" ]; then
        echo "$ALLOWLIST: $entry no longer exists — remove it."
        failures=$((failures + 1))
    fi
done <<<"$allowed"

while IFS= read -r file; do
    if grep -qxF -- "$file" <<<"$allowed"; then
        continue
    fi
    # A catch passes when the previous catch of the same try (within 12 lines) is the
    # CancellationException one, when its first statement is
    # `if (e is CancellationException) throw e`, or when it carries the cancellation-ok marker.
    hits="$(awk '
        /^[[:space:]]*\/\/ cancellation-ok:/ { pending = ""; next }
        /^[[:space:]]*(\/\/|\*)/ { next }
        pending != "" {
            if ($0 !~ /if \(e is (kotlinx\.coroutines\.)?CancellationException\) throw e/) print pending
            pending = ""
        }
        /catch \((e|_): (kotlinx\.coroutines\.)?CancellationException\)/ { cancelLine = NR; next }
        /catch \((e|_): Exception\)/ {
            if (!(cancelLine > 0 && NR - cancelLine <= 12) && $0 !~ /\/\/ cancellation-ok:/) {
                pending = FILENAME ":" NR ": " $0
            }
        }
        /catch \(/ { cancelLine = 0 }
        END { if (pending != "") print pending }
    ' "$file")"
    if [ -n "$hits" ]; then
        echo "$hits"
        failures=$((failures + 1))
    fi
done < <(grep -rlE --include='*.kt' "$SUSPEND_CODE" core/*/src/main tv/src/main mobile/src/main | sort)

if [ "$failures" -gt 0 ]; then
    echo
    echo "$failures problem(s): catch Exception in suspend code without rethrowing CancellationException."
    echo "Add 'catch (e: CancellationException) { throw e }' before the catch, use suspendRunCatching,"
    echo "or mark a deliberate case with '// cancellation-ok: <reason>' (see $ALLOWLIST)."
    exit 1
fi
echo "Cancellation gate: OK"
