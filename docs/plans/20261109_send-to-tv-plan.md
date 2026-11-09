# Send to TV

**Status:** In progress since 2026-11-09 (built on its own branch, not merged). The user took every recommended answer on 2026-11-09 (see Answers).

## Goal

On the phone, the user picks a film, an episode or a live channel and sends it to a TV of the
same sync group. The TV asks first ("Play" / "Dismiss"), then plays the item from its own source,
at the phone's position for a film or an episode. The TV never stops what it plays without a Play
from the user in the room.

Live sync is foreground-only: a TV whose app is closed or in the background has no socket and gets
nothing. The phone lists only the TVs that have the app open now, and tells the user when a TV did
not answer.

## What exists (survey 2026-11-09)

- **Device-to-device messages.** Volatile records (`SyncKind.VOLATILE` in core/network
  `sync/SyncKind.kt`): held in memory on both ends, queued in `VolatileRecords.outbox`, pushed with
  a fresh HLC, last write wins on the server. No server change for a new kind; an older build skips
  a kind it does not know. `SyncApplier` routes each kind (`applyRemoteCommand`, `applyPinRequest`).
  - **Remote Stop:** `remote_command` keyed on the target's device id; `SyncPayloads.RemoteCommand`
    (`command`, `sessionId`, `fromDeviceName`). Sent from `SyncSettingsViewModel` (phone devices
    list); received by `RemoteCommands` (fire once per session, `claim`) and `RemoteStopEffect` /
    `RemoteStopFallback` (core/ui `sync/RemoteStop.kt`). No answer record: the phone watches the
    target's now playing line, "Couldn't reach <device>" after about 90 s.
  - **PIN on the phone:** `pin_request` + `pin_answer`, both keyed on the asking device's id;
    `PinRequests` keeps a one-use `requestId`, `open` flag, `sentAt` with `MAX_AGE_MS` (5 min), and
    a `finished` set so a resync never shows a prompt again (`PinOnPhone.kt` on the phone). This is
    the request/answer model to copy.
- **Presence.** `SyncWire.Device` (`GET /devices`, `SyncAccountManager.devices()`) has `name` and
  `lastSeen`. The server (`server/src/account.ts`, `lastSeen()`) takes the newest of the last
  request and the socket's 30 s auto-answered ping, so a device with the app open is never more
  than about 30 s old. `SyncManager.onBackground()` closes the socket.
- **Device type.** `DeviceBuild.app` is `tv` or `phone` (device builds plan), synced as the shared
  setting `device_build:<deviceId>`; `SyncSettingsViewModel.deviceBuilds` maps id to build. A TV
  that runs a build before 2026-11-06 has no build record.
- **Now playing.** `NowPlayingStore` (`isCurrent`, `STALE_AFTER_MS`), `NowPlayingPublisher`; sent
  only with Share What's Playing on (off by default).
- **Opening an item on the TV.** `GameReminderAlerts` (core/network `sports/`): `requestTune` /
  `takeTune`; `TvNavHost` takes the tune when `canTuneFrom(destination)` (not on `ProfilePicker`,
  `SafeMode`, `NewerData`) and drops it when the profile in use differs. Routes in core/navigation
  `Screen.kt`: `Screen.Player(streamId, streamName, categoryId, contentType, episodeId,
  episodeExtension, seriesId, seriesName, startFromBeginning, catchup…, providerId)`. `providerId`
  is a local row id; across devices a source is its `providerKey` (`SettingsSyncDao.providerByKey`).
  `Screen.Player` has no start position: resume comes from the watch row, which the phone holds
  for up to a minute while it plays (R-18).
- **TV banner.** `TvGameReminderBanner` / `ReminderBannerState` (tv `ui/components/`): never takes
  focus, takes the remote's keys while shown (Left/Right choose, OK acts on key up only, Back
  closes, volume passes), no keys while a Dialog window is on top, closes after
  `CinemaAnimation.reminderBannerDismissMs`. Tests: `TvGameReminderBannerFocusTest`, `…LayoutTest`.
