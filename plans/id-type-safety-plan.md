# Plan: stop routing on untyped ids and string maps

## Status (2026-08-19)
Proposed, nothing started. Written after the "Provider has no get_series_info for id
242136" bug on the phone, which was the third symptom of the same defect class this
week ("0 episodes" screens being the earlier two).

## Context

A Recent card in TV Shows opened the episode screen for id `242136` and the provider
answered "no such series" — because `242136` is an **episode stream id**, not a series
id. The app had no way to tell the two apart: both are `String`, and the destination
screen was chosen by looking up string keys in a `Map<String, String>`.

Immediate fix already landed at the read site (`MediaRepository.toRecentMediaItem`):
episode cards now always carry `episodeId`, and a row whose `seriesId` equals its own
`itemId` is demoted back to an episode card. That stops the bleeding. It does not
remove the defect class — it adds two more guards to the pile.

**Caveat carried into this plan:** the phone was not on adb when the bug was analysed,
so the exact shape of the offending history row is inferred from the screenshot, not
read. Phase 0 settles it. Every later phase stands on its own regardless of the answer.

## The three sources of fragility

### 1. Destination chosen by string-key presence
`MediaItem.providerData: Map<String, String>` (`core/player/.../domain/MediaItem.kt:40`)
carries routing decisions. 20 read sites; the routing keys are `resumeSeries`,
`episodeId`, `seriesId`, `seriesName`, `episodeExtension`, `isCategoryRef`,
`categoryId`. Both nav hosts branch on presence/absence:

- `mobile/.../navigation/MobileNavHost.kt:238`
- `tv/.../navigation/TvNavHost.kt:295`

A **missing** key does not fail — it silently selects a different screen. That is
exactly how an episode id reached `Screen.EpisodeSelection(seriesId = itemId)`.

### 2. Every id is `String`
149 declarations of `seriesId: String` / `episodeId: String` / `streamId: String` /
`movieId: String` / `vodId: String` across `core`, `mobile`, `tv`. Nothing at any
boundary can reject an episode id passed where a series id belongs; they are the same
type all the way down to `seriesId.toIntOrNull()` at
`XtreamMediaProvider.kt:186`.

### 3. Watch history is a schema-less blob with optional fields
`WatchedItem` (`MediaRepository.kt:38`) is serialized to JSON in SharedPreferences under
`watch_history_v2`. `episodeId`/`seriesId`/`seriesName` were added in `1822a6e5`
(2026-03-10) as nullable with no migration, so rows written before that date are a
second, undocumented schema that every reader must handle forever. The existing test
fixture `episode()` always sets `episodeId`, which is why the null case shipped
unexercised.

Aggravating environment: the active provider is a proxy that answers `[]`, well-formed
empty objects, and series with no name. Six commits on 2026-08-19 fix that class of
thing one call site at a time (`1975d0ab`, `3a1ec93c`, `4220ce69`, `72eb2e90`, ...).

---

## Phase 0 — read the device before designing around a guess
**Effort: 10 minutes.**

With the phone on adb:

```
adb -s <serial> exec-out "run-as org.njarasoa.fijerena cat shared_prefs/media_cache_<providerId>.xml" \
  | grep -o 'watch_history_v2[^<]*'
```

Classify every `TV_SHOWS` row into: (a) no `seriesId` at all — legacy pre-March row;
(b) `seriesId == itemId` — poisoned by the mis-route; (c) healthy. The mix decides
whether Phase 3's migration needs to repair rows or only normalize them, and confirms
or kills the propagation theory. Record the answer in this file.

### Result (2026-08-19, phone `b048cf47`, 12 TV_SHOWS rows across providers 2/8/9)

All shape (a): no `episodeId`, no `seriesId`, no `episodeExtension`. **No row of shape
(b) exists** — the propagation theory was wrong, and the `seriesId == itemId` guard in
`toRecentMediaItem` guards a shape nothing produces.

Ids confirmed against the device's own catalogue (`xtream_v2.db`): `242136` is
`xtream_episodes.id`, S06E18 of series **4080** "EN - Law & Order (1990) (US)", which is
present locally with 543 episodes. It is not a stream id and not a series id anywhere.
So the provider was right and the app asked with an episode id.

**These rows are not old.** They are being written continuously — three on 2026-08-19
(14:30, 15:40, 18:17). Sorting all 12 rows by watched fraction separates them perfectly:

| metadata | watched | rows |
|---|---|---|
| present | 2.08 % – 96.32 % | 6 |
| absent | 0.08 % – 1.33 % | 6 |

