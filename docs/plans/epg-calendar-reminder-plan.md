# EPG "Add to Calendar" reminder — plan

## Goal

When user taps a matched-but-not-currently-airing program in the EPG Search / browser screen, the existing "Watch now?" dialog gains a third option to add the airing as an event to the device's calendar app (Google Calendar on most devices), so the user gets a native calendar reminder instead of just a channel-switch confirmation.

## Current behavior (confirmed in code)

- The only future-aware prompt in the app today lives in the EPG **browser/search** screens, not the main guide grid (the grid has no future/past distinction at all).
- `AiringRow` (TV) / `MobileAiringRow` (mobile) computes `isOnAir` / `isSoon` and, on click of a *matched* airing that is **not** on-air (future or past), calls `onRequestConfirmation(airing)`, which shows `CinemaAlertDialog`:
  - TV: `tv/src/main/java/org/njarasoa/fijerena/feature/epgbrowser/TvEpgBrowserScreen.kt:893-1042`
  - Mobile: `mobile/src/main/java/org/njarasoa/fijerena/feature/epgbrowser/MobileEpgBrowserScreen.kt:610-748`
- Dialog today: title "Watch now?", message "This show airs at %1$s.\nWatch %2$s now?" (`%2$s` is actually `channelName`, not the program title), one confirm button "Watch now" (tunes the channel regardless of airing time) and "Cancel". Strings: `core/ui/src/main/res/values/strings.xml:560-568` (+ `values-fr`, `values-mg`).
- No reminder/notification DB entity, no `CalendarContract` usage, no `AlarmManager` usage anywhere in the repo today (verified by full-repo grep).

## Chosen approach: delegate to the calendar app via `Intent.ACTION_INSERT`

Rejected alternative: build our own reminder system (new Room entity, `AlarmManager` exact alarm or WorkManager one-off job, new notification channel, `POST_NOTIFICATIONS` runtime permission on Android 13+). That's a lot of new infrastructure for "remind me about a TV airing," and nothing like it exists yet to build on.

Instead, hand the event to whatever calendar app is installed via an implicit `ACTION_INSERT` intent on `CalendarContract.Events.CONTENT_URI`. This:
- Needs **no new permission** (it's a delegated intent, not direct `CalendarContract` provider access).
- Needs **no new DB table, worker, or notification channel**.
- Matches an idiom already in the codebase: `openExternalUrl()` in `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/utils/ExternalLinks.kt:14-26` already does "hand intent to whatever app claims it, toast if nothing does" for external links. We add a sibling function using the same pattern.
- Degrades safely on Android TV boxes that have no calendar app at all (common) — button is hidden if nothing can resolve the intent, no crash.

## Changes

### 1. `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/utils/ExternalLinks.kt`

Add two functions following the existing `openExternalUrl` pattern:

```kotlin
fun canOpenCalendar(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
    return intent.resolveActivity(context.packageManager) != null
}

fun openAddToCalendarEvent(
    context: Context,
    title: String,
    description: String?,
    location: String,
    startEpochSeconds: Long,
    endEpochSeconds: Long,
) {
    val intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description ?: "")
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startEpochSeconds * 1000L)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endEpochSeconds * 1000L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.epg_browser_no_calendar_app), Toast.LENGTH_LONG).show()
    }
}
```

(`canOpenCalendar` is used to hide the button up front on TV boxes with no calendar app; the try/catch stays as a second safety net, same as `openExternalUrl`.)

### 2. Strings — add to `core/ui/src/main/res/values/strings.xml` (near line 568), plus `values-fr/strings.xml` (~line 556) and `values-mg/strings.xml` (~line 467), matching the existing translation coverage for this string block

- `epg_browser_add_calendar_btn` — "Add to calendar"
- `epg_browser_no_calendar_app` — "No calendar app found on this device"

### 3. TV dialog — `tv/.../TvEpgBrowserScreen.kt:893-922`

`program` (title, description) is already in scope in this composable (used above at lines 826/863), so no new parameter plumbing is needed. Restructure the `confirmButton` slot into a stacked column of buttons, following the multi-button-in-one-slot precedent already used in `ImportConflictDialog` (`mobile/.../settings/components/ImportDialogs.kt:97-139`):

```kotlin
confirmButton = {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
        CinemaButton(onClick = {
            pendingConfirmAiring = null
            onNavigateToPlayer(matched.streamId.toString(), matched.streamName, matched.categoryId)
        }) { Text(stringResource(R.string.epg_browser_watch_now_btn)) }

        if (pending.startEpoch > nowEpoch && canOpenCalendar(airingContext)) {
            CinemaButton(onClick = {
                pendingConfirmAiring = null
                openAddToCalendarEvent(
                    context = airingContext,
                    title = program.title,
                    description = program.description,
                    location = pending.channelName,
                    startEpochSeconds = pending.startEpoch,
                    endEpochSeconds = pending.endEpoch,
                )
            }) { Text(stringResource(R.string.epg_browser_add_calendar_btn)) }
        }
    }
}
```

`dismissButton` (Cancel) stays as-is. Gate on `pending.startEpoch > nowEpoch` so the calendar option never appears for a past airing (only "Watch now"/"Cancel" there, unchanged from today).

`nowEpoch` is already in scope at this level (passed down to `AiringRow` on line 885).

### 4. Mobile dialog — `mobile/.../MobileEpgBrowserScreen.kt:610-637`

Same restructuring, using `CinemaDialogTextButton`/`CinemaDialogActionButton` inside a `Column` in `confirmButton`, identical gating (`pending.startEpoch > nowEpoch && canOpenCalendar(...)`).

### 5. Manifest — package-visibility query (both `mobile/src/main/AndroidManifest.xml` and `tv/src/main/AndroidManifest.xml`)

On Android 11+ (API 30+), `PackageManager.resolveActivity`/`queryIntentActivities` can't see other apps' components unless declared. Without this, `canOpenCalendar()` always returns false — confirmed on-device (see manual test log below) — even with Calendar installed, silently hiding the button. Add:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.INSERT" />
        <data android:mimeType="vnd.android.cursor.item/event" />
    </intent>
</queries>
```

No DB or migration changes.

## Manual test plan

1. Mobile, device/emulator with Google Calendar installed: EPG Search → tap a future (not-on-air) matched result → dialog shows "Watch now" / "Add to calendar" / "Cancel" → tap "Add to calendar" → Google Calendar's new-event screen opens pre-filled with program title, time, channel as location → save or discard there, back in app dialog is dismissed.
2. Same, tap "Cancel" → dialog dismisses, nothing else happens.
3. Same, tap "Watch now" → unchanged existing behavior (tunes channel).
4. Past (already-aired) matched result → dialog still shows only "Watch now" / "Cancel" (no regression, no calendar option).
5. TV / emulator with no calendar app installed → dialog shows only "Watch now" / "Cancel" (button hidden via `canOpenCalendar`), D-pad focus still lands cleanly on the two remaining buttons.
6. TV with a calendar app installed (rare, but if any leanback calendar exists) → three-button column, D-pad up/down cycles all three correctly.

## Out of scope / possible follow-up

If the user later wants reminders that fire *inside* the app (a notification at airing time) rather than delegating to an external calendar app, that's a materially bigger feature: new Room entity for scheduled reminders, `AlarmManager.setExactAndAllowWhileIdle` (or WorkManager) to fire it, a new user-facing notification channel, and the `POST_NOTIFICATIONS` runtime permission on Android 13+. Not pursued here — flagging only so it's a deliberate future decision, not an oversight.
