#!/bin/bash
# Gate for docs/plans/20261016_design-system-plan.md.
#
# Every look comes from the colour theme and Look and Feel through CinemaTheme (core/ui/.../theme)
# and a style class per kind of element; feature code uses components and decides no look itself.
# This counts, per Kotlin file outside the theme and component code, the places that do:
#   - a colour literal (Color(0x…), Color.White / Black / …)
#   - a .dp / .sp literal
#   - a FontWeight, a RoundedCornerShape / CircleShape
#   - MaterialTheme.colorScheme or MaterialTheme.typography read directly, an ad-hoc text style
#     (typography.x.copy(…)) — text styles come from textStyleOf(role)
#   - a raw Material control (Button, TextButton, OutlinedButton, TextField, OutlinedTextField,
#     Switch, Checkbox, RadioButton, FilterChip, AlertDialog, Card, Divider, HorizontalDivider)
#   - a raw Text( — text is CinemaText(role, tone); a raw progress indicator — CinemaProgressBar / CinemaSpinner
#   - a palette colour read directly (CinemaTextSecondary, CinemaAccent, …) — use a role
#   - a phone top bar (TopAppBar and its variants, TopAppBarDefaults) — use MobileScreenHeader
#   - dialog buttons laid out by the caller (confirmButton = / dismissButton =): a dialog takes its
#     actions as data and the Look and Feel lays them out (docs/plans/20261101_style-layout-tokens-plan.md)
#   - outside the icon components (ICON_COMPONENTS): a raw IconButton( — phone CinemaIconButton /
#     IconAction, TV TvIconAction / CinemaIconButton; and a colour set on an icon (tint = ,
#     iconTint = ) — an icon's colour is a tone: CinemaIcon(tone = …) (TV TvIcon)
#   - a sheet or dialog window opened directly (ModalBottomSheet, Dialog, BasicAlertDialog,
#     DialogProperties), outside the dialog and sheet components (a file in ui/components named
#     *Dialog*, *Sheet*, *Overlay* or *Panel*): use the phone CinemaSheet, the app's
#     CinemaAlertDialog or TvOverlayPanel, which follow the Look and Feel
#   - outside the components (ui/components, core/ui/components): a raw panel or list row (Surface(,
#     ListItem() and a fill or outline set in place (.background(, .border() — CinemaPanel /
#     Modifier.panel (PanelFill, PanelCorner, PanelOutline), TV TvPanel, TvCard with a TvCardLooks
#     look, the focus modifiers (FocusModifiers.kt), the phone CinemaChoiceRow and SettingsListRow
#     (docs/plans/20261101_style-layout-tokens-plan.md, Phase 5)
#   - an alpha number (alpha = 0.5f, .copy(alpha = 0.5f)) — use a CinemaAlpha token; and a type
#     value set in place (fontSize = , fontSize * , FontFamily.) — use a TextRole, CinemaCodeText for
#     machine text, or the Look's fonts (UiFonts)
# And, anywhere: a button's text with a fixed tone (it must follow the button's colour) —
# scripts/check-button-text-tone.py; the core CinemaAlertDialog used outside each app's own
# CinemaAlertDialog (ui/components/CinemaAlertDialog.kt), which gives it the app's buttons; the
# phone's dialog button (CinemaDialogActionButton) called outside the phone's components: a dialog
# takes its actions as data, a list of actions is rows (CinemaChoiceRow), and a page uses the
# CinemaButton family. Feedback and empty states: a SnackbarHost or a Toast outside ui/components
# (a plain message goes through AppNotices, an Undo through the phone's LocalCinemaSnackbar or
# TvUndoBar); TvUndoBar given an alignment or a padding (it places itself); a CinemaText of a
# *_no_results* / *_empty* string outside the *EmptyState components. Leftover raw Material outside
# ui/components: Icon( (CinemaIcon / TvIcon), Slider( (CinemaSlider), PullToRefreshBox(
# (CinemaPullToRefresh), TooltipBox( (CinemaTooltip); and a raw .clickable / .combinedClickable /
# .toggleable / .selectable in feature code (a row component; `// look-ok: <reason>` for a tap
# target that is not a row).
# Consistency round 2, Phase 4b: text cut to a number of lines (maxLines) without an overflow
# (scripts/check-text-overflow.py: the components clip without "…"); a pane's loading state built
# from MitadyLoading in a large role (TvLoadingState / MobileLoadingState; MitadyLoading stays for
# an inline status line); a raw Material control called by its full name (androidx.compose.material3.
# OutlinedTextField(, …), which the counted patterns above do not see.
#
# Each file's count must equal its entry in scripts/check-design-system-allowlist.txt (0 when
# absent). More fails: use a component or a CinemaTheme role. Fewer also fails until the entry is
# lowered, so the list only shrinks; it is empty when the plan is done.
#
# Not counted: theme files (/theme/) and style classes (*Style.kt, and *Defaults.kt: a component's
# defaults object is its style) — the only places raw values live. The
# component library is counted like feature code: components read the theme too. Also not counted:
# comment lines, and lines marked `// look-ok: <reason>` (or the line above).
#
# Run locally: scripts/check-design-system.sh   (--counts prints every file's count)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ALLOWLIST="scripts/check-design-system-allowlist.txt"
PATTERN='Color\(0x|Color\.(White|Black|Red|Gray|Grey|Yellow|Green|Blue|Transparent|Cyan|Magenta|DarkGray|LightGray)([^A-Za-z0-9_]|$)|(^|[^A-Za-z0-9_.])[0-9]+(\.[0-9]+)?f?\.(dp|sp)([^A-Za-z0-9_]|$)|FontWeight\.|RoundedCornerShape\(|CircleShape|MaterialTheme\.colorScheme|MaterialTheme\.typography|typography\.[a-zA-Z]+\.copy\(|(^|[^A-Za-z0-9_.])(Button|TextButton|OutlinedButton|TextField|OutlinedTextField|Switch|Checkbox|RadioButton|FilterChip|AlertDialog|Card|Divider|HorizontalDivider|Text|CircularProgressIndicator|LinearProgressIndicator)\(|(^|[^A-Za-z0-9_])(confirmButton|dismissButton)[[:space:]]*=|(^|[^A-Za-z0-9_])Cinema(Accent|AccentDark|AccentLight|TextPrimary|TextSecondary|TextTertiary|TextDisabled|Surface|SurfaceVariant|SurfaceLight|Background|Error|Success|Warning|Orange|OrangeLight|OrangeDark|Live|GlassBackground|GlassBorder)([^A-Za-z0-9_]|$)'
# Phone top bars: MobileScreenHeader is the only one (docs/plans/20261101_style-layout-tokens-plan.md, Phase 3).
TOP_BAR_PATTERN='(^|[^A-Za-z0-9_.])(TopAppBar|CenterAlignedTopAppBar|MediumTopAppBar|LargeTopAppBar)\(|TopAppBarDefaults'
PATTERN="$PATTERN|$TOP_BAR_PATTERN"
# Alpha numbers and type values set in place (same plan, Phase 6). An alpha computed from a value
# (alpha = 1f - open) is not counted: only a number that ends the argument.
ALPHA_TYPE_PATTERN='(^|[^A-Za-z0-9_])alpha[[:space:]]*=[[:space:]]*[0-9.]+f?[[:space:]]*([,)}]|$)|(^|[^A-Za-z0-9_])fontSize[[:space:]]*(=([^=]|$)|\*)|(^|[^A-Za-z0-9_])FontFamily\.'
PATTERN="$PATTERN|$ALPHA_TYPE_PATTERN"
# Sheets and dialog windows: only the dialog and sheet components open one (same plan, Phase 2).
WINDOW_PATTERN='(^|[^A-Za-z0-9_.])(ModalBottomSheet|Dialog|BasicAlertDialog|DialogProperties)\('
WINDOW_COMPONENT='/ui/components/[^/]*(Dialog|Sheet|Overlay|Panel)[^/]*\.kt$'

