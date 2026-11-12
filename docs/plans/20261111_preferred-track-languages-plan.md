# Preferred track languages

**Status:** Planned 2026-11-11, not started; the per-stream track reset was fixed on its own 2026-11-11. Open questions PL-Q1 to PL-Q7 wait for the user.

## Goal

Each profile sets the audio and subtitle languages it wants, for example "audio: French, else the
original; subtitles: French when the audio is not French". The player uses them when the item has
no saved track choice of its own and its series has none. TV and phone. Live sync takes the
setting to every device of the profile.

## What exists (survey 2026-11-11)

- **Per-item memory:** `WatchStateEntity.audioTrackIndex` and `subtitleTrackIndex` (watch_state).
  - The value is the index in the flattened track list of `StreamingPlaybackService.getAudioTracks()` / `getSubtitleTracks()`, not a language. Subtitles Off is `-1`.
  - Only a pick writes it: `selectAudioTrack(groupIndex, trackIndex)`, `selectSubtitleTrack(…)` and `disableSubtitles()` call `savePosition` with the index. Position saves send null, and the `WatchStateDao` upsert keeps the old value (`COALESCE`).
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

| # | Question | Options |
|---|----------|---------|
| PL-Q1 | What does a profile with no setting get? | (a) **The stream's default track.** Remove the fixed `"en"` from `PlayerConfigFactory`. Recommended: today a multi-audio film starts in English for every profile, and nobody chose that. (b) Keep English as the default. (c) The app language. |
| PL-Q2 | What can the audio setting say? | (a) **One language, then "the original".** The original is TMDB's original language when it is known, else a `qaa` track, else the stream's default track. Recommended: it is your example, and it is one row. (b) Two languages in order, then the stream's default track. (c) One language, then the stream's default track. |
| PL-Q3 | What can the subtitle setting say? | (a) **A language, and a switch "Only When the Audio Is in Another Language" (on by default).** Values: Stream Default, Off, or a language. Recommended: the switch covers your example, and "Always" helps a viewer who learns a language or does not hear well. (b) A language only, always with the rule "only when the audio is in another language". |
| PL-Q4 | Live TV and catch-up too? | (a) **Yes.** Live channels have no memory, so the profile setting is the only choice, for example the original-version (`qaa`) track of a French channel. Recommended. (b) Films and episodes only. |
| PL-Q5 | Jellyfin items that the server transcodes? | (a) **The Jellyfin server's choice**, from its user's language settings. Direct play and direct stream follow the profile in the player. Recommended for this plan: most items play direct. (b) Send `AudioStreamIndex` and `SubtitleStreamIndex`, picked from the item's media streams with the profile's languages (Phase 5). |
| PL-Q6 | Which languages does the picker offer? | (a) **A fixed list of about 20 common languages.** English, French and Malagasy come first, then the others by name in the app language: Arabic, Chinese, Dutch, German, Hindi, Italian, Japanese, Korean, Polish, Portuguese, Russian, Spanish, Turkish and others. Recommended: it stays short with the D-pad. (b) Every ISO 639-1 language that Android knows, by name. (c) The list of (a), then "More Languages" with (b). |
| PL-Q7 | Make the per-series memory follow a language? | (a) **No, keep the index (as today).** Recommended for this plan. (b) Also save the language of the picked track, and find the same language in the next episode. Today index 1 is English in one episode and French in the next when the order changes. (b) needs a migration 32→33, a new sync payload field and a backup field. Better as its own plan. |

## Design

### The setting

- Three per-profile keys, all strings, synced as `SETTING` records. The newer change wins.
  - `audio_language`: an ISO 639-1 code, or blank for the stream's default track (PL-Q1 a).
  - `subtitle_language`: a code, `off`, or blank for the stream's default.
  - `subtitles_other_audio_only`: a boolean, true by default (PL-Q3 a).
- `applyRemoteSetting` keeps a blank value, `off`, or 2 to 3 lower-case letters. It drops any other value and keeps the local one (the R-09 rule). It keeps a code that this version does not list, and the picker shows that code by `languageLabel`.
- **Deleting a profile** removes the keys in `ProfileRepository`.
- **Backup file:** the keys are not in it, as Play Next Episode Automatically.
- **Child profile:** no special rules.

### Which choice wins

Each track type (audio, subtitles) is decided on its own, in this order:

1. The item's own choice.
2. The series' choice (episodes only). The rule stays as today: the episode takes the series' choice only when it has no audio and no subtitle choice.
3. The profile's setting.
4. The stream's default track.

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

- **At each `playStream`:**
  - turn text back on (`setTrackTypeDisabled(TRACK_TYPE_TEXT, false)`);
  - clear the audio and text overrides;
  - set `setPreferredAudioLanguages(profile language, original language)`.
  Thus a well-tagged stream starts on the right track, with no switch after the first frame. The fixed `"en"` goes (PL-Q1 a).
- **Once the tracks are known:** one shared helper in core/ui replaces the two restore blocks of `TvPlayerScreen` and `MobilePlayerScreen`.
  - It applies a saved index as today. Saving it again keeps today's series chain.
  - For a type with no saved index, it runs the matching function. It changes a track only when its pick is not the one that plays.
- **A profile pick is never saved.** The service gets select calls with `persist = false`. Thus a later change of the setting still applies to items the viewer never picked for, and the series memory holds only the viewer's own picks.
- **Live TV and catch-up** use the same helper with no saved index (PL-Q4 a).
- **The pickers do not change.** A pick there is the item's own choice, as today.

### Settings rows (TV and phone)

- **Rows:** the Playback section of the profile page, after Play Next Episode Automatically:
  - **Audio Language:** value "Stream Default" or the language's name.
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

Off reuses `player_subtitles_off`. If Android has no Malagasy names for a language in the list (ICU), Phase 4 adds mg strings for the list.

## Phases

| Phase | What | Visible change |
|---|---|---|
| 1 | `TrackLanguage.kt`: track language, exclusions, audio and subtitle pick; raw fields on `AudioTrackInfo` and `SubtitleTrackInfo`; unit tests | None |
| 2 | The three keys, their sync, their removal with the profile; `AppSettingsTrackLanguageTest` | None |
| 3 | The player uses them: per-stream reset, Media3 preferred languages, the shared start-tracks helper on TV and phone, picks that are not saved, live and catch-up (PL-Q4); `startTracks` tests | Items with no saved choice start in the profile's languages (set only by sync so far) |
| 4 | The Settings rows and pickers on TV and phone; strings en, fr, mg; docs | Users set their languages |
| 5 | Only with PL-Q5 (b): `Index` on `JellyfinMediaStream`; the indices sent with `PlaybackInfo` | Jellyfin transcodes follow the profile |

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
  - A live channel with two audio tracks.
  - The rows and pickers in Malagasy, with focus on TV in English.
- **Docs:**
  - docs/FEATURES.md → Player → Track selection (Phase 3).
  - Settings → Your Profile, and Profiles → A profile's page (Phase 4).
  - docs/RELEASE_NOTES.md (Phases 3 and 4).
  - docs/design.md → Jellyfin Playback (Phase 5).

## Progress

| Phase | Status |
|---|---|
| 1 | Not started |
| 2 | Not started |
| 3 | Not started |
| 4 | Not started |
| 5 | Only with PL-Q5 (b) |

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
