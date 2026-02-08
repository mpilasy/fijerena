# Implementation Plan: XMLTV EPG SQLite Indexing for Fast Search

## Problem

The EPG browser search scans a raw 500MB+ XMLTV file on every query using streaming `XmlPullParser`. This takes multiple seconds per search. The goal is to parse the XML once into SQLite and then answer queries via indexed SQL in <100ms.

## Architecture

### Separate Room Database

A dedicated `EpgIndexDatabase` in `core/network`, separate from `ProviderDatabase`. Cache-like — can be deleted and rebuilt from the XML at any time.

### Schema

**`epg_channel`**
```sql
CREATE TABLE epg_channel (
    xmltv_id TEXT NOT NULL PRIMARY KEY,
    display_name TEXT NOT NULL,
    icon_url TEXT
);
```

**`epg_programme`**
```sql
CREATE TABLE epg_programme (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id TEXT NOT NULL,
    title TEXT NOT NULL,
    title_lowercase TEXT NOT NULL,
    description TEXT,
    category TEXT,
    start_epoch INTEGER NOT NULL,
    end_epoch INTEGER NOT NULL,
    FOREIGN KEY (channel_id) REFERENCES epg_channel(xmltv_id)
);
CREATE INDEX idx_programme_start ON epg_programme(start_epoch);
CREATE INDEX idx_programme_channel ON epg_programme(channel_id);
CREATE INDEX idx_programme_title_lower ON epg_programme(title_lowercase);
```

**`epg_programme_fts` (FTS4)**
```sql
CREATE VIRTUAL TABLE epg_programme_fts USING fts4(
    content="epg_programme",
    title,
    tokenize=unicode61
);
```

**`epg_index_metadata`** — single-row table tracking source file size + last-modified timestamp to detect when re-indexing is needed.

### Room Entities

```kotlin
@Entity(tableName = "epg_channel")
data class EpgChannelEntity(
    @PrimaryKey @ColumnInfo(name = "xmltv_id") val xmltvId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "icon_url") val iconUrl: String? = null
)

@Entity(tableName = "epg_programme", foreignKeys = [...], indices = [...])
data class EpgProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "channel_id") val channelId: String,
    val title: String,
    @ColumnInfo(name = "title_lowercase") val titleLowercase: String,
    val description: String? = null,
    val category: String? = null,
    @ColumnInfo(name = "start_epoch") val startEpoch: Long,
    @ColumnInfo(name = "end_epoch") val endEpoch: Long
)

@Fts4(contentEntity = EpgProgrammeEntity::class, tokenizer = "unicode61")
@Entity(tableName = "epg_programme_fts")
data class EpgProgrammeFts(val title: String)

@Entity(tableName = "epg_index_metadata")
data class EpgIndexMetadata(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "file_last_modified_ms") val fileLastModifiedMs: Long,
    @ColumnInfo(name = "indexed_at_ms") val indexedAtMs: Long,
    @ColumnInfo(name = "channel_count") val channelCount: Int,
    @ColumnInfo(name = "programme_count") val programmeCount: Int
)
```

### DAO — Key Queries

```kotlin
@Dao
interface EpgIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Insert
    suspend fun insertProgrammes(programmes: List<EpgProgrammeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: EpgIndexMetadata)

    // Primary: FTS search
    @Query("""
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_programme_fts fts ON fts.rowid = p.id
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE epg_programme_fts MATCH :query
          AND p.start_epoch >= :windowStart AND p.end_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        LIMIT :maxResults
    """)
    suspend fun searchByTitleFts(query: String, windowStart: Long, windowEnd: Long, maxResults: Int = 500): List<EpgSearchResultRow>

    // Fallback: LIKE search (for queries with FTS-breaking special chars)
    @Query("""
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE p.title_lowercase LIKE '%' || :queryLower || '%'
          AND p.start_epoch >= :windowStart AND p.end_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        LIMIT :maxResults
    """)
    suspend fun searchByTitleLike(queryLower: String, windowStart: Long, windowEnd: Long, maxResults: Int = 500): List<EpgSearchResultRow>

    @Query("SELECT * FROM epg_index_metadata WHERE id = 1")
    suspend fun getMetadata(): EpgIndexMetadata?

    @Query("DELETE FROM epg_programme")
    suspend fun deleteAllProgrammes()

    @Query("DELETE FROM epg_channel")
    suspend fun deleteAllChannels()
}
```

### Database Class

```kotlin
@Database(
    entities = [EpgChannelEntity::class, EpgProgrammeEntity::class, EpgProgrammeFts::class, EpgIndexMetadata::class],
    version = 1, exportSchema = false
)
abstract class EpgIndexDatabase : RoomDatabase() {
    abstract fun epgIndexDao(): EpgIndexDao

    companion object {
        // Singleton + destroy() method for re-indexing
        // Uses fallbackToDestructiveMigration() since this is a cache DB
    }
}
```

## Indexing Lifecycle

### State Machine

```kotlin
sealed interface EpgIndexState {
    data object NotIndexed : EpgIndexState
    data class Indexing(val progressPercent: Int, val channelsIndexed: Int, val programmesIndexed: Int) : EpgIndexState
    data class Indexed(val channelCount: Int, val programmeCount: Int, val indexedAtMs: Long) : EpgIndexState
    data class Failed(val reason: String) : EpgIndexState
}
```

