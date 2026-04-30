# Plan: Complete Removal of Jellyfin Integration

This plan outlines the steps to remove all references to Jellyfin from the Fijerena project, implement a startup migration to purge existing Jellyfin data, and ensure future imports ignore Jellyfin-related records.

## Objective
- Completely decouple the application from Jellyfin.
- Clean up the database and internal settings on first launch after the update.
- Prevent Jellyfin providers from being imported from legacy settings files.
- Remove all code, resources, and documentation related to Jellyfin.

## Key Files & Context
- **Migration:** `ProviderDao.kt`, `ProviderRepository.kt`, `AppSettings.kt`, `FijerenaApplication.kt`.
- **Import Logic:** `SettingsExportManager.kt`.
- **Core Logic:** `ProviderType.kt`, `MediaProviderFactory.kt`, `ProviderViewModel.kt`.
- **UI:** `JellyfinForm.kt`.
- **Build Files:** `core/player/build.gradle.kts`, `core/network/build.gradle.kts`.

## Implementation Steps

### Phase 0: Documentation
1. **Save Plan:** Write this plan to `docs/remove_jellyfin.md`.

### Phase 1: Migration Infrastructure (Persistence & Settings)

1. **Modify `core/network/.../provider/ProviderDao.kt`**
   - Add `@Query("DELETE FROM providers WHERE type = :type") suspend fun deleteProvidersByType(type: String)` to allow batch removal of Jellyfin providers.

2. **Modify `core/network/.../AppSettings.kt`**
   - Add a new preference key `KEY_JELLYFIN_REMOVED = "jellyfin_removed"`.
   - Add a corresponding property `var isJellyfinRemoved: Boolean` (default `false`).

3. **Modify `core/network/.../provider/ProviderRepository.kt`**
   - Add a `migrateJellyfinRemoval()` function that:
     - Fetches all providers with type `JELLYFIN`.
     - Iterates through them and calls `deleteProvider(id)` for each (this ensures encrypted passwords and caches are also cleared).

4. **Modify `core/ui/.../FijerenaApplication.kt`**
   - In `onCreate()`, instantiate `AppSettings` and `ProviderRepository`.
   - If `!appSettings.isJellyfinRemoved`:
     - Run `providerRepository.migrateJellyfinRemoval()` in a coroutine.
     - Set `appSettings.isJellyfinRemoved = true`.

### Phase 2: Import Logic Update

1. **Modify `core/network/.../SettingsExportManager.kt`**
   - In `importFromJson()`:
     - Filter `exported.providers` to exclude those with `type == "JELLYFIN"`.
     - Filter `exported.providerFavorites` and `exported.providerFavoriteCategories` to exclude any entries associated with Jellyfin providers.

### Phase 3: Code and Resource Removal

1. **Modify `core/player/.../domain/ProviderType.kt`**
   - Remove `JELLYFIN("Jellyfin")` entry.

2. **Delete Jellyfin Implementation Package**
   - Remove `core/network/src/main/java/org/njarasoa/fijerena/core/network/jellyfin/` directory and all its files.

3. **Modify `core/network/.../MediaProviderFactory.kt`**
   - Remove imports and logic related to `JellyfinMediaProvider`.
   - Delete `createJellyfin()` and `getJellyfinSessionPrefs()` helper methods.

4. **Modify `core/ui/.../viewmodels/ProviderViewModel.kt`**
   - Remove `quickConnectSave()` method.
   - Remove `JELLYFIN` case from `testConnection()`.

5. **Delete TV UI Form**
   - Remove `tv/src/main/java/org/njarasoa/fijerena/tv/feature/provider/components/JellyfinForm.kt`.
   - Update `TvAddProviderScreen.kt` to remove references to `JellyfinForm`.

6. **Cleanup other ViewModels**
   - Remove Jellyfin-specific comments and logic from `SearchViewModel.kt`, `StreamLoaderViewModel.kt`, and `PlaybackViewModel.kt`.

7. **Update Build Files**
   - In `core/player/build.gradle.kts`, update the comment for `media3-ffmpeg-decoder`.
   - In `core/network/build.gradle.kts`, remove Ktor dependencies if unused.

### Phase 4: Documentation Update

1. **Update Markdown Files**
   - Remove Jellyfin references from `README.md`, `AGENTS.md`, `design.md`, `FEATURES.md`, `DATABASE_SCHEMA.md`, `NAVIGATION_GUIDE.md`, and `RELEASE_NOTES.md`.

## Verification & Testing

1. **Migration Test:** Verify Jellyfin providers are purged on first launch and `isJellyfinRemoved` flag is set.
2. **Import Test:** Verify Jellyfin providers are skipped during settings import.
3. **Build Test:** Ensure the project compiles without errors.
4. **UI Test:** Ensure Jellyfin is no longer an option in the "Add Provider" screens.
