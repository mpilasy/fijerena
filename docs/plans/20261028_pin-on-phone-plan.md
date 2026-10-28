# PIN on the phone

**Status:** Agreed 2026-10-28 (mocks, decisions below); building.

## Goal

When the TV asks for a profile PIN, the user can type it on a linked phone. The TV shows only
"Enter the PIN on your phone" and the dots, so people in the room cannot read the PIN from the
focus on the TV's number pad. Asked by the user 2026-10-28 (option 1 of three: phone, D-pad code,
shuffled pad).

## Today

- `TvPinDialog` shows a 3×4 number pad. The D-pad moves the focus from digit to digit, so anyone
  who sees the screen can read the PIN. A remote's digit keys type without moving the focus, but
  the Shield and Chromecast remotes have no digit keys.
- Every linked device already has every profile's PIN hash (`pin_hash` syncs per profile), so the
  phone can check a PIN itself with `ProfilePins.check`.
- Live sync has one device-to-device command, Remote Stop: an end-to-end encrypted volatile
  record addressed to one device id. The server's WebSocket tells the target to pull, so it
  arrives in a few seconds while both apps are open. The socket is open only while the app is in
  the foreground; there are no push notifications.

## Mocks

Design canvas with the four screens: https://claude.ai/artifact/9FrNNqGcrCfivnCYa1Uu9x

TV, the PIN dialog when a phone is linked (new: the phone button under the pad; an icon, with its
label only while it has the focus, as for every TV button):

```
┌──────────────────────────────────────┐
│           Enter Anna's PIN           │
│               ○ ○ ○ ○                │
│        [1]    [2]    [3]             │
│        [4]    [5]    [6]             │
│        [7]    [8]    [9]             │
│        [⌫]    [0]                    │
│                                      │
│   [📱 Use My Phone]       [Cancel]   │
└──────────────────────────────────────┘
```

TV, after Use My Phone (the pad goes away, nothing on the screen shows the PIN):

```
┌──────────────────────────────────────┐
│           Enter Anna's PIN           │
│               ○ ○ ○ ○                │
│                                      │
│   Open Fijerena on your phone and    │
│   enter the PIN there.        ◌      │
│                                      │
│   [⌨ Use the TV]          [Cancel]   │
└──────────────────────────────────────┘
```

Phone, over any screen when the request arrives (or when the app opens):

```
┌──────────────────────────────┐
│     Living Room TV asks      │
│     for Anna's PIN           │
│          ● ● ○ ○             │
│                              │
│                    [Cancel]  │
├──────────────────────────────┤
│  1      2      3             │  ← number keyboard
│  4      5      6             │
│  7      8      9             │
│  ⌫      0      ✓             │
└──────────────────────────────┘
```

Then on the TV:
- Right PIN on the phone: the TV dialog closes and the action goes on, as for a right PIN on the pad.
- Cancel on the phone: the TV shows the pad again with "Cancelled on the phone".
- No answer in 2 minutes: the TV shows the pad again with "No answer from the phone".

## Design

- **The phone checks the PIN, not the TV.** The PIN never leaves the phone. The phone checks it
  against the synced hash with its own wrong-try count and wait, then sends "unlocked" for the
  request. The TV acts on that answer as on a right PIN (`PinSession.unlock`, then the held action).
- **Two new volatile kinds** (no server change; old builds skip unknown kinds):
  - `pin_request`, keyed on the TV's device id: request id (random, one use), profile id, the
    TV's name, open or closed, and the TV's clock time. The TV writes it open when the user picks
    Use My Phone, and closed when the dialog ends for any reason.
  - `pin_answer`, keyed on the TV's device id: request id, unlocked or cancelled.
- **Which phone:** every linked phone that is open shows the prompt. The first answer wins; the
  TV closes the request, so the other phones close their prompt. The device list has no TV/phone
  type, so the phone app is the one that answers and the TV app never shows a request.
- **Which prompts:** the Check prompts (Settings groups, guarded screens, the PIN row's "current
  PIN"). Choosing a new PIN stays on the TV pad; a PIN can also be set on the phone, and it syncs.
  (Done note: only Check prompts offer it. The child lock prompt, off since 2026-10-24, checks
  any admin PIN and keeps the pad only.)
- **Button only when it can work:** Use My Phone shows only while this device is linked to a sync
  group. (Done note: the device list comes from the server, so the TV does not check for another
  device first; with none, the request times out after 2 minutes.)
- **Old requests:** a resync gives records again, so the request id is used once on both sides.
  The phone ignores a request that is closed, or more than 5 minutes older than its own clock. A TV
  with a wrong clock fails safe: the phone ignores the request and the TV falls back to the pad.
- **Trust:** the same as Remote Stop. Any device of the sync group can send an answer; the account
  key protects it from the server and from other people. The PIN is a household lock, not real
  security (`ProfilePins.kt`).
- **Phone in the background:** the request waits on the server. The phone shows it when the app
  opens, if the TV is still waiting. No notifications (live sync has none).
- **Phone screen privacy (added):** the phone PIN dialog blocks screenshots, screen recording and
  the Recents thumbnail (`FLAG_SECURE`) while it is open, for every PIN prompt on the phone.
- **D-pad on the TV dialog:** Use My Phone sits left of Cancel on the button row. Down from the
  pad's bottom row goes to it; while waiting, focus is on Use the TV.

## Phases

| Phase | Work | Status |
|---|---|---|
| 1 | Sync: `pin_request` and `pin_answer` kinds, payloads, apply, an in-memory store, tests | Done (05be3dfbe) |
| 2 | TV: Use My Phone button, waiting state, answer or timeout, request closed at the end; focus tests | Done (31d626dd4) |
| 3 | Phone: prompt over any screen, local check, answer, `FLAG_SECURE` on PIN dialogs; tests | In progress |
| 4 | Both emulators linked to the local sync server: walk every case; docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES) | Not started |

## Decisions

- 2026-10-28, the user: the phone prompt opens by itself over any screen (no banner first).
- 2026-10-28, the user: the TV never sends a request by itself; the user presses Use My Phone.
