# Fijerena Documentation

The documents in this folder describe how the apps work today. The project's front page is the
[README](../README.md). The rules for changes are in [CONTRIBUTING.md](../CONTRIBUTING.md), and
what changed in each version is in [RELEASE_NOTES.md](../RELEASE_NOTES.md). To report a security
problem, see [SECURITY.md](../SECURITY.md). Everyone who takes part follows the
[Code of Conduct](../CODE_OF_CONDUCT.md).

## For Users

| Document | Contents |
|----------|----------|
| [USER_GUIDE.md](USER_GUIDE.md) | How to use the apps, in plain words |
| [FEATURES.md](FEATURES.md) | Every feature, setting and theme in full detail, with API details |

## For Developers

| Document | Contents |
|----------|----------|
| [DEVELOPING.md](DEVELOPING.md) | Build, install, checks, CI, tech stack, project structure, known issues |
| [RUN_GUIDE.md](RUN_GUIDE.md) | Build, install, deploy, backup/restore, debugging, focus walks and releasing for TV and phone |
| [DESIGN.md](DESIGN.md) | System design: module graph, domain model, player system, EPG architecture, theme system, screen inventory |
| [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md) | Type-safe navigation, screen definitions, flow diagrams, TV focus handling |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | All Room databases and SharedPreferences. **Update it in the same commit as any migration** (CONTRIBUTING.md → Schema Changes) |
| [EPG_GUIDE.md](EPG_GUIDE.md) | EPG pipeline: ingestion, indexer, workers, TV Guide grid, Search the guide, settings, file inventory |
| [EPG_INDEX_STORAGE.md](EPG_INDEX_STORAGE.md) | Why the EPG index grew to 87% dead space, the PRAGMA/Requery traps behind it, and how to read DB state from a file header |
| [plans/](plans/README.md) | Open plans for multi-phase work (CONTRIBUTING.md → Plans) |
| [server/README.md](../server/README.md) | Live sync server: develop, deploy, self-host, API |
