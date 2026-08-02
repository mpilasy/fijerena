# Phone landscape Live TV preview — match TV split layout

## Context

Scope assumption (stated up front so it's easy to correct): this is about the **Live TV docked preview** in `MobileCategoryListScreen.kt` — the small always-playing preview + "last watched" channel list that appears when a Live TV channel is tapped. Its own code comments already say it "mirrors TV's LiveTvSplitLayout.kt" (`tv/.../LiveTvSplitLayout.kt`), and the user's phrasing ("TV preview screen", "last viewed panel on right", "in addition to the portrait preview") lines up exactly with the existing portrait/landscape split in that file. Not about Continue Watching rows on Movies/TV Shows, which is a separate, already-shipped feature.

Today, `MobileCategoryListScreen.kt` already has a landscape branch (`isWideLayout`, gated on `screenWidthDp >= 600`) that puts the video pane on the left and the channel list on the right — structurally already "last-viewed panel on right". Two things fall short of "as close to TV as possible" and of being a real *phone*-landscape feature rather than a tablet-only one:

1. **Trigger condition is width-based, not orientation-based.** `screenWidthDp >= 600` happens to catch most phones once rotated (landscape width is usually >600dp), but it's an accident of typical phone dimensions, not an intentional landscape check — it'll misbehave on small phones/foldables and doesn't clearly express intent.
2. **Visual proportions and polish don't match TV.** TV uses a 0.66/0.34 weight split with a true 16:9 video box; mobile's landscape branch uses 0.5/0.5 and a fixed `120.dp`-tall box (a documented workaround for an `aspectRatio()` mis-measurement bug in that exact spot). Mobile's landscape branch also has no "still loading" spinner overlay (TV has one) and no close (X) button (portrait's docked box has one, landscape's doesn't).

## Orientation: portrait-locked everywhere except the docked preview

Follow-up requirement from the user: rotation should follow the phone's physical orientation *only* while the Live TV preview is docked — everywhere else in the mobile app stays portrait.

Current actual behavior doesn't match this. The manifest (`mobile/src/main/AndroidManifest.xml:27`) sets `android:screenOrientation="user"` on `MainActivity`, which lets the **entire app** rotate freely whenever the device's OS-level auto-rotate is on — not just the preview. `MobilePlayerScreen.kt`'s existing comment ("even though the rest of the app is portrait-locked") describes the *intended* design (matches AGENTS.md: "mobile: portrait-locked"), but the manifest doesn't actually enforce it today. `MobilePlayerContent` (used for the full-screen player, including promoting the dock) already force-locks to `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` on mount and restores `activity.requestedOrientation` on dispose (`MobilePlayerScreen.kt:188-194`) — that part is fine and unaffected by this change.

Two changes:

