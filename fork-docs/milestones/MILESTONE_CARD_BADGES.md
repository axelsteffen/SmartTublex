# Milestone: Card Badges (Plex + Immich)

## Goal

Plex and Immich cards get the same badge overlay YouTube cards already have:
playable videos show their length, container cards a child count. Before this
milestone only YouTube items had a badge — Plex movies/episodes and Immich videos
rendered without any length hint.

## Starting Point / Current State

| Component | Status |
|-----------|--------|
| YouTube cards (length / playlist count badge) | upstream, unchanged |
| Plex movie / episode length badge | done |
| Plex show / season count badge | done |
| Immich video length badge | done |
| Immich album count badge | done |
| Immich still images (no badge) | done |

## How the badge works upstream

- `VideoCardPresenter` → `ComplexImageCardView.setBadgeText(video.badge)`
- `Video.from(MediaItem)` → `video.badge = item.getBadgeText()`, unconditionally
  for every item type
- YouTube source of that text (verified via `javap` against the resolved
  `com.liskovsoft.smarttubetv:smarttube:latest` artifact): videos use the
  thumbnail time-status overlay ("12:34"), playlists use `videoCountText`
  ("50 Videos")

Both fork adapters returned `null` from `getBadgeText()`, while the duration was
already mapped (`PlexMediaItem.getDurationMs()`, `ImmichAsset.getDurationMs()`).

## Architecture Principles

1. **Adapter level, no upstream patch** — implemented in
   `PlexMediaItemAdapter` / `ImmichMediaItemAdapter`; no app-side presenter
   override needed, since `Video.from(MediaItem)` already forwards the value.
2. **Duplicated formatter, intentionally** — `:plexapi` and `:immichapi` share no
   fork-owned module; a new shared module for ~12 lines would be
   disproportionate. Each adapter keeps a private `formatDuration(long)`.
3. **German literals** — library modules have no access to app resources, same
   constraint that already produced hardcoded `"Suchen"` / `"Alle Filme"` in
   `PlexMediaItemAdapter`.
4. **`Video.isMix()` side effect accepted** — a badge containing letters plus
   `durationMs == 0` plus `hasPlaylist()` makes upstream `isMix()` true. Upstream
   YouTube playlist cards hit the same path, and `isMix()` is only read by
   `Video.equals()` (deterministic, evaluated on both sides) and
   `isSectionPlaylistEnabled()` (playback queue — container cards are never
   played). Numeric duration badges are unaffected: `Helpers.hasWords` requires a
   letter.
5. **Graceful degradation** — missing or zero values yield no badge instead of
   "0:00" / "0 Folgen".

## Badge Assignment

| Card | Badge | Source |
|------|-------|--------|
| Plex movie / episode | `1:52:30` / `12:34` | `PlexMediaItem.getDurationMs()` |
| Plex show | `3 Staffeln` (fallback `62 Folgen`) | `childCount`, else `leafCount` |
| Plex season | `7 Folgen` | `leafCount` |
| Plex stub cards ("Alle Filme", "Suchen") | none | — |
| Immich video | `1:30:00` / `12:34` | `ImmichAsset.getDurationMs()` |
| Immich album | `24 Medien` / `1 Medium` | `ImmichAlbum.getAssetCount()` |
| Immich still image | none | — |

## Implementation Steps

| Step | Description | Status |
|------|-------------|--------|
| A | `PlexMediaItemAdapter.getBadgeText()` + private `formatDuration` (YouTube format, seconds truncated) | done |
| B | PMS counts plumbed through: `PlexMetadata.childCount/leafCount` → `PlexMediaItem` → `PlexMediaItemImpl` (widest ctor +2 `int`, mapped in `fromMetadata`) → container count badge | done |
| C | `ImmichMediaItemAdapter.getBadgeText()` (video length, album count, stills `null`) | done |
| D | Tests: `PlexMediaItemAdapterTest` badge cases, `MediaContainerResponseTest` count parsing, new `ImmichMediaItemAdapterTest` | done |
| E | CHANGELOG + this milestone doc + `openapi-plex-pms-in-use.yaml` + `graphify update .` | done |

## Dependencies

- A independent of B; the show/season badge stays `null` until B lands
- C independent of A/B (no data plumbing needed on the Immich side)

## Affected Files

- `PlexServiceCore/plexapi/src/main/java/de/developerleipzig/plexapi/adapter/PlexMediaItemAdapter.java`
- `PlexServiceCore/plexapi/src/main/java/de/developerleipzig/plexapi/network/dto/PlexMetadata.java`
- `PlexServiceCore/plexapi/src/main/java/de/developerleipzig/plexapi/library/PlexMediaItemImpl.java`
- `PlexServiceCore/plexserviceinterfaces/src/main/java/de/developerleipzig/plexserviceinterfaces/data/PlexMediaItem.java`
- `ImmichServiceCore/immichapi/src/main/java/de/developerleipzig/immichapi/adapter/ImmichMediaItemAdapter.java`
- `PlexServiceCore/plexapi/openapi-plex-pms-in-use.yaml`
- Tests: `PlexMediaItemAdapterTest.java`, `MediaContainerResponseTest.java`,
  `ImmichMediaItemAdapterTest.java` (new)

## TV Notes

- No extra requests: both durations and both counts ride along on responses the
  browse rows already fetch (`library/sections/{id}/all`, `albums`,
  `search/metadata`)
- No new caches or data structures; the badge is derived per card on demand

## Known Limitations

- PMS omits `childCount` on some show entries; the badge then falls back to the
  episode count, and to no badge if both are missing
- Immich albums mix photos and videos, so the neutral wording "Medien" is used
  instead of "Fotos" / "Videos"
- Album cards now show the count twice (badge plus `secondTitle`); dropping it
  from `secondTitle` is a possible follow-up

## Verification

- `./gradlew :plexapi:testDebugUnitTest :immichapi:testDebugUnitTest` — green
  (43 tests across the three touched classes)
- Manual (per [TV_DEPLOY.md](../TV_DEPLOY.md)): deploy debug APK, open Filme /
  TV-Shows / Fotos / Alben, confirm movie and episode cards show a length,
  show/season cards a count, photo cards nothing, and that YouTube cards are
  unchanged