- **Phone actions.** `MovieDetailsScreen` hero row of `DetailIconAction`; `EpisodeSelectionScreen`
  episode menu (`MenuAction`); channel and title long-press menu `ItemActionsDialog`; player top
  bar `CinemaIconButton`s in `MobileControlsOverlay`. No Cast action exists.
- **One-connection accounts.** Xtream stream release plan (2026-10-17): a stopped stream frees its
  slot; a paused one can hold it.

## Decisions (proposed)

| # | Subject | Decision |
|---|---|---|
| ST-D1 | Messages | Two new volatile kinds, as PIN on the phone: `play_request` keyed on the **target TV's** device id (`requestId`, `profileId`, `profileName`, `fromDeviceName`, `providerKey`, the `Screen.Player` fields but `providerId`, `positionMs`, title lines for the banner, `open`, `sentAt`) and `play_answer` keyed on the target's id (`requestId`, `result`: `received`, `playing`, `dismissed`, `busy`, `no_source`, `no_profile`, `not_allowed`). A one-use `PlayRequests` store in core/network (as `PinRequests`). Remote Stop stays as it is. |
| ST-D2 | Which TVs | The picker lists the TVs of the group (`DeviceBuild.app == tv`, not revoked). A TV is **Open** when its `lastSeen` is less than 90 s old (one fetch of `/devices` when the picker opens, then every 15 s while it shows). A TV that is not open shows "Not open · Open Fijerena on the TV" and cannot be picked. A TV with no build record or an older build shows "Needs an update". |
| ST-D3 | Feature check | `DeviceBuild` gets `features: List<String>`; a build with Send to TV reports `play_request`. The picker uses it; the version code is not used (debug builds). |
| ST-D4 | Life of a request | The TV answers `received` at once when the request applies. The phone shows "Sent to <TV>…"; no `received` in 20 s gives "Couldn't reach <TV>" and the phone closes the request (`open` false), so a TV that opens later does nothing. The TV drops a request older than 2 min by its own clock or by its receive time, and every `requestId` it saw. |
| ST-D5 | TV banner | A Send banner in the same place and with the same keys as `TvGameReminderBanner` (shared key state, not a copy): "<device> sends Malcolm X · From 42:10" with **Play** and **Dismiss**. Play is the first choice. It stays 60 s, then answers `dismissed`. A new request replaces the old one (the old one answers `dismissed`). |
| ST-D6 | TV states | Any screen: banner. Playing (full screen or Live TV preview): banner; Play saves the position as Back does, then opens the item. A Dialog on top (PIN gate, menus): the banner waits until it closes, as the reminder banner does. Who's Watching?: banner; Play picks the sender's profile, then plays. Safe mode, Newer data, setup screens: answer `busy`, no banner. |
| ST-D7 | Profile | The item plays for the **sender's profile** (`profileId` is synced). If the TV uses another profile, the banner says "for Anna" and Play switches the profile as Who's Watching? does (no PIN: picking a profile never asks one). Unknown profile on the TV: `no_profile`. See ST-Q2. |
| ST-D8 | Checks on the TV | Before the banner: the source exists (`providerByKey`) and is on for that profile, else `no_source`; the profile's Parental Controls and content filters allow the item (the same check as a play from a list), else `not_allowed`. A child profile follows its own rules; nothing is played that the profile could not open on the TV. |
| ST-D9 | Position | `Screen.Player` gets `startPositionMs: Long? = null`; when set it wins over the watch row. Films and episodes only; live has none. |
| ST-D10 | What opens | Every kind opens `Screen.Player` full screen (live too). Back from the player goes back to the screen and control the TV was on before. |
| ST-D11 | Phone entry points | An icon action **Send to TV** (TV icon, not the Cast icon), with its tooltip: the film hero row (after Trailer, the CONTRIBUTING detail-page order gets it), the show page's Play row, the episode menu, the channel and title long-press menu, Home card menus, the phone player top bar. It shows only while linked to a sync group that has a TV with the feature. |
| ST-D12 | Picker | A bottom sheet "Send to TV" with one row per TV (name, Open or Not open, what it plays if it shares that). With exactly one TV open, the action sends at once and says "Sent to <TV>". |
| ST-D13 | Phone results | Messages via the app's notices: "Playing on <TV>", "<TV> dismissed it", "<TV> is busy", "<TV> doesn't have this source", "<profile> isn't on <TV>", "Not allowed on <TV> for <profile>", "Couldn't reach <TV>". |
| ST-D14 | Phone playing | From the player: the phone stops its stream first (saves the watch position as Back does, frees an Xtream slot), then sends with that position. See ST-Q3. |
| ST-D15 | Trust | The same as Remote Stop: any device of the group can send; the account key keeps it from the server and from other people. No setting on the TV in v1. |
| ST-D16 | TV focus | The banner never takes focus (focus contract rule 8: the focused control stays). Its keys: Left/Right choose Play or Dismiss, OK acts on key up only (a key pressed before the banner showed never acts), Back = Dismiss. After Play, the host records the focused control (`NavReturnFocus` `leaveFrom`) before it opens the player, so Back restores it (rule 6). After a profile switch, focus lands on Home's first row (`requestFocusWithRetry`). After Dismiss, focus is unchanged. |