### EpgIndexer (singleton)

- **Trigger:** Called by `EpgFileManager` when state → `Ready` (both on fresh download and on app launch with cached file)
- **Staleness check:** Compare `file.length()` + `file.lastModified()` against stored metadata
- **Full re-index strategy:** Destroy DB, recreate, streaming parse with batch INSERT (1000 rows per transaction)
- **FTS rebuild:** After all inserts, execute `INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')` for efficiency
- **Progress reporting:** Estimate % from bytes processed vs file size (~200 bytes per programme element)
- **Memory bounded:** 1000-row batches ≈ 200KB in memory

### Integration with EpgFileManager

In `EpgFileManager`, after setting `_state.value = EpgFileState.Ready(...)`:
```kotlin
scope.launch {
    val indexer = EpgIndexer.getInstance(context)
    if (indexer.needsReindex(xmltvFile)) {
        indexer.startIndexing(xmltvFile)
    } else {
        indexer.initialize() // Restore Indexed state from metadata
    }
}
```

Same check in `initialize()` after restoring Ready from cache.

## Search Changes

### Dual-Path XmltvSearchService

```
search(query) →
  if EpgIndexState is Indexed → SQL query (FTS MATCH, fallback to LIKE)
  else → XML scan (existing behavior)
```

- **FTS query:** Wrap user input in double quotes + `*` suffix for prefix matching
- **Fallback LIKE:** For queries with special characters that break FTS syntax
- **Time window:** Same as current — past 1 day to future 6 days
- **Returns:** Same `XmltvSearchResult` type — transparent to callers

### Performance

- FTS MATCH: ~5-20ms
- LIKE fallback: ~50-200ms (on time-windowed subset)
- Well under 100ms target for most queries

## ViewModel Changes

### EpgBrowserViewModel

- Add `UiState.Indexing(progressPercent, programmesIndexed)` variant
- Collect `EpgIndexer.state` and map to UI state
- Add `searchedFromIndex: Boolean` to `Results` state
- Search still works during indexing (falls back to XML scan)

## UI Changes

### EPG Browser Screens (TV + Mobile)

- Handle `UiState.Indexing` — show progress banner below search bar with `LinearProgressIndicator`
- Search still available during indexing (XML fallback)
- Show "Indexed" vs "XML scan" indicator in results metadata

### Settings Screens (TV + Mobile)

- Below EPG file size, show index status: "Search index: 2.3M programmes, 1,200 channels" or "Indexing: 45%" or "Not built"

## File List

### New Files (in `core/network/.../xmltv/epgindex/`)

| File | Purpose |
|------|---------|
| `EpgChannelEntity.kt` | Room entity for channels |
| `EpgProgrammeEntity.kt` | Room entity for programmes + FTS entity |
| `EpgIndexMetadata.kt` | Room entity for index metadata |
| `EpgSearchResultRow.kt` | Data class for JOIN query results |
| `EpgIndexDao.kt` | Room DAO |
| `EpgIndexDatabase.kt` | Room database singleton |
| `EpgIndexer.kt` | Indexing orchestrator |
| `EpgIndexState.kt` | Sealed interface for state machine |

### Modified Files

| File | Changes |
|------|---------|
| `core/network/.../xmltv/XmltvSearchService.kt` | Dual-path: SQLite first, XML fallback |
| `core/network/.../xmltv/EpgFileManager.kt` | Trigger indexer on Ready state |
| `core/ui/.../viewmodels/EpgBrowserViewModel.kt` | Add Indexing state, observe indexer, searchedFromIndex flag |
| `tv/.../feature/epgbrowser/EpgBrowserScreen.kt` | Indexing progress banner, search source indicator |
| `mobile/.../feature/epgbrowser/MobileEpgBrowserScreen.kt` | Same as TV |
| `tv/.../feature/settings/SettingsScreen.kt` | Index status display |
| `mobile/.../feature/settings/SettingsScreen.kt` | Index status display |

### No Changes Needed

- `core/network/build.gradle.kts` — Room dependencies already present
- `XmltvParser.kt` — Reuse existing parse methods from indexer
- `XmltvModels.kt` — Still used for XML fallback path
- `EpgBrowserModels.kt` — ViewModel mapping unchanged
- Navigation, Screen.kt — No new routes

## Implementation Sequence

1. **Phase 1 — Database layer:** Entities, DAO, Database class
2. **Phase 2 — Indexer:** Streaming parse + batch insert + state management
3. **Phase 3 — Search integration:** Modify XmltvSearchService for dual-path
4. **Phase 4 — Lifecycle:** Wire EpgFileManager → EpgIndexer
5. **Phase 5 — ViewModel:** Add indexing state observation
6. **Phase 6 — UI:** Progress banners + settings status

## Edge Cases

- **Indexing interrupted (app killed):** No valid metadata → re-index on next launch
- **Disk space:** SQLite DB ~200-400MB for 500MB XML. Room insert throws on disk full → `Failed` state
- **Concurrent reads during indexing:** Room WAL mode allows it. During DB destroy/recreate window, search falls back to XML
- **OOM during indexing:** 1000-row batches ≈ 200KB — negligible memory
- **FTS query injection:** User input wrapped in double quotes, special chars escaped
- **Empty XML:** Zero results, metadata stored with count=0