# Raw icon buttons and icon colours: counted outside the icon components only.
ICON_PATTERN='(^|[^A-Za-z0-9_.])IconButton\(|(^|[^A-Za-z0-9_])(tint|iconTint)[[:space:]]*=([^=]|$)'
ICON_COMPONENTS=(
    core/ui/src/main/java/org/njarasoa/fijerena/core/ui/components/CinemaIcon.kt
    mobile/src/main/java/org/njarasoa/fijerena/ui/components/buttons/IconAction.kt
    tv/src/main/java/org/njarasoa/fijerena/ui/components/buttons/TvIconAction.kt
)

# Raw panels, list rows, fills and outlines: counted outside the component folders only.
PANEL_PATTERN='(^|[^A-Za-z0-9_])(Surface|ListItem)\(|\.(background|border)\('

is_component() {
    case "$1" in
    */ui/components/* | core/ui/src/main/java/org/njarasoa/fijerena/core/ui/components/*) return 0 ;;
    *) return 1 ;;
    esac
}

count_file() {
    local pat="$PATTERN|$ICON_PATTERN"
    local c
    for c in "${ICON_COMPONENTS[@]}"; do
        [ "$1" = "$c" ] && pat="$PATTERN"
    done
    [[ "$1" =~ $WINDOW_COMPONENT ]] || pat="$pat|$WINDOW_PATTERN"
    is_component "$1" || pat="$pat|$PANEL_PATTERN"
    # The pattern goes in through the environment: awk -v would strip its backslashes (gawk does).
    PAT="$pat" awk '
        BEGIN { pat = ENVIRON["PAT"] }
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
        -not -path '*/theme/*' -not -name '*Style.kt' -not -name '*Defaults.kt' | sort
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

# A button's text follows the button's colour (scripts/check-button-text-tone.py).
if ! python3 "$(dirname "$0")/check-button-text-tone.py" tv/src/main mobile/src/main core/ui/src/main; then
    failures=$((failures + 1))
fi

# Text cut to a number of lines ends with an ellipsis (scripts/check-text-overflow.py).
if ! python3 "$(dirname "$0")/check-text-overflow.py" tv/src/main mobile/src/main core/ui/src/main; then
    failures=$((failures + 1))
fi

# Feature code calls its app's CinemaAlertDialog; only that one calls the core dialog.
while IFS= read -r file; do
    echo "$file: uses the core CinemaAlertDialog — use the app's own (ui/components/CinemaAlertDialog.kt)."
    failures=$((failures + 1))
done < <(grep -rl --include='*.kt' 'org\.njarasoa\.fijerena\.core\.ui\.components\.CinemaAlertDialog' tv/src/main mobile/src/main |
    grep -v '/ui/components/CinemaAlertDialog\.kt$' || true)

# The phone's dialog button is drawn only by the components (the app dialog and the CinemaButton family).
while IFS= read -r hit; do
    echo "$hit: CinemaDialogActionButton outside ui/components — give the dialog DialogAction data, use rows for a list of actions, or the CinemaButton family on a page."
    failures=$((failures + 1))
done < <(grep -rn --include='*.kt' 'CinemaDialogActionButton(' mobile/src/main | grep -v '/ui/components/' | cut -d: -f1,2 || true)

# Feedback and empty states (docs/plans/20261101_consistency-round-2-plan.md, Phase 3). Lines are
# file:line hits of Kotlin code (not comments, not lines marked `// look-ok: <reason>` or under one).
code_hits() {
    local pat="$1"
    shift
    find "$@" -name '*.kt' | sort | while IFS= read -r f; do
        PAT="$pat" awk '
            BEGIN { pat = ENVIRON["PAT"] }
            /\/\/ look-ok:/ { marked = FNR }
            $0 ~ /^[[:space:]]*(\/\/|\*|\/\*)/ { next }
            $0 ~ /^import / { next }
            {
                line = $0
                sub(/\/\/.*/, "", line)
                if (marked > 0 && FNR - marked <= 1) next
                if (line ~ pat) print FILENAME ":" FNR
            }
        ' "$f"
    done
}