Cause is in `StreamLoaderViewModel.recordHistory`/`stopPlayback`: the 2 % gate wraps
`saveLastPlayedItem` — the only call that carries `episodeId`/`seriesId`/`seriesName`/
extension — while `savePlaybackPosition` runs unconditionally and creates the row via
`addToWatchHistory`, inheriting metadata from an existing row that does not exist yet.
Sample under 2 % of an episode and quit, and history gains a row with a position and no
identity. That is the source of every broken Recent card, and it needs a write-side fix
(pass the metadata into `savePlaybackPosition`, or move it outside the gate) — the
read-side guard already shipped only stops that row from mis-routing.

## Phase 1 — a typed browse target, not a string map
**Effort: ~4 hours. Kills the defect class that caused this bug.**

Add to `core/player/.../domain/`:

```kotlin
@Immutable
sealed interface BrowseTarget {
    data class Channel(val streamId: String) : BrowseTarget
    data class Movie(val movieId: String) : BrowseTarget
    data class Series(val seriesId: String, val resumeEpisodeId: String?) : BrowseTarget
    data class Episode(val episodeId: String, val seriesId: String?, val seriesName: String?) : BrowseTarget
    data class CategoryRef(val categoryId: String) : BrowseTarget
}
```

- `MediaItem` gains `val target: BrowseTarget?`. `providerData` stays for genuinely
  provider-internal payload (`smbPath`, `epgChannelId`, container extension) and stops
  being a routing channel.
- `MediaRepository.toRecentMediaItem` and the virtual-category builders in
  `CategoryViewModel` (`FAVORITE_CATEGORIES_ID` branch) produce `target` directly.
- Both nav hosts replace their key-sniffing `if/else` with an exhaustive `when (target)`.
  A new target variant then fails the build instead of falling through to a wrong screen.
- `Screen.*` destinations already are type-safe; they are unchanged.

Success test: delete the two guards added today from `toRecentMediaItem` and the bug
must still be impossible, because an `Episode` target cannot reach `EpisodeSelection`.

Compose note: `MediaItem` is `@Immutable` for skipping (see its KDoc). `BrowseTarget`
must be `@Immutable` too, and hold only stable types, or every list row loses its skip.

### Done (2026-08-19)

`BrowseTarget` lives in `core/player/domain`, with `Channel` / `Movie` / `Series` /
`Episode` / `CategoryRef`, and `MediaItem` carries it. Both nav hosts now `when` over it
and no longer read a single routing key; the fallback for a plain catalogue row — the row
that carries no target of its own — is `browseTargetFor(contentType, id)`, the one place
content type is read as intent.

`providerData` is out of the routing business entirely. What is left in it across the
whole app is `smbPath` and `epgChannelId`, both genuine provider payload.

Follow-up, done the same day: `playbackPosition`, `duration`, `isCompleted`, `isFavorite`,
`streamType`, `tvArchive` and `directSource` were all written into `providerData` and read
by nobody — list progress comes from `CategoryViewModel.watchProgress`. Removed. What is
left in the map anywhere is `epgChannelId` (matched by the EPG pipeline) and `smbPath`,
both of which have a reader.

## Phase 2 — value-class ids, starting with the pair that actually collided
**Effort: ~4 hours, mechanical but wide.**

```kotlin
@JvmInline @Serializable value class SeriesId(val raw: String)
@JvmInline @Serializable value class EpisodeId(val raw: String)
```

- Roll out to `BrowseTarget`, `WatchedItem`, `Screen.EpisodeSelection`, the
  `MediaProvider` interface and `MediaRepository` signatures — the boundaries where a
  swap is possible. **Not** every local variable; that is churn without safety.
- Keep the DB/API edge on raw `String`/`Int`; convert at the boundary (`.raw`) so Room
  and the Xtream query params need no converters.
- `StreamId`/`MovieId` afterwards, same shape, only if Phase 2 lands cleanly.

`@JvmInline` means no allocation at runtime, and kotlinx-serialization handles value
classes, so nav-arg serialization keeps working.

### Done (2026-08-19)

`SeriesId` and `EpisodeId` are `@JvmInline @Serializable` value classes in
`core/player/domain`. They are carried by `BrowseTarget`, by `WatchedItem`, by
`MediaProvider.getSeriesDetail` and its four implementations, and by the history-write
signatures on `MediaRepository`. Each provider unwraps once at its own edge (`.raw`),
where the provider's numbering takes over.