## Open questions

| # | Question | Options |
|---|---|---|
| ST-Q1 | Catch-up programmes | (a) **Not in v1; add them after the walk.** Recommended: the route has the fields, but the archive window and the TV's guide need their own checks. (b) In v1. |
| ST-Q2 | TV on another profile | (a) **Banner says "for Anna"; Play switches the TV to Anna.** Recommended. (b) Play under the profile the TV uses (its rules apply). (c) Refuse with `no_profile` unless the TV uses the same profile. |
| ST-Q3 | Phone playing, then Send | (a) **Stop on the phone at once, then send.** Recommended: frees a 1-connection slot, the position is exact; if the TV dismisses, the phone resumes from its watch row. (b) Keep playing until the TV answers `playing`, then stop (the TV can fail "account busy" on a 1-connection account). (c) (a) only for 1-connection sources, (b) for the others. |
| ST-Q4 | Continue on Phone (the reverse) | (a) **Not in this plan.** Recommended: the TV has no picker for phones, and the phone needs the TV's position (Share What's Playing is off by default). (b) Phase 6: a phone's devices list gets "Continue Here" on a TV that plays; the TV answers with the item and position and stops. |
| ST-Q5 | Phone to phone | (a) **TVs only.** Recommended: it is the asked use. (b) Any device of the group. |
| ST-Q6 | Banner default during TV playback | (a) **Play first, as everywhere.** Recommended: the key-up rule stops an OK pressed before the banner. (b) Dismiss first while the TV plays. |

## Answers (user, 2026-11-09)

| # | Answer |
|---|---|
| ST-Q1 | (a) No catch-up in v1. |
| ST-Q2 | (a) The banner names the profile; Play switches the TV to it. |
| ST-Q3 | (a) The phone stops at once, then sends. |
| ST-Q4 | (a) No Continue on Phone in this plan. |
| ST-Q5 | (a) TVs only. |
| ST-Q6 | (a) Play first, as everywhere. |

## Phases