# One snackbar host per app and no Toast: a plain message goes through AppNotices, an Undo through
# the phone's LocalCinemaSnackbar or the TV's TvUndoBar; only the components draw a host.
while IFS= read -r hit; do
    echo "$hit: SnackbarHost or Toast outside ui/components — send AppNotices.show(text), or an Undo to LocalCinemaSnackbar (phone) / TvUndoBar (TV)."
    failures=$((failures + 1))
done < <(code_hits '(^|[^A-Za-z0-9_])SnackbarHost\(|Toast[[:space:]]*\.makeText|^[[:space:]]*\.makeText\(' tv/src/main/java mobile/src/main/java core/ui/src/main/java |
    grep -v '/ui/components/' || true)

# TvUndoBar places itself, under the list in the screen's column: callers give it no alignment or padding.
while IFS= read -r hit; do
    echo "$hit: TvUndoBar given an alignment or a padding — it places itself; put it in the column under the list."
    failures=$((failures + 1))
done < <(code_hits 'TvUndoBar\(.*\.(align|padding)\(' tv/src/main/java || true)

# "Nothing here" is MobileEmptyState / TvEmptyState: no CinemaText of a *_no_results* / *_empty*
# string outside the *EmptyState components (the string on the CinemaText's line or in its call).
while IFS= read -r hit; do
    echo "$hit: an empty-state text built by hand — use MobileEmptyState (phone) or TvEmptyState (TV)."
    failures=$((failures + 1))
