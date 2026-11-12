# Preferred track languages

**Status:** Phases 1 to 4 done 2026-11-11; the emulator checks of Phases 3 and 4 done 2026-11-12 (see Progress), except the live channel check. PL-Q1 to PL-Q7 decided 2026-11-11 (see Open questions). The per-stream track reset was fixed on its own 2026-11-11. No Phase 5 (PL-Q5 a).

## Goal

Each profile sets the audio and subtitle languages it wants, for example "audio: French, else the
original; subtitles: French when the audio is not French". The player uses them when the item has
no saved track choice of its own and its series has none. TV and phone. Live sync takes the
setting to every device of the profile.

## What exists (survey 2026-11-11)

- **Per-item memory:** `WatchStateEntity.audioTrackIndex` and `subtitleTrackIndex` (watch_state).
  - The value is the index in the flattened track list of `StreamingPlaybackService.getAudioTracks()` / `getSubtitleTracks()`, not a language. Subtitles Off is `-1`.
  - A pick writes it: `selectAudioTrack(groupIndex, trackIndex)`, `selectSubtitleTrack(…)` and `disableSubtitles()` call `savePosition` with the index. Position saves send null, and the `WatchStateDao` upsert keeps the old value (`COALESCE`).
  - Until 2026-11-11 the exit save (`finalizeSession`) also wrote the tracks on screen, `-1` when no subtitle was on, also for a title left before its tracks loaded. It now writes no track, so only picks (and the restores they cause) write it. Rows written before keep their values.
  - It syncs with watch progress (`SyncPayloads`) and goes into the backup file (`SettingsExportManager`).
- **Per-series memory:** `WatchStateDao.getLatestSeriesTrackPrefs` → `MediaRepository.getSeriesTrackPrefs`.
  `StreamLoaderViewModel` uses it for an episode (`ContentType.TV_SHOWS`) when the episode has no audio and no subtitle choice.
- **Restore:** `TvPlayerScreen` and `MobilePlayerScreen` have the same block in `LaunchedEffect(currentStreamId)`.
  - It waits for the first Playing or Paused state, then calls `service.selectAudioTrack(index)` and `selectSubtitleTrack(index)`.
  - These calls save the index again. Thus a series choice becomes the episode's own choice.
- **No memory:**
  - Live TV and catch-up: `recordHistory` and `savePlaybackPosition` skip `ContentType.LIVE_TV`.
  - Jellyfin (`usesServerUserData`): `getPlaybackPositionSuspend` has no track indices, and `getSeriesTrackPrefs` returns null.
- **Media3 preferences:**
  - `PlayerConfigFactory.createTrackSelector` sets `setPreferredAudioLanguage("en")` for every profile and every stream. Thus a stream with an English track starts in English, also when its default track is French.
  - Nothing sets a text language or role flags.
- **Track parameters were not reset between streams (fixed 2026-11-11, before the phases).** Subtitles Off (`setTrackTypeDisabled(TRACK_TYPE_TEXT, true)`) stayed on for every later stream of the same service instance, until a subtitle was picked, and old overrides stayed too.
  - Now `StreamingPlaybackService.playStream` calls `resetTrackChoices` when the stream URL changes: text back on, overrides cleared. The same item played again (Retry, a reconnect, a return from the screensaver) keeps the viewer's picks, because those paths restore no saved choice.
- **Pickers:**
  - TV: `AudioTrackSelectorDialog` and `SubtitleSelectorDialog` (tv/.../ui/player/components/dialogs).
  - Phone: `AudioTrackSelectorDialog` and `SubtitleSelectorDialog` in `MobilePlayerDialogs.kt`.
  - The rows come from `AudioTrackInfo` and `SubtitleTrackInfo` (`PlaybackState.kt`). Their `language` is `Format.language`, or the translated word "Unknown" when the stream gives no language. They do not carry `roleFlags` or `selectionFlags`.
- **Language data on tracks:**
  - Media3 normalises `Format.language` ("fre" and "fra" become "fr").
  - IPTV streams often send `und`, nothing, `mul`, or the DVB codes `qaa` (original version) and `qad` (audio description).
  - Labels often name the language: "VFF", "VFQ", "TrueFrench", "English", "Commentary", "Forced", "SDH".
  - Media3 flags: `C.ROLE_FLAG_COMMENTARY`, `ROLE_FLAG_DESCRIBES_VIDEO` (audio description), `ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND` (SDH), `SELECTION_FLAG_FORCED`, `SELECTION_FLAG_DEFAULT`.
