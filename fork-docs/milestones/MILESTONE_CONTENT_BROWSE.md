# Milestone: Content Browse + Series Autoplay

## Goal

Replace the single Plex / Immich sidebar entries with five content-type menus (Filme, TV-Shows, Merkliste, Fotos, Alben), and make Plex TV episodes continue into the next episode via SmartTube’s existing `nextMediaItem` playback chain.

## Starting Point / Current State

| Component | Status |
|-----------|--------|
| Plex browse (single sidebar, mixed movie/TV rows) | superseded |
| Immich browse (Recent Videos + album rows) | superseded |
| Plex playback via format-cache seed | done |
| Next-episode autoplay | done |
| Content-type sidebar split | done |

## Architecture Principles

1. **Wrapper-only** — no upstream bytecode patches; reuse `BrowsePresenter` injection and `PlexAwareVideoLoaderController`
2. **TV resources** — lazy rows, one-ahead `nextMediaItem` (no full-season play queues)
3. **Sign-in gates** — Filme / TV-Shows / Merkliste → Plex; Fotos / Alben → Immich
4. **Icons** — `android.resource://` URLs + `packageWrapperApk` copy (same as library card thumbs)

## Implementation Steps

| Step | Description | Status |
|------|-------------|--------|
| 0 | Milestone doc (this file) | done |
| A | `PlexNextEpisodeResolver` + `parentRatingKey` / index on domain + seed `Video.nextMediaItem` | done |
| B1 | Five sidebar section IDs + installer + pin order | done |
| B2a | Filme / TV-Shows rows (Continue Watching, Recently Added, Alle-… card) | done |
| B2b | Merkliste (movies + shows, sort by year) | done |
| B2c | Fotos (year categories) + Alben (album cards) | done |
| B3 | Sidebar icons + packaging | done |
| C | CHANGELOG + graphify update | done |

## Dependencies

- A independent of B (can ship first)
- B2a–B2c depend on B1
- B3 can parallel B2 once section titles exist

## Affected Files (expected)

- `app/.../SidebarSectionRegistry.kt`, `ContentBrowseInstaller`, presenters
- `app/.../PlexAwareVideoLoaderController.kt`, `PlexNextEpisodeResolver`
- `PlexServiceCore` — `PlexMediaItem` / metadata mapping
- `ImmichServiceCore` — year / photo search APIs
- `app/build.gradle.kts` — icon copy in `packageWrapperApk`
- `fork-docs/CHANGELOG.md`, this milestone

## TV Notes

- Watchlist year-sort applies to the loaded merge window (cap), not a full remote sort
- Photo years emit empty years skipped; assets paginated per year
