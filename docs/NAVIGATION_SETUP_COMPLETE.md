# Navigation Setup - Status

**Status:** Complete and evolved well beyond initial setup.

This file was created during initial navigation scaffolding. The navigation system has since been significantly expanded. For current navigation documentation, see:

- **NAVIGATION_GUIDE.md** — Full navigation guide with screen definitions, flows, and integration
- **CLAUDE.md** — Project context including navigation flow overview
- **FEATURES.md** — Feature documentation including navigation destinations

## Current Navigation Destinations

All destinations defined in `core/navigation/Screen.kt`:

| Screen | Type | Description |
|--------|------|-------------|
| `ContentTypeSelection` | `data object` | Main landing page — choose Live TV, Movies, or TV Shows |
| `CategoryList` | `data class` | Browse categories for a content type |
| `Player` | `data class` | Video playback with stream parameters |
| `MovieDetails` | `data class` | Movie info and play button |
| `EpisodeSelection` | `data class` | TV show seasons and episodes |
| `Search` | `data class` | Search across categories |
| `EpgGuide` | `data class` | TV Guide with 24-hour grid |
| `Settings` | `data object` | App configuration, themes, provider management |
| `ProviderSelection` | `data object` | List, select, edit, delete providers |
| `AddProvider` | `data class` | Add or edit a provider |

**Note:** `Screen.Login` and `Screen.EditProvider` exist in the sealed interface but are not registered in either NavHost. Authentication is handled automatically via stored credentials.