1. **`mobile/src/main/AndroidManifest.xml`**: change `android:screenOrientation="user"` to `"portrait"`. This makes portrait the real, enforced default everywhere, matching what the codebase already assumes. Runtime `requestedOrientation` overrides (like the player's) still work exactly as before — `activity.requestedOrientation` will now correctly read back `portrait` as the "original" to restore to, instead of `user`.

2. **`MobileCategoryListScreen.kt`**: add a `DisposableEffect` that mirrors the player's pattern, scoped to `isLiveTv && target != null` (i.e. the preview is docked, portrait or landscape branch, promoted-to-fullscreen or not — since the fullscreen return happens later in the function, this effect must be placed *before* that early `return` so it stays mounted through promote/demote):
   ```kotlin
   val activity = context as? Activity  // context already resolved above via LocalContext.current
   if (isLiveTv && target != null) {
       DisposableEffect(activity) {
           val original = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
           activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
           onDispose { activity?.requestedOrientation = original }
       }
   }
   ```
   Use `SCREEN_ORIENTATION_USER` (not `SENSOR`) here — unlike the full-screen player, which deliberately forces landscape even if the OS auto-rotate toggle is off, the preview dock should *respect* that toggle (it's still a browsing surface, not a deliberate "watch" commitment) and just stop pinning it to portrait while docked.
   - This nests correctly with `MobilePlayerContent`'s own effect during promote: when the dock promotes to full screen, that effect captures `original = USER` (what the dock just set) and restores to `USER` on demote; when the dock itself closes, this effect restores to `portrait`.
   - This effect is what actually makes device rotation reach the landscape branch below at all (Compose recomposes `LocalConfiguration` live on rotation), so it must land together with the layout changes, not separately.

## Changes — `mobile/.../feature/category/MobileCategoryListScreen.kt`

All changes are inside the existing `if (isLiveTv && target != null && isWideLayout) { ... }` branch (~line 574-657) plus the condition itself. No new files, no changes to the portrait branch, no changes to TV code.

1. **Landscape detection.** Replace/supplement the `screenWidthDp >= 600` check with an explicit orientation signal: `LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }`. Keep the `isWideLayout` name only if it still reads correctly, otherwise rename to `isLandscape` at the call site — this is a same-file, single-use variable so the rename is free.

2. **Match TV's proportions.** Change `Modifier.weight(0.5f)` on both columns to `0.66f` (video/EPG pane) and `0.34f` (channel list), matching `LiveTvSplitLayout.kt` lines 388/509.

3. **Real 16:9 video box without re-triggering the known layout bug.** The existing comment (lines 588-595) documents that `Modifier.aspectRatio(16f/9f)` mis-measures in this exact spot (weighted+fillMaxHeight column after preceding siblings in the parent `Column`). Instead of retrying `aspectRatio()`, wrap the video `Box` in `BoxWithConstraints` and compute the height explicitly from the measured width (`height = maxWidth * 9f / 16f`), capped so it never exceeds the available column height. This sidesteps the constraint-propagation bug because it doesn't depend on `aspectRatio()`'s intrinsic-measurement pass at all — it just reads the actual measured width and sets a `.height()` from it, the same primitive the current `120.dp` workaround uses, just now computed instead of hardcoded.

4. **Loading state overlay.** Collect `dockPlayback.playbackState` as compose state (mirrors `previewPlaybackState` in `LiveTvSplitLayout.kt`) and show a `CircularProgressIndicator` centered over the video box whenever it isn't `PlaybackState.Playing`, exactly like TV lines 403-408. This needs a null-safe read since `dockPlayback` can be null when nothing is docked (it can't be null inside this branch since `target != null`, but the state must be hoisted at the same level `dockPlayback` already is).

5. **Close (X) button parity with portrait.** Add the same `IconButton` (Close icon, top-end aligned, white tint) that the portrait branch already has (lines 680-692), reusing the same `onClick = { dockPlayback?.stop(); dockTarget = null }` logic, so landscape and portrait behave identically for dismissing the preview.

6. **Leave everything else as-is**: EPG now/next text + progress bar (already present and already close to TV's version), "LIVE PREVIEW" label, `streamsList()` call, tap-to-promote (`clickable { fullScreen = true }`) on the video box.

## Verification

- Run the mobile debug build. With no Live TV preview docked, confirm every other screen (Browse, Settings, Movie/TV details, Search) stays portrait when the device is rotated — this is the new manifest-level lock.
- Open Live TV, tap a channel to dock the preview, then rotate the device to landscape.
- Confirm: video pane left (~2/3 width) with true 16:9 box, channel list right (~1/3 width) highlighting the current channel, "LIVE PREVIEW" label + now/next EPG with progress bar, a working close (X) button, and a loading spinner while a channel is still buffering.
- Rotate back to portrait and confirm the portrait branch is unaffected, then close the dock (X button) and confirm rotation stops working again (back to portrait-locked).
- Test on both a phone-sized emulator/device and, if available, a small (<600dp-portrait-width) device to confirm the new orientation check (not the old width threshold) is what's driving the layout.
- Tap the video box to confirm promote-to-fullscreen still works from landscape, and that demoting back to the dock (still docked, not closed) preserves rotation-following.
- With the OS-level auto-rotate toggle off, confirm the docked preview does NOT force-rotate (per `SCREEN_ORIENTATION_USER` choice) while the full-screen player still does (unaffected, existing `SENSOR_LANDSCAPE` behavior).