done < <(find tv/src/main/java mobile/src/main/java -name '*.kt' -not -name '*EmptyState*' | sort | while IFS= read -r f; do
    awk '
        $0 ~ /^[[:space:]]*(\/\/|\*|\/\*)/ { next }
        /(^|[^A-Za-z0-9_])CinemaText\(/ { open = FNR }
        open > 0 && /R\.string\.[a-z0-9_]*(no_results|_empty)(_[a-z_]*format|_phone)?([^a-z0-9_]|$)/ { print FILENAME ":" FNR; open = 0; next }
        # The call ends: on its own line, or on the CinemaText line itself.
        open == FNR && /\)[[:space:]]*,?[[:space:]]*$/ { open = 0 }
        open > 0 && FNR > open && (/^[[:space:]]*\)/ || FNR - open > 8) { open = 0 }
    ' "$f"
done || true)

# Dialog bodies and menus (docs/plans/20261101_consistency-round-2-plan.md, Phase 1), outside the
# components (ui/components):
#   - a button in a CinemaAlertDialog( call (its body): the dialog takes its buttons as data
#     (main, others, danger, cancel); a list of actions is an action menu (phone CinemaActionMenu,
#     TV TvActionMenu). A form's own control (an inline Add) is marked `// look-ok: <reason>` on its line.
#   - a colour given to a dialog (containerColor =, titleContentColor =, textContentColor = in a
#     CinemaAlertDialog( call): the dialog takes its colours from the theme.
#   - a Cancel row (onClick = onDismiss within 3 lines of a TvInputListItem( / TvRadioRow( call):
#     Cancel is the dialog's cancel action, a button.
#   - a raw drop-down (DropdownMenu, DropdownMenuItem, ExposedDropdownMenu…): a menu of actions is an
#     action menu, one choice of a list is a picker dialog.
DIALOG_BUTTON='(^|[^A-Za-z0-9_])(Cinema(Outlined|Text|Primary|Secondary|Danger)?Button|CinemaTextLabelButton|ProviderDangerButton|TvSelectableButton)\('
DIALOG_COLOUR='(^|[^A-Za-z0-9_])(containerColor|titleContentColor|textContentColor)[[:space:]]*='
while IFS= read -r file; do
    is_component "$file" && continue
    # Tracks the bracket depth from each CinemaAlertDialog( call to its closing bracket. Strings
    # and comments are taken out of the line first, so their brackets do not count.
    BTN="$DIALOG_BUTTON" COL="$DIALOG_COLOUR" awk '
        BEGIN { btn = ENVIRON["BTN"]; col = ENVIRON["COL"] }
        {
            raw = $0
            line = $0
            gsub(/"([^"\\]|\\.)*"/, "\"\"", line)
            sub(/\/\/.*/, "", line)
            if (line ~ /^[[:space:]]*(\*|\/\*)/) next
            start = 0
            if (depth == 0 && match(line, /(^|[^A-Za-z0-9_])CinemaAlertDialog\(/)) {
                start = 1
                line = substr(line, RSTART + RLENGTH - 1)
            }
            if (depth > 0 || start) {
                if (raw !~ /\/\/ look-ok:/ && line ~ btn) printf "%s:%d: a button in a dialog body — give the dialog DialogAction data, or use an action menu.\n", FILENAME, FNR
                if (line ~ col) printf "%s:%d: a colour given to a dialog — the dialog takes its colours from the theme.\n", FILENAME, FNR
                n = length(line)
                for (i = 1; i <= n; i++) {
                    c = substr(line, i, 1)
                    if (c == "(") depth++
                    else if (c == ")") depth--
                }
                if (depth < 0) depth = 0
            }
        }
    ' "$file"
