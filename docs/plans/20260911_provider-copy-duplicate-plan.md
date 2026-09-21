# Provider copy / duplicate plan

On-device only. No JSON export/import, no file I/O — direct Room DAO / EncryptedSharedPreferences
copies between two `ProviderEntity` rows. Reuses `ProviderRepository`, `FavoriteStateDao`,
`WatchStateDao`. Does not touch `SettingsExportManager` (that stays the cross-device backup path).

## Two entry points, one core

1. **Copy to…** — pick an existing target provider, copy selected data from source into it.
2. **Duplicate** — create a new provider cloned from source (credentials + settings), then copy
   its favorites/watch history in.

Both call the same core copy routine for favorites/watch-history/settings; duplicate additionally
creates the target row first.

## New class: `ProviderCopyManager`

`core/network/src/main/java/org/njarasoa/fijerena/core/network/provider/ProviderCopyManager.kt`,
constructed the same way as `SettingsExportManager(context)`.

```kotlin
data class CopyOptions(
    val copyConnection: Boolean = false,   // URL/username/password/type/config — off by default, destructive
    val copyProviderSettings: Boolean = true,
    val copyFavorites: Boolean = true,
    val copyWatchHistory: Boolean = true,
)

data class CopyResult(
    val connectionCopied: Boolean = false,
    val settingsCopied: Boolean = false,
    val favoritesCopied: Int = 0,
    val favoriteCategoriesCopied: Int = 0,
    val watchStateCopied: Int = 0,
)

suspend fun copyProviderData(sourceId: Long, targetId: Long, options: CopyOptions): CopyResult
suspend fun duplicateProvider(sourceId: Long, newName: String): Long?
```

### `copyProviderData(sourceId, targetId, options)`

- No-op / early return if `sourceId == targetId` or either provider missing.
- `copyConnection`: `providerRepo.updateProvider(targetId, name = target.name, url = source.url,
  username = source.username, password = providerRepo.getPassword(sourceId) ?: "", type =
  source.type, config = source.config)`. Target's **own name is kept** — this repoints
  credentials/URL, it doesn't rename the row. Reuses `updateProvider`, so Jellyfin token
  invalidation and `MediaProviderFactory.clearCache` stay correct for free.
- `copyProviderSettings`: `providerRepo.updateProviderSettings(targetId,
  providerRepo.getProviderSettings(sourceId))`.
- `copyFavorites`: read `favoriteStateDao.getAll(sourceId)`, remap `providerId = targetId`, drop
  rows whose `(itemId, contentType, kind)` already exists on target (**existing wins** — merge, not
  overwrite), `restoreAll(...)` the rest. Covers both `FavoriteKind.STREAM` and `CATEGORY` in one
  pass (table already holds both).
- `copyWatchHistory`: read `watchStateDao.getAll(sourceId)`, remap `providerId = targetId`. Must
  pre-filter by `(itemId, contentType)` already present on target before calling `restoreAll` —
  unlike the backup-import path, `restoreAll` is a raw `REPLACE`, and here overwrite would violate
  the merge/existing-wins policy.
- EPG sources are **out of scope** — not part of the original ask, left untouched.

### `duplicateProvider(sourceId, newName)`

- `source = dao.getProviderById(sourceId) ?: return null`.
- `newId = providerRepo.addProvider(name = newName, url = source.url, username = source.username,
  password = providerRepo.getPassword(sourceId) ?: "", type = source.type, config = source.config,
  initialSettings = providerRepo.getProviderSettings(sourceId), activate = false)`.
- Then `copyProviderData(sourceId, newId, CopyOptions(copyConnection = false, copyProviderSettings
  = false, copyFavorites = true, copyWatchHistory = true))` — connection/settings already set at
  creation, only favorites/history need copying.
- Default `newName` from caller: `"${source.name} (copy)"`; editable in the dialog before confirm.

### `ProviderRepository.addProvider` — one signature change

Add `activate: Boolean = true` (default preserves every existing call site). When `false`, skip
`dao.deactivateAll()` and insert with `isActive = false`. Needed so duplicating a provider doesn't
steal "active" away from whatever the user currently has selected.

## ViewModel

`core/ui/.../ProviderViewModel.kt`: add

- `duplicateProvider(sourceId: Long, newName: String)` → calls `ProviderCopyManager`, refreshes
  list, surfaces a result/toast.
- `copyProviderData(sourceId: Long, targetId: Long, options: CopyOptions)` → same, surfaces
  `CopyResult` for a summary toast ("Copied: settings, 42 favorites, 118 watch-history rows").

## UI — TV (`tv/.../ProviderSelectionScreen.kt`) and mobile mirror

Provider row already has 4 icon buttons (select/EPG/edit/delete). Add 2 more, same
`CinemaIconButton` pattern already used there:

- **Duplicate** (content-copy icon) → small dialog: editable name field, prefilled
  `"<name> (copy)"`, Confirm/Cancel. Confirm calls `duplicateProvider`.
- **Copy to…** (compare-arrows icon) → dialog: radio list of *other* providers as target, 4
  checkboxes seeded from `CopyOptions` defaults (Connection & credentials **off**, the other 3
  **on**). Checking "Connection & credentials" shows an inline warning ("This overwrites
  `<target>`'s URL, username and password"). Confirm/Cancel. Confirm calls `copyProviderData`.
  Disabled/hidden when there's only one provider (no valid target).

Reuse `CinemaAlertDialog` (already used for the delete-confirm dialog in this screen) for both.

New strings in the shared `core/ui` strings.xml: button content-descriptions, dialog titles,
checkbox labels, the credentials-overwrite warning, and a result-summary format string. Mobile
screen gets the identical two buttons/dialogs in whatever action-row pattern it already uses.

## Tests

No JVM unit test added. `ProviderCopyManager` constructs `ProviderRepository`,
`SettingsDatabase.getInstance()` and `XtreamDatabase.getInstance()` directly — real Room/
EncryptedSharedPreferences, not injectable without a DI seam this module doesn't have. Its sibling
`SettingsExportManager`, doing the same class of work over the same repositories, has no JVM test
either (this module has no Robolectric); `FakeWatchStateDao` exists but only covers code that takes
a DAO as a parameter, which `ProviderCopyManager` doesn't. Verified instead by compiling all four
touched modules (`core:network`, `core:ui`, `mobile`, `tv`) and `ktlintCheck`, both clean.

## Explicitly out of scope

- EPG sources.
- Cross-device transfer (that's the existing file export/import, untouched).
- Any change to `SettingsExportManager`.
