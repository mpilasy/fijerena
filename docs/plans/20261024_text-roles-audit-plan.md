# Text fonts and colours audit

**Status:** Built 2026-10-24 (Phases 1–3). Checked on the phone emulator (phase 1's dock in Meadow; Home card titles in Console) and the TV emulator (Settings group names, section headings and the Live TV preview's Next line in Console).

## Goal

Every piece of text takes its font and colour from the role it plays, so each Look and colour
theme shows it as designed: names in the title font (Antonio in Console, not Inter), the accent
only on what is current or selected, and no text that a light theme or a coloured fill makes
unreadable.

## Audit (2026-10-24, read-only, TV and phone)

- Almost no text skips the design system (`CinemaText`), and none uses a raw colour or the global
  theme holder vals.
- **Fonts:** about 40 names (films, shows, episodes, channels, programmes, sources, profiles,
  sections, dialog titles) in Body/BodySmall/Caption instead of Title/Heading; the profile initial
  and the no-picture poster letter in the platform font.
- **Colours:** about 50 ordinary texts in the accent (genres, episode numbers, channel names,
  counts, status messages, section headings); a few unreadable: the no-picture poster letter, the
  phone player menus' "Active", the phone category rule editor, the expiry banner, the phone
  Live TV dock text over video.
- About 150 unused imports of the old global colour vals (clean-up, not in this plan).

## Decisions (user, 2026-10-24)

- **TR-D1** Section headings in Settings and dialogs: the primary text colour, not the accent.
- **TR-D2** Buttons, tabs and badges keep their present role (no Label change, so no new capitals
  in Console).
- **TR-D3** Names use Heading (lists, rows) or Title (screen and dialog titles), as TV's rows do.

## Progress

| Phase | What | State |
|---|---|---|
| 1 | Unreadable text: fills, light themes, text over video | Done |
| 2 | Names in the title font (TV and phone); profile initial and poster letter in the Look's font | Done |
| 3 | Accent only on what is current or selected; headings in the primary text colour (TR-D1) | Done |

## Notes

- **Phase 1:** the no-picture poster letter (both apps) and the TV status badge take white or black
  from their own fill (`contentOn`); the danger role's text colour does too, and a new
  `TextTone.OnDanger` gives it to the expiry banners. The phone player menus' selected row had
  Material's text colour for its fill, the same as the fill on Daylight: it now gets white or
  black from the fill, "Active" included. The phone category rule editor moved to a neutral fill
  with an accent outline. The phone dock's text and close buttons over the video use the media
  scrim and white. Checked on the phone emulator in Meadow (the dock).
- **Phase 2:** about 46 names moved to Heading or Title (TV and phone), among them Home cards,
  search results, related titles, episode and channel names, guide names, source, profile and
  device names, dialog titles; TV dialogs that used Material's Text now use CinemaText. The phone
  profile initial uses the Look's title font. Left as they were: the rename field's floating label
  (TV), season tabs, OSD Now/Next lines, and all buttons, tabs and badges (TR-D2).
- **Phase 3:** about 66 accent texts moved to Primary, Secondary or Success (genres, episode
  numbers, channel names, counts, status and success lines), section headings to Primary (TR-D1).
  Kept in the accent because they mark something current: a synced device's now-playing line, the
  current login, ON AIR cells, the active "Only <source>" filter. Not reviewed yet: a few more
  accents the phone pass found (detail stream-name rows, EPG browser and guide lines, search
  progress, the sync status line).
