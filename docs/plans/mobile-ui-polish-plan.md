# Mobile UI Polish Plan

Source: screenshot review of Content Type Selection, TV Shows list, Movie Details,
and Live TV player on mobile. Design system (Deep Night theme, gradient content
cards, glass surfaces, staggered entrance animations) is already solid — these are
concrete data-display bugs undercutting the "pro" look, not a redesign.

## Bugs (fix first)

1. **Rating decimal separator flips mid-app**
   `core/player/src/main/java/org/njarasoa/fijerena/core/player/model/PlaybackFormat.kt:101`
   `formatRating()` uses `"%.1f".format(value)`, which is locale-default. Same
   session shows `9.0` (TV Shows) vs `6,4` (Movies) — reads as a bug when browsing
   both tabs back to back.
   Fix: force `Locale.US` (or `Locale.ROOT`) so ratings always render `X.X`.

2. **Dangling "Ends at" label with no value**
   Movie/episode detail screens render the "Ends at" row unconditionally.
   `computeEndsAt()` (`PlaybackFormat.kt:64`) returns `null` when duration is
   missing or zero, but the caller doesn't hide the row in that case — leaves
   `Ends at` printed with nothing after it.
   Fix: hide the whole "Ends at" row when `computeEndsAt()` returns null.

3. **"0s" duration shown as a real value**
   `formatDuration()` (`PlaybackFormat.kt:83`) formats `duration = "0"`
   (bad/missing provider data) straight into `"0s"` instead of treating it as
   unknown.
   Fix: treat `duration == null || parseDurationToSeconds(duration) <= 0` as
   "unknown" and hide the duration field, same pattern as fix #2.

4. **Blank stream title row**
   TV Shows list has a row rendering as `EN -  (US)` — empty title, provider-side
   bad name field. `StreamCard` (in
   `mobile/src/main/java/org/njarasoa/fijerena/feature/category/MobileCategoryListScreen.kt`)
   has no guard for a blank/whitespace-only `item.name`.
   Fix: fall back to something sane (e.g. category name, or skip the row) when
   `item.name.isBlank()`.

## Consistency / polish (lower priority)

5. **Audit other locale-dependent formatters**
   `formatTime()` and `formatBitrate()` (`PlaybackFormat.kt:6,24`) also use
   `Locale.getDefault()` — same class of bug as #1. Check both for the
   comma-vs-dot flip and fix alongside it.

6. **Category counts shown as raw fraction**
   `ContentTypeSelectionScreen.kt:276` — `showTotal = isDevMode` — but the
   reviewed screenshot shows `901 of 917` on-screen. Confirm dev mode isn't
   accidentally enabled on that device/build; the fraction is meant to be
   dev-only, not shown to end users.

## Out of scope

Poster/thumbnail crop quality (odd face crops, text-only posters) is source
metadata, not app code — not actionable here.