done < <(find tv/src/main/java mobile/src/main/java -name '*.kt' | sort) >"${TMPDIR:-/tmp}/dialog-gate.$$"
while IFS= read -r hit; do
    echo "$hit"
    failures=$((failures + 1))
done <"${TMPDIR:-/tmp}/dialog-gate.$$"
rm -f "${TMPDIR:-/tmp}/dialog-gate.$$"

while IFS= read -r hit; do
    echo "$hit: a Cancel row — Cancel is the dialog's cancel action (DialogAction), not a row."
    failures=$((failures + 1))
done < <(grep -rn -A3 --include='*.kt' -E '(^|[^A-Za-z0-9_])(TvInputListItem|TvRadioRow)\(' tv/src/main |
    grep -E 'onClick[[:space:]]*=[[:space:]]*onDismiss([^A-Za-z0-9_]|$)' | grep -v '/ui/components/' |
    sed -E 's/^([^:]+)-([0-9]+)-.*/\1:\2/' || true)

while IFS= read -r hit; do
    echo "$hit: a raw drop-down — use an action menu (CinemaActionMenu, TvActionMenu) or a picker dialog."
    failures=$((failures + 1))
done < <(grep -rn --include='*.kt' -E '(^|[^A-Za-z0-9_.])(DropdownMenu(Item)?|ExposedDropdownMenu(Box)?)\(' tv/src/main mobile/src/main |
    grep -v '/ui/components/' | cut -d: -f1,2 || true)
# Leftover raw Material (docs/plans/20261101_consistency-round-2-plan.md, Phase 4): an icon is
# CinemaIcon / TvIcon with a tone, the phone's slider CinemaSlider, pull to refresh
# CinemaPullToRefresh, a tooltip CinemaTooltip (or IconAction, which has one); only ui/components
# call the Material ones.
while IFS= read -r hit; do
    echo "$hit: raw Icon, Slider, PullToRefreshBox or TooltipBox outside ui/components — use CinemaIcon / TvIcon (with a tone), CinemaSlider, CinemaPullToRefresh or CinemaTooltip."
    failures=$((failures + 1))
done < <(code_hits '(^|[^A-Za-z0-9_.])(Icon|Slider|PullToRefreshBox|TooltipBox)\(' tv/src/main/java mobile/src/main/java core/ui/src/main/java |
    grep -v '/ui/components/' || true)

# A tappable row is a row component (phone CinemaRow, SettingsListRow, CinemaChoiceRow, a
# CinemaCard with onClick; TV TvCard with a TvCardLooks look), never a raw clickable,
# combinedClickable, toggleable or selectable in feature code. A tap target that is not a row (a
# whole-screen tap catcher, a guide cell, a dock tab) is marked `// look-ok: <reason>`.
while IFS= read -r hit; do
    echo "$hit: a raw clickable / combinedClickable / toggleable / selectable outside ui/components — use CinemaRow, SettingsListRow, CinemaChoiceRow, CinemaCard(onClick) or TvCard; mark a tap target that is not a row with // look-ok: <reason>."
    failures=$((failures + 1))
done < <(code_hits '\.(clickable|combinedClickable|toggleable|selectable)([^A-Za-z0-9_]|$)' tv/src/main/java mobile/src/main/java core/ui/src/main/java |
    grep -v '/ui/components/' || true)

# Phone lists end clear of the bottom bar: a phone page (a file that calls MobileScreenHeader) with
# a scrolling list or column (LazyColumn, LazyVerticalGrid, verticalScroll) ends it with
# LocalDockInset of space. The check is per file: a file with a scroll that is not under the bar (a
# sheet's text) carries `// dock-ok: <reason>`.
while IFS= read -r file; do
    echo "$file: a phone page with a scrolling list and no LocalDockInset — end the list with LocalDockInset.current of space (contentPadding or padding), or mark a scroll that is not under the bar with // dock-ok: <reason>."
    failures=$((failures + 1))
done < <(find mobile/src/main/java -name '*.kt' -not -path '*/ui/components/*' | sort | while IFS= read -r f; do
    if grep -qE '(^|[^A-Za-z0-9_])(LazyColumn|LazyVerticalGrid|LazyVerticalStaggeredGrid|verticalScroll)\(' "$f" &&
        grep -q 'MobileScreenHeader(' "$f" && ! grep -q 'LocalDockInset' "$f" && ! grep -q '// dock-ok:' "$f"; then
        echo "$f"
    fi
done)