**Nav args stayed `String`, deliberately.** Typing `Screen.EpisodeSelection` would need a
custom `NavType` per id for Navigation Compose's type-safe routes, and the destinations
are now built inside an exhaustive `when (target)` that already knows which id it holds —
so the conversion sits at the two nav hosts (`target.seriesId.raw`) and buys nothing more.

`WatchedItemSerializationTest` pins the thing that would have been catastrophic and
silent: a value class serializes as the bare string it wraps, so watch history written by
every earlier version still reads, and still writes back byte-identical. The fixture is a
real row copied off the phone.

## Phase 3 — version the watch-history schema and migrate once
**Effort: ~3 hours.**

- Bump the key to `watch_history_v3`, with a one-time read of `watch_history_v2` that
  normalizes every row: `TV_SHOWS` rows always get an `episodeId` (falling back to
  `itemId`), and a `seriesId` equal to `itemId` is dropped rather than trusted.
- After migration, `toRecentMediaItem` needs no defensive `?:` at all — delete the two
  guards from today and let the shape carry the meaning.
- Keep the v2 reader for one release, then drop it.

## Phase 4 — one place that decides "the provider gave us nothing"
**Effort: ~4 hours.**

Today each Xtream call site invents its own emptiness check: `[]` at
`XtreamApiService.kt:280`, the empty-shell retry at `XtreamMediaProvider.resolveSeriesInfo`,
the missing-name fix in `1975d0ab`, the don't-cache-empty fix in `3a1ec93c`.

Collapse into a single normalization at the API boundary returning a sealed
`ProviderResponse<T>` of `Ok(value)` / `Unavailable(id, action)` / `Malformed(raw)`, so
"provider returned junk" is handled once and every caller gets the same three cases to
`when` over. Seed it with response fixtures captured from the real proxy.

### Done (2026-08-19)

Landed as `XtreamResponse<T>` in `core/player/api`, with four cases rather than three:
`Failed(cause)` was needed so a call that never completes travels the same channel as one
that completed with nothing, which is what let `getSeriesInfo`/`getVodInfo` stop returning
`Result` and stop throwing.

`XtreamApiService.fetchItem` is now the only place that reads a single-item response, so
`[]`, an object with nothing in it, an unparseable body and a dead connection are decided
once. `resolveSeriesInfo` shrank to its actual policy: one retry, then re-resolve the id
by name. Both recoveries now apply to every kind of nothing — previously a `[]` skipped
the retry and an empty object skipped the name lookup, for no reason either could defend.

**Left as it was, and now settled:** a response carrying an `info` object but no episodes
reads as `Ok`, and the screen shows a show with nothing under it. Treating it as
unavailable would start erroring on shows that are genuinely empty in the catalogue, and
nothing in the response distinguishes those from a provider having a bad day. Asked and
answered on 2026-08-19: leave it. `XtreamResponseTest` pins the behaviour, so a future
reader finds the decision rather than re-deriving it.

## Phase 5 — fixtures that encode the broken shapes
**Effort: ongoing, ~2 hours to seed.**

`MediaRepositoryRecentItemsTest.episode()` always set `episodeId`, so the shape that
broke was structurally untestable. Replace with a fixture matrix covering: legacy row
(no episode/series ids), poisoned row (`seriesId == itemId`), healthy row, server-backed
row. Assert every row maps to a `BrowseTarget` and that no `TV_SHOWS` row can ever
produce `Series` with an id that also appears as an `itemId` in history.

---

## Sequencing

| Phase | Effort | Kills |
|---|---|---|
| 0 — read device history | 10 min | the guessing |
| 1 — `BrowseTarget` | ~4 h | routing-by-key-presence (this bug) |
| 2 — value-class ids | ~4 h | id-swap bugs at every boundary |
| 3 — history v3 + migration | ~3 h | the second undocumented schema |
| 4 — provider normalization | ~4 h | the weekly "provider returned junk" fix |
| 5 — fixture matrix | ~2 h | shapes that ship untested |

Phases 1 and 3 together are the minimum that makes today's guards deletable. Phase 2 is
what makes the *next* one impossible rather than merely fixed. Phase 4 is independent —
it can be done first if provider junk keeps costing more than routing bugs.

## Non-goals

- Rewriting `MediaItem` into a sealed hierarchy per provider. Every provider builds it;
  the churn is large and the payoff is Phase 1's, already had for less.
- Touching the Room schema or the Xtream DB. Ids stay raw at that edge on purpose.
- Retro-fitting typed ids into EPG/XMLTV code, which has its own id space and no
  history of confusing them.
