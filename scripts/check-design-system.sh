#!/bin/bash
# Gate for docs/plans/20261016_design-system-plan.md.
#
# Every look comes from the colour theme and Look and Feel through CinemaTheme (core/ui/.../theme)
# and a style class per kind of element; feature code uses components and decides no look itself.
# This counts, per Kotlin file outside the theme and component code, the places that do:
#   - a colour literal (Color(0x…), Color.White / Black / …)
#   - a .dp / .sp literal
#   - a FontWeight, a RoundedCornerShape / CircleShape
#   - MaterialTheme.colorScheme read directly, an ad-hoc text style (typography.x.copy(…))
#   - a raw Material control (Button, TextButton, OutlinedButton, TextField, OutlinedTextField,
#     Switch, Checkbox, RadioButton, FilterChip, AlertDialog, Card, Divider, HorizontalDivider)
#   - a raw Text( — text is CinemaText(role, tone)
#   - a palette colour read directly (CinemaTextSecondary, CinemaAccent, …) — use a role
#
# Each file's count must equal its entry in scripts/check-design-system-allowlist.txt (0 when
# absent). More fails: use a component or a CinemaTheme role. Fewer also fails until the entry is
# lowered, so the list only shrinks; it is empty when the plan is done.
#
# Not counted: theme files (/theme/), the component library (core/ui/.../core/ui/components,
# tv/.../ui/components, mobile/.../ui/components), style classes
# (*Style.kt), comment lines, and lines marked `// look-ok: <reason>` (or the line above).
#
# Run locally: scripts/check-design-system.sh   (--counts prints every file's count)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ALLOWLIST="scripts/check-design-system-allowlist.txt"
PATTERN='Color\(0x|Color\.(White|Black|Red|Gray|Grey|Yellow|Green|Blue|Transparent|Cyan|Magenta|DarkGray|LightGray)\b|\b[0-9]+(\.[0-9]+)?f?\.(dp|sp)\b|FontWeight\.|RoundedCornerShape\(|CircleShape|MaterialTheme\.colorScheme|typography\.[a-zA-Z]+\.copy\(|(^|[^A-Za-z0-9_.])(Button|TextButton|OutlinedButton|TextField|OutlinedTextField|Switch|Checkbox|RadioButton|FilterChip|AlertDialog|Card|Divider|HorizontalDivider|Text)\(|(^|[^A-Za-z0-9_])Cinema(Accent|AccentDark|AccentLight|TextPrimary|TextSecondary|TextTertiary|TextDisabled|Surface|SurfaceVariant|SurfaceLight|Background|Error|Success|Warning|Orange|OrangeLight|OrangeDark|Live|GlassBackground|GlassBorder)([^A-Za-z0-9_]|$)'

count_file() {
    awk -v pat="$PATTERN" '
        /\/\/ look-ok:/ { marked = NR }
        $0 ~ /^[[:space:]]*(\/\/|\*|\/\*)/ { next }
        $0 ~ /^import / { next }
        {
            line = $0
            sub(/\/\/.*/, "", line)
            if (marked > 0 && NR - marked <= 1) next
            while (match(line, pat)) { n++; line = substr(line, RSTART + RLENGTH) }
        }
        END { print n + 0 }
    ' "$1"
}

files() {
    find tv/src/main/java mobile/src/main/java core/ui/src/main/java -name '*.kt' \
        -not -path '*/theme/*' -not -name '*Style.kt' \
        -not -path 'core/ui/src/main/java/org/njarasoa/fijerena/core/ui/components/*' \
        -not -path 'tv/src/main/java/org/njarasoa/fijerena/ui/components/*' \
        -not -path 'mobile/src/main/java/org/njarasoa/fijerena/ui/components/*' | sort
}

if [ "${1:-}" = "--counts" ]; then
    while IFS= read -r f; do
        c="$(count_file "$f")"
        [ "$c" -gt 0 ] && echo "$f $c"
    done < <(files)
    exit 0
fi

allowed="$(sed -e 's/#.*//' -e 's/[[:space:]]*$//' -e '/^$/d' "$ALLOWLIST")"
failures=0
while read -r entry _; do
    if [ -n "$entry" ] && [ ! -f "$entry" ]; then
        echo "$ALLOWLIST: $entry no longer exists — remove it."
        failures=$((failures + 1))
    fi
done <<<"$allowed"

while IFS= read -r file; do
    count="$(count_file "$file")"
    limit="$(awk -v f="$file" '$1 == f { print $2 }' <<<"$allowed")"
    limit="${limit:-0}"
    if [ "$count" -gt "$limit" ]; then
        echo "$file: $count look values, allow-list says $limit — use a component or a CinemaTheme role."
        failures=$((failures + 1))
    elif [ "$count" -lt "$limit" ]; then
        echo "$file: $count look values, allow-list says $limit — lower its entry in $ALLOWLIST."
        failures=$((failures + 1))
    fi
done < <(files)

if [ "$failures" -gt 0 ]; then
    echo
    echo "$failures problem(s). Looks come from CinemaTheme and the style classes (see docs/plans/20261016_design-system-plan.md)."
    exit 1
fi
echo "Design system gate: OK"