# Choice and toggle rows (docs/plans/20261101_consistency-round-2-plan.md, Phase 2):
#   - a check box, radio or switch outside the components (ui/components, core/ui/components): a
#     row of options is a toggle row (phone CinemaCheckRow / CinemaRadioRow / CinemaSwitchRow, TV
#     TvCheckRow / TvRadioRow / TvSwitchRow), the whole row tappable. A card's own control is marked
#     `// look-ok: <reason>` on its line or the line above.
#   - TvOptionRow: retired; TvRadioRow is the TV's one single-choice row.
#   - CinemaChoiceRow( with a trailing content lambda (best effort: `) {` on the call's line or on
#     the line that closes its arguments): the row takes its label, icon and supporting text as data.
while IFS= read -r hit; do
    echo "$hit: a check box, radio or switch built into a row by hand — use CinemaCheckRow / CinemaRadioRow / CinemaSwitchRow (phone) or TvCheckRow / TvRadioRow / TvSwitchRow (TV)."
    failures=$((failures + 1))
done < <(code_hits '(^|[^A-Za-z0-9_])(CinemaCheckbox|CinemaRadioButton|CinemaSwitch)\(' tv/src/main/java mobile/src/main/java core/ui/src/main/java |
    grep -v '/ui/components/' || true)

while IFS= read -r hit; do
    echo "$hit: TvOptionRow is retired — use TvRadioRow, the one single-choice row."
    failures=$((failures + 1))
done < <(code_hits '(^|[^A-Za-z0-9_])TvOptionRow\(' tv/src/main/java || true)

while IFS= read -r hit; do
    echo "$hit: CinemaChoiceRow given content — give it label, icon and supportingText as data."
    failures=$((failures + 1))
done < <(find mobile/src/main/java -name '*.kt' | sort | while IFS= read -r f; do
    awk '
        $0 ~ /^[[:space:]]*(\/\/|\*|\/\*)/ { next }
        /(^|[^A-Za-z0-9_])CinemaChoiceRow\(/ && !/fun CinemaChoiceRow/ {
            open = FNR
            if ($0 ~ /\)[[:space:]]*\{[[:space:]]*$/) { print FILENAME ":" FNR; open = 0 }
            next
        }
        open > 0 && /^[[:space:]]*\)[[:space:]]*\{[[:space:]]*$/ { print FILENAME ":" FNR; open = 0; next }
        open > 0 && /^[[:space:]]*\)[[:space:]]*$/ { open = 0 }
        open > 0 && FNR - open > 40 { open = 0 }
    ' "$f"
done || true)

# A pane's loading state is TvLoadingState / MobileLoadingState: MitadyLoading only as an inline
# status line (TextRole.Body), never in a large role (consistency round 2, finding 33).
while IFS= read -r hit; do
    echo "$hit: a loading state built from MitadyLoading in a large role — use TvLoadingState (TV) or MobileLoadingState (phone)."
    failures=$((failures + 1))
done < <(find tv/src/main/java mobile/src/main/java -name '*.kt' | sort | while IFS= read -r f; do
    awk '
        $0 ~ /^[[:space:]]*(\/\/|\*|\/\*)/ { next }
        /(^|[^A-Za-z0-9_])MitadyLoading\(/ { open = FNR }
        open > 0 && /TextRole\.(Display|Headline|Title|Heading)/ { print FILENAME ":" open; open = 0; next }
        open > 0 && (/\)[[:space:]]*$/ || FNR - open > 6) { open = 0 }
    ' "$f"
done || true)

# A raw Material control called by its full name escapes the counted patterns: none in feature or
# component code (the core components that wrap one mark it `// look-ok:`).
while IFS= read -r hit; do
    echo "$hit: a raw Material control by its full name — use the component (CinemaTextField, CinemaSpinner, CinemaProgressBar, …)."
    failures=$((failures + 1))
done < <(code_hits 'androidx\.compose\.material3\.(Button|TextButton|OutlinedButton|TextField|OutlinedTextField|Switch|Checkbox|RadioButton|FilterChip|AlertDialog|Card|HorizontalDivider|Text|Icon|Slider|CircularProgressIndicator|LinearProgressIndicator)\(' tv/src/main/java mobile/src/main/java core/ui/src/main/java || true)