| # | Work | Done when |
|---|---|---|
| 1 | Sync: `play_request` / `play_answer` kinds, payloads, `SyncApplier` routing, `PlayRequests` store (one use, age, close), `DeviceBuild.features` | `PlayRequestsTest`, `SyncApplierPlayRequestTest`, `DeviceBuildTest` pass; old-build skip covered |
| 2 | Core: resolve a request on the TV (source by `providerKey`, profile, source on, parental check) to a `Screen.Player`; `Screen.Player.startPositionMs` honoured by the player | `PlayRequestResolverTest` (every answer result), player resume test pass on TV and phone |
| 3 | TV: Send banner on the shared banner key state, host wiring in `TvNavHost` (states of ST-D6, profile switch, answers, return focus) | `TvSendBannerFocusTest`, `TvSendBannerLayoutTest`, reminder banner tests still pass |
| 4 | Phone: Send to TV action in every entry point, picker sheet with presence, result messages, stop-then-send from the player | `SendToTvPickerTest`, `SendToTvViewModelTest` (timeouts, close on no answer), UI tests pass |
| 5 | Walk on the emulators (see Checks); docs: FEATURES (Live Sync), NAVIGATION_GUIDE (banner keys), CONTRIBUTING (detail-page action order), RELEASE_NOTES; strings in en, fr, mg | Every walk case passes; docs in the same commits |

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 | Not started | |
| 2 | Not started | |
| 3 | Not started | |
| 4 | Not started | |
| 5 | Not started | |

## Checks

- **Tests:** the tests named in Phases; touched-module unit tests per phase; full suite once at merge.
- **Gates:** ktlint, `scripts/check-focus-retry.sh`, `scripts/check-text-overflow.py`, the design
  gate (no marquee under mobile/, button text tone), `./gradlew lintDebug` with 0 errors.
- **Strings:** English UI titles in Title Case ("Send to TV"), messages in sentence case, British
  spelling; fr and mg sentence case, Malagasy glossary.
- **Walk:** phone emulator and TV emulator linked to the local sync server (workerd on the host
  :8787, `10.0.2.2` from the AVDs), jellyxtream for VOD, iptv for live. Agree a time slot with any
  other user of the emulators first. Cases:
  1. Film from details at 10:00 → TV banner, Play → TV plays from 10:00; phone shows "Playing on".
  2. Episode from the episode menu; channel from the long-press menu.
  3. From the phone player: the phone stops, the TV resumes at the phone's position.
  4. TV playing a channel: banner shows, playback goes on until Play; Dismiss → phone "dismissed".
  5. TV on Who's Watching? and TV on another profile: Play picks the sender's profile.
  6. TV in the Settings PIN gate: banner waits until the dialog closes.
  7. TV app in the background: the picker shows it as Not open; force a send in a test build →
     "Couldn't reach", and opening the TV app later shows nothing.
  8. Source off for the profile on the TV → "doesn't have this source".
  9. Back from the TV player restores the control that had focus; no "Focus lost, given back" in Logs.
  10. Both apps in Malagasy: no English text on the banner, the sheet or the messages.

## Risks

- **Presence lag:** a TV closed less than 90 s ago still shows Open; the 20 s `received` timeout
  covers it. A TV in its screensaver can stop the activity and close the socket: it shows Not open.
- **Wrong TV clocks (changed 2026-11-09, by the user):** both devices compare a request's age on the
  sync server's clock (`ServerClock`), so a TV clock that is wrong keeps a fresh request and drops a
  stale one. Each device reads its offset once per app start from its own row of `/devices`, so the
  server needs no change. The TV also checks the receive time. Before the offset is read, a device
  uses its own clock.
- **Key conflicts:** two phones sending to one TV write the same key; the newer one wins and the
  older phone gets no `received`, so it says "Couldn't reach". Acceptable for v1.
- **Banner clash:** a game reminder and a send at the same time. One banner shows at a time; the
  send waits until the reminder closes.
- **1-connection accounts:** with ST-Q3 (b) the TV can fail "account busy"; (a) avoids it.
- **Catalogue not loaded on the TV:** the route carries the stream fields, so Xtream plays without
  the TV's lists; a Jellyfin item needs its source's login, which syncs with the source.
- **Old builds:** a TV before this feature never answers; the feature check (ST-D3) hides it.