- **Language data on the item:**
  - `parseDisplayTitle` (core/player/.../domain/TitleLanguage.kt) gives the title's code badge ("FR -") and tags ("VOSTFR", "(FRENCH ENG-SUB)", "MULTI-AUDIO").
  - `providerCode(name).language` (core/ui/.../viewmodels/SearchFacets.kt) turns the badge into an ISO 639-1 code. Its `languageOfCode` maps 2- and 3-letter codes, bibliographic codes included.
  - `TitleFacts.originalLanguage` (TMDB's ISO 639-1 code) is in the detail cache (`xtream_streams.facts`, `xtream_series.facts`). It exists only after a details page was read with a TMDB key.
  - `languageLabel(code, locale)` names a code in the app language ("anglais"). The details tab uses it (`titleFactRows`).
- **Jellyfin:**
  - `JellyfinApiService.getPlaybackInfo` sends `JellyfinPlaybackInfoRequest` with `audioStreamIndex` and `subtitleStreamIndex` null.
  - Direct play and direct stream give the player every embedded track. A transcode (HLS) carries the audio that the Jellyfin server picks with its own user settings.
  - `JellyfinMediaStream` has `Language` and `IsDefault`, but no `Index`.
  - The device profile asks for external subtitles (`SubtitleProfiles`, `Method: External`), but the player adds no external subtitle track (no `SubtitleConfiguration`). Thus a Jellyfin item's external subtitle files never show.
- **Per-profile synced settings:** the same steps as in the home row order plan:
  - `AppSettings.profileKey`;
  - `SYNCED_SETTING_KEYS` and `PER_PROFILE_SETTING_KEYS`;
  - a branch in `applyRemoteSetting` and `syncedSetting`;
  - a getter, and a setter that calls `SettingsSyncQueue.setting`;
  - removal in `ProfileRepository` when the profile is deleted (as `removeAutoplayNextEpisode`).
  Model: `KEY_AUTOPLAY_NEXT_EPISODE` and `AppSettingsAutoplayNextEpisodeTest`. The backup file does not carry that setting.
- **Where the rows go:** the profile page's Playback section (`settings_playback_section_title`), after Play Next Episode Automatically.
  - TV: `ProfilesSettingsCard`.
  - Phone: `ProfileEditScreen`.
  - Both read and save through `ProfilesViewModel.settingsOf`.
  - The same page is Your Profile and an admin's Settings → Profiles → a profile. A child has no Settings, so an admin sets a child's languages there.

## Open questions

All decided by the user 2026-11-11. The **Decided** column gives the choice; PL-Q1 and PL-Q4 differ
from the recommendations, and the design below follows the decisions.

| # | Question | Options | Decided |
|---|----------|---------|---------|
| PL-Q1 | What does a profile with no setting get? | (a) **The stream's default track.** Remove the fixed `"en"` from `PlayerConfigFactory`. Recommended: today a multi-audio film starts in English for every profile, and nobody chose that. (b) Keep English as the default. (c) The app language. | **(b)** Keep English as the default for a profile with no setting. |
| PL-Q2 | What can the audio setting say? | (a) **One language, then "the original".** The original is TMDB's original language when it is known, else a `qaa` track, else the stream's default track. Recommended: it is your example, and it is one row. (b) Two languages in order, then the stream's default track. (c) One language, then the stream's default track. | **(a)** One language, then the original. |
| PL-Q3 | What can the subtitle setting say? | (a) **A language, and a switch "Only When the Audio Is in Another Language" (on by default).** Values: Stream Default, Off, or a language. Recommended: the switch covers your example, and "Always" helps a viewer who learns a language or does not hear well. (b) A language only, always with the rule "only when the audio is in another language". | **(a)** Stream Default, Off or a language, and the switch (on by default). |
| PL-Q4 | Live TV and catch-up too? | (a) **Yes.** Live channels have no memory, so the profile setting is the only choice, for example the original-version (`qaa`) track of a French channel. Recommended. (b) Films and episodes only. | **(b)** Films and episodes only. |
| PL-Q5 | Jellyfin items that the server transcodes? | (a) **The Jellyfin server's choice**, from its user's language settings. Direct play and direct stream follow the profile in the player. Recommended for this plan: most items play direct. (b) Send `AudioStreamIndex` and `SubtitleStreamIndex`, picked from the item's media streams with the profile's languages (Phase 5). | **(a)** The server's choice; direct play follows the profile. No Phase 5. |
| PL-Q6 | Which languages does the picker offer? | (a) **A fixed list of about 20 common languages.** English, French and Malagasy come first, then the others by name in the app language: Arabic, Chinese, Dutch, German, Hindi, Italian, Japanese, Korean, Polish, Portuguese, Russian, Spanish, Turkish and others. Recommended: it stays short with the D-pad. (b) Every ISO 639-1 language that Android knows, by name. (c) The list of (a), then "More Languages" with (b). | **(a)** A fixed list of 20 languages. |
| PL-Q7 | Make the per-series memory follow a language? | (a) **No, keep the index (as today).** Recommended for this plan. (b) Also save the language of the picked track, and find the same language in the next episode. Today index 1 is English in one episode and French in the next when the order changes. (b) needs a migration 32→33, a new sync payload field and a backup field. Better as its own plan. | **(a)** Keep the index. No migration. |

## Design

### The setting

- Three per-profile keys, all strings, synced as `SETTING` records. The newer change wins.
  - `audio_language`: an ISO 639-1 code, or blank for no setting. No setting keeps today's behaviour (PL-Q1 b): Media3 prefers English (`setPreferredAudioLanguage("en")`), and the player changes no audio track. The row shows "English" for it.
  - `subtitle_language`: a code, `off`, or blank for the stream's default.
  - `subtitles_other_audio_only`: a boolean, true by default (PL-Q3 a).
- `applyRemoteSetting` keeps a blank value, `off` (subtitles only), or 2 to 3 lower-case letters. It drops any other value and keeps the local one (the R-09 rule). It keeps a code that this version does not list, and the picker shows that code by `languageLabel`.
- **Deleting a profile** removes the keys in `ProfileRepository`.
- **Backup file:** the keys are not in it, as Play Next Episode Automatically.
- **Child profile:** no special rules.

### Which choice wins

Each track type (audio, subtitles) is decided on its own, in this order:

1. The item's own choice.
2. The series' choice (episodes only). The rule stays as today: the episode takes the series' choice only when it has no audio and no subtitle choice.
3. The profile's setting (films and episodes only, PL-Q4 b).
4. The stream's default track (for audio with no setting: English first, as today, PL-Q1 b).

- `StreamLoaderViewModel.StreamState.Success` tells where each index came from (item, series or none). Thus the player applies the profile only to a type with no saved choice.
- A pure function `startTracks(item, series, preference)` holds this order, with unit tests.

### Matching tracks (core:player, `TrackLanguage.kt`)

- **A track's language** is found in this order:
  1. `Format.language`, normalised. `und`, `mul`, `zxx` and an empty value count as unknown.
  2. The label's words: a 2- or 3-letter code (the `languageOfCode` rules, moved to core/player); a language name in English, French or in the language itself ("French", "Français"); or a release word ("VF", "VFF", "VFQ", "TrueFrench" → `fr`).
  3. For the only audio track, the title's code (`providerCode(name).language`), unless its tags name subtitles ("VOSTFR", "ENG-SUB"). Such tags mean that the audio is the original.
- **Tracks not picked automatically:**
  - commentary (`ROLE_FLAG_COMMENTARY`, label "Commentary", "Commentaire");
  - audio description (`ROLE_FLAG_DESCRIBES_VIDEO`, language `qad`, label "AD", "Audio description", "Audiodescription");
  - for subtitles, SDH (`ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND`, label "SDH", "CC", "malentendants"), unless no other track has the language.
- **Audio:** the first track in the profile's language. Else the original:
  - the track in `TitleFacts.originalLanguage` when it is known;
  - else a `qaa` track ("VO" label);
  - else the stream's default track (`SELECTION_FLAG_DEFAULT`, else the first).
  Among tracks of one language, the default flag wins, then more channels.
- **Subtitles:**
  - Blank: the stream's default, as today.
  - `off`: no subtitles. A forced track in the audio language still shows.
  - A language, with the switch on: a full (not forced) track in that language when the audio's language is known and different. When the audio is in that language, only a forced track in it shows. When the audio's language is unknown, no subtitles: a wrong guess would put subtitles on every channel of a country.
  - A language, with the switch off: that track always.
- The function receives plain track data. `AudioTrackInfo` and `SubtitleTrackInfo` get the raw `languageCode`, `roleFlags` and `selectionFlags` for it. The localised "Unknown" stays only for display.

### In the player

- **At each `playStream` of a new stream URL:**
  - turn text back on (`setTrackTypeDisabled(TRACK_TYPE_TEXT, false)`);
  - clear the audio and text overrides;
  - set `setPreferredAudioLanguages(profile language, original language)` from `PlayerMetadata.preferredAudioLanguages`.
  Thus a well-tagged stream starts on the right track, with no switch after the first frame. With no list (no audio setting, Live TV, catch-up, every other caller) the preference stays `"en"`, as before (PL-Q1 b, PL-Q4 b).
- **Once the tracks are known:** one shared helper in core/ui replaces the two restore blocks of `TvPlayerScreen` and `MobilePlayerScreen`.
  - It applies a saved index as today. Saving it again keeps today's series chain.
  - For a type with no saved index, it runs the matching function. It changes a track only when its pick is not the one that plays.
- **A profile pick is never saved.** The service gets select calls with `persist = false`. Thus a later change of the setting still applies to items the viewer never picked for, and the series memory holds only the viewer's own picks.
- **Live TV and catch-up** do not use the profile's languages (PL-Q4 b): no preference goes into their `StreamState.Success`, and Media3 keeps English first.
- **Jellyfin transcodes** (PL-Q5 a) need no code of their own: the HLS carries the one audio track that the server picked, so the audio pick finds that track and changes nothing. Direct play and direct stream carry every track, and the helper picks among them.
- **The pickers do not change.** A pick there is the item's own choice, as today.

### Settings rows (TV and phone)

- **Rows:** the Playback section of the profile page, after Play Next Episode Automatically:
  - **Audio Language:** value: the language's name; "English" when the profile has no setting (PL-Q1 b). No "Stream Default" choice: the user chose English as the default.
  - **Subtitle Language:** value "Stream Default", "Off" or the language's name.
  - **Only When the Audio Is in Another Language:** a switch, shown when a language is picked.
- **Pickers:** one column, starting on the current value.
  - TV: a picker in the pane, as Theme.
  - Phone: a dialog.
  - The list follows PL-Q6. Names come from `languageLabel(code, appLocale)`. The value always shows in the app language.
- **Strings:** en, fr and mg, in core/ui. British spelling. English titles in Title Case, fr and mg in sentence case. The Malagasy words follow the existing strings: Feo (audio), Dikan-teny (subtitles), Filalaovana (Playback), sarimihetsika, andian-tantara.

| Key | en | fr | mg |
|---|---|---|---|
| `settings_audio_language_title` | Audio Language | Langue audio | Fitenin'ny feo |
| `settings_audio_language_desc` | Used when you have not picked a track for the film or show; else the original | Utilisée si vous n'avez pas choisi de piste pour le film ou la série ; sinon la version originale | Ampiasaina raha tsy nifidy làlana ho an'ilay sarimihetsika na andian-tantara ianao; raha tsy izany, ny tany am-boalohany |
| `settings_subtitle_language_title` | Subtitle Language | Langue des sous-titres | Fitenin'ny dikan-teny |
| `settings_subtitles_other_audio_title` | Only When the Audio Is in Another Language | Seulement si l'audio est dans une autre langue | Raha amin'ny fiteny hafa ihany ny feo |
| `settings_track_language_stream_default` | Stream default | Par défaut du flux | Izay ao amin'ny lahatsary |

Off reuses `player_subtitles_off`. The mg value for `settings_track_language_stream_default` is "Izay ao amin'ny fandefasana": "fandefasana" is the word the strings use for a stream. The language names come from ICU (`languageLabel`); CLDR has Malagasy names for all 20 ("Anglisy", "Frantsay", …), so no mg strings were added. The en description ends with a full stop, as the other descriptions.

## Phases

| Phase | What | Visible change |
|---|---|---|
| 1 | `TrackLanguage.kt`: track language, exclusions, audio and subtitle pick; raw fields on `AudioTrackInfo` and `SubtitleTrackInfo`; unit tests | None |
| 2 | The three keys, their sync, their removal with the profile; `AppSettingsTrackLanguageTest` | None |
| 3 | The player uses them: per-stream reset, Media3 preferred languages, the shared start-tracks helper on TV and phone, picks that are not saved, live and catch-up (PL-Q4); `startTracks` tests | Items with no saved choice start in the profile's languages (set only by sync so far) |
| 4 | The Settings rows and pickers on TV and phone; strings en, fr, mg; docs | Users set their languages |
| 5 | Dropped: PL-Q5 (a) | None |

- One commit per phase on main. Each commit updates this plan and the docs it affects.
- **Checks in every phase:** ktlint, the touched modules' unit tests, compile of tv and mobile. A full suite once at merge.
- **Phase 1 tests:**
  - normalised codes, `und` with a "VFF" label, 3-letter labels;
  - the only track with the title's "FR" code, and with "VOSTFR";
  - two French tracks (main, commentary), an AD track, `qaa` as the original, TMDB's original language;
  - subtitles: other audio, same audio (forced only), unknown audio, switch off, `off`, SDH-only.
- **Phase 2 tests:** a remote value kept or dropped, the per-profile key, the record queued, removal on delete.
- **Phase 3 tests:**
  - `startTracks`: item, series, profile, stream default, and one type saved with the other not;
  - a profile pick does not call `savePosition` with an index;
  - `playStream` resets text and overrides: done 2026-11-11 (`StreamingPlaybackServiceTrackResetTest`).
- **Emulator checks, Phases 3 and 4** (TV and phone AVDs, jellyxtream for films and episodes, iptv for live):
  - A test file goes into the host Jellyfin library first: French and English audio, an English commentary, French forced, French full and English subtitles. A second file has `und` tracks labelled "VF" and "VO".
  - Check each row of "Which choice wins".
  - An episode after a series pick.
  - The rows and pickers in Malagasy, with focus on TV in English.
  - A live channel with two audio tracks starts as before (English first): Live TV does not use the profile (PL-Q4 b).
- **Docs:**
  - docs/FEATURES.md → Player → Track selection (Phase 3).
  - Settings → Your Profile, and Profiles → A profile's page (Phase 4).
  - docs/RELEASE_NOTES.md (Phase 4: before the rows, the player change of Phase 3 has nothing a user can set, so one note covers both).
  - No Phase 5, so no docs/design.md change.

## Progress

| Phase | Status |
|---|---|
| 1 | Done 2026-11-11 |
| 2 | Done 2026-11-11 |
| 3 | Done 2026-11-11 |
| 4 | Done 2026-11-11; emulator checks done 2026-11-12 |
| 5 | Dropped (PL-Q5 a) |

**Emulator checks (2026-11-12, debug 0.9.20261112184527):** test files on the fake panel: one with
Français, English (default) and Commentary audio and Forcés, Français and English subtitles; one with
no-language audio labelled "VF" and "VO". Every row of "Which choice wins" passes on TV (Admin) and on the
phone (Teen):
- no setting: English audio, subtitles off;
- audio French: Français, never Commentary; with no subtitle setting the forced French track shows;
- audio English, subtitles French, switch on: English audio with the full French subtitles;
- audio French, subtitles French, switch on: Français with only the forced track;
- audio French, subtitles English, switch off: Français with English subtitles;
- "VF"/"VO" file: French gives VF; Malagasy (no such track) gives the original, VO;
- a saved choice for the title wins; an episode after a series pick takes the series choice;
- leaving the player saves no choice.
The rows and pickers fit in Malagasy, and the TV focus walk in English passes. Not checked: a live
channel with two audio tracks (no allowed source has one).

## Done notes

- **Phase 1 (2026-11-11):** `TrackLanguage.kt` (core/player domain): `normalisedLanguage`, `labelLanguage`, `audioLanguage`, `subtitleLanguage`, `playingAudioLanguage`, `isCommentary`, `isAudioDescription`, `isSdh`, `isForced`, `titleNamesSubtitles`, `pickAudioTrack`, `pickSubtitleTrack` (`SubtitlePick`: Keep, Off, Track), with `TrackLanguagePreference` and `ItemLanguages`. `languageOfCode` moved there from `SearchFacets.kt`, which now uses it. `AudioTrackInfo` and `SubtitleTrackInfo` carry `languageCode`, `roleFlags` and `selectionFlags`, filled by the service and `PlaybackViewModel`. A label code counts only in capitals or as the whole label, and codec words ("HE-AAC", "DTS-ES", "HI") never count. With no track in the subtitle language, or audio in an unknown language, the pick is Off; Off still shows a forced track in the audio's language. Tests: `TrackLanguageTest` (21).
- **Phase 2 (2026-11-11):** `AppSettings` keys `audio_language`, `subtitle_language` and `subtitles_other_audio_only`, per profile (`profileKey`) and in `SYNCED_SETTING_KEYS` and `PER_PROFILE_SETTING_KEYS`. `trackLanguages(profileId)` gives a `TrackLanguagePreference` (the switch on until set); `setAudioLanguage`, `setSubtitleLanguage` and `setSubtitlesOtherAudioOnly` queue the record (`SettingsSyncQueue.setting`). `applyRemoteSetting` keeps a blank value, a 2- or 3-letter lower-case code, and `off` for subtitles only, and drops any other value and a record for no profile (R-09). `ProfileRepository` removes the keys with the profile (`removeTrackLanguages`). Not in the backup file. docs/DATABASE_SCHEMA.md §4 lists the keys. Tests: `AppSettingsTrackLanguageTest` (10).
- **Phase 3 (2026-11-11):** `StartTracks.kt` (core/player domain): `startTracks` (item, then series, then profile, then stream default, each type on its own), `startTrackActions` (the changes to make once the tracks are known: a saved index applied and saved again; a profile pick not saved, and made only when it is not the track that plays), `preferredAudioLanguages`. `StreamState.Success` carries `startTracks`, `trackLanguages` (blank for Live TV and catch-up) and `itemLanguages` (the title's code and subtitle tags at load; TMDB's original language from the list item, else from the detail in the enrichment). The shared helper `applyStartTracks` (core/ui) replaces the restore blocks of `TvPlayerScreen` and `MobilePlayerScreen`; it reads the latest `itemLanguages`, so an original language that lands after the stream started still counts. `PlayerMetadata.preferredAudioLanguages` goes to `resetTrackChoices`, which sets `setPreferredAudioLanguages` (empty: `PlayerConfigFactory.DEFAULT_AUDIO_LANGUAGE`, "en"). The service's `selectAudioTrack`, `selectSubtitleTrack` and `disableSubtitles` take `persist` (true by default; the pickers do not change). Tests: `StartTracksTest` (13), two new cases in `StreamingPlaybackServiceTrackResetTest` (preferred languages, an Off that is not saved). docs/FEATURES.md → Player → Track selection.
- **Phase 4 (2026-11-11):** the rows on the profile's page, Playback section, after Play Next Episode Automatically, TV (`ProfileEditPane`) and phone (`ProfileOwnRows`, so Your Profile and Settings → Profiles → a profile): Audio Language (value: the language's name, English with no setting; description `settings_audio_language_desc`), Subtitle Language (Stream default, Off or a name) and, only while a language is picked, the switch Only When the Audio Is in Another Language. Pickers: TV `SettingsPickerPane` (as Colour, focus back on the row), phone `SettingsPickerDialog`. Choices and labels: `TrackLanguageLabels.kt` (core/ui): `trackLanguageCodes` (en, fr, mg, then 17 others by name; a code that is not listed, set on a newer version, is offered too), `trackLanguageLabel`, `audioLanguageChoices`, `subtitleLanguageChoices`. `ProfilesViewModel` saves each change at once (`ProfileRules.changesLook`: its own profile, or an admin). The audio picker has no Stream Default (PL-Q1 b). Tests: `TrackLanguageLabelsTest` (5); TV focus tests (`SettingsProfilePageFocusTest`, `SettingsProfilesFocusTest`) walk the two rows, and a new case picks a subtitle language and turns the switch off; phone `SettingsAdminTest` checks the rows and a picked subtitle language. `scripts/focus-walks/ci-settings.txt` edited by hand. docs: FEATURES (Profiles, Settings → Your Profile), NAVIGATION_GUIDE (TV Settings pickers), RELEASE_NOTES.

## Difficulty

Medium. Phases 1 and 2 are small, and the matching is pure code with tests. Phase 3 has the risk:
it touches track parameters that the service keeps across streams, and the restore path that the
series memory depends on. Phase 4 is two rows and a picker like Theme.

## Risks

- **Wrong language data:** a track that is tagged with the wrong language gets picked. The pickers still correct it, and that pick is saved for the item.
- **A switch at the start:** a stream that Media3 cannot match before the first frame switches its track once the helper runs, as a saved choice does today.
- **The per-stream reset changed behaviour (done 2026-11-11):** Subtitles Off no longer carries over to the next item. An item's default subtitle shows unless the item or the series says Off (and, after Phase 3, the profile).
- **Index memory (PL-Q7 a):** a series pick can still give the wrong language in an episode whose track order changed.
- **Out of scope:** Jellyfin external subtitle files do not show at all (no `SubtitleConfiguration`). It needs its own fix.