# No marquee on the phone: touch has no focus to end one, so it scrolls for as long as the text
# shows and cuts its start; a long text ends with "…". A marquee is for TV focus only (phone walk A, item 5).
while IFS= read -r hit; do
    echo "$hit: a marquee on the phone — let the text end with \"…\" (TextOverflow.Ellipsis); bounceMarquee is for TV focus only."
    failures=$((failures + 1))
done < <(code_hits '(bounceMarquee|basicMarquee)\(' mobile/src/main/java || true)

# A Settings row says what it does as data (SettingsRowKind): the chevron is for a row that opens
# a page or a picker only. An empty trailing slot to hide it (the old way) is not allowed.
while IFS= read -r hit; do
    echo "$hit: an empty trailing slot to hide a Settings row's chevron — give kind = SettingsRowKind.Action."
    failures=$((failures + 1))
done < <(code_hits 'trailing = \{\}' mobile/src/main/java tv/src/main/java || true)

# Play and Trailer show the play icon, never a "▶" typed into a label (final phone walk, item 9).
while IFS= read -r hit; do
    echo "$hit: a ▶ glyph in a string — show the play icon (PlayButtonLabel on the phone, CinemaPrimaryButton(icon = CinemaIcons.PlayArrow) on TV)."
    failures=$((failures + 1))
done < <(grep -n '▶' core/*/src/main/res/values*/strings.xml tv/src/main/res/values*/strings.xml mobile/src/main/res/values*/strings.xml 2>/dev/null || true)

# A phone page gives MobileScreenHeader its title as text (title =, subtitle =), so the Look sets
# where it goes: the content form (a whole bar of its own) is for a tab root only
# (MobileSourceTopBar.kt). A page that built its own title there was left at the start of the bar.
while IFS= read -r hit; do
    echo "$hit: MobileScreenHeader without a title — give title (and subtitle) as text; the content form is for a tab root only."
    failures=$((failures + 1))
done < <(python3 - mobile/src/main/java <<'PY' || true
import pathlib
import re
import sys

ALLOWED = {"MobileSourceTopBar.kt", "MobileScreenHeader.kt"}
for path in sorted(pathlib.Path(sys.argv[1]).rglob("*.kt")):
    if path.name in ALLOWED:
        continue
    text = path.read_text(encoding="utf-8")
    for m in re.finditer(r"(?<![A-Za-z0-9_.])MobileScreenHeader\s*([({])", text):
        args = ""
        if m.group(1) == "(":
            depth, i = 1, m.end()
            while i < len(text) and depth:
                depth += {"(": 1, ")": -1}.get(text[i], 0)
                i += 1
            args = text[m.end():i]
        if not re.search(r"(?<![A-Za-z0-9_])title\s*=", args):
            print(f"{path}:{text.count(chr(10), 0, m.start()) + 1}")
PY
)

# A menu's "Mark as watched" / "Mark as unwatched" has one icon each on both apps
# (CinemaIcons.MarkWatched / MarkUnwatched; final TV walk, item 10).
while IFS= read -r hit; do
    echo "$hit: a watched action in a menu with its own icon — use CinemaIcons.MarkWatched / CinemaIcons.MarkUnwatched."
    failures=$((failures + 1))
done < <(python3 - tv/src/main/java mobile/src/main/java <<'PY' || true
import pathlib
import re
import sys

for root in sys.argv[1:]:
    for path in sorted(pathlib.Path(root).rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for m in re.finditer(r"(?<![A-Za-z0-9_.])MenuAction\(", text):
            depth, i = 1, m.end()
            while i < len(text) and depth:
                depth += {"(": 1, ")": -1}.get(text[i], 0)
                i += 1
            args = text[m.end():i]
            if not re.search(r"R\.string\.watched_(un)?mark", args):
                continue
            icons = set(re.findall(r"CinemaIcons\.([A-Za-z]+)", args))
            if icons - {"MarkWatched", "MarkUnwatched"}:
                print(f"{path}:{text.count(chr(10), 0, m.start()) + 1}")
PY
)

if [ "$failures" -gt 0 ]; then
    echo
    echo "$failures problem(s). Looks come from CinemaTheme and the style classes (see docs/plans/20261016_design-system-plan.md)."
    exit 1
fi
echo "Design system gate: OK"
