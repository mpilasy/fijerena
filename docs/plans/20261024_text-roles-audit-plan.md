# Text fonts and colours audit

**Status:** In progress 2026-10-24.

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
| 1 | Unreadable text: fills, light themes, text over video | In progress |
| 2 | Names in the title font (TV and phone); profile initial and poster letter in the Look's font | Not started |
| 3 | Accent only on what is current or selected; headings in the primary text colour (TR-D1) | Not started |
