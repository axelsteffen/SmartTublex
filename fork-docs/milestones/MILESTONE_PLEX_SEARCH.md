# Milestone: Plex Search (Filme + TV-Shows)

## Goal

Let the user search Plex movies and TV shows by title, via a dedicated,
wrapper-owned search screen — not the upstream YouTube search.

## Starting Point / Current State

| Component | Status |
|-----------|--------|
| Plex browse rows (Continue Watching / Recently Added / Alle-… card) | done ([MILESTONE_CONTENT_BROWSE.md](MILESTONE_CONTENT_BROWSE.md)) |
| Plex text search (title filter over movies/shows) | done |
| Immich (Fotos/Alben) text search | not started — deferred, see below |

## Architecture Principles

1. **Wrapper-only, no smali patch** — upstream `SearchPresenter` has a `private`
   constructor (verified via `javap` against the resolved
   `com.liskovsoft.smarttubetv:smarttube:latest` artifact, not just the source
   checkout) and cannot be subclassed the way `PlexChannelUploadsPresenter`
   subclasses `ChannelUploadsPresenter`. `SearchTagsFragment`/`SearchTagsActivity`
   are subclassable but instantiated hardcoded upstream — no safe injection point
   without smali patching, which [fork-upstream-minimal.mdc](../../.cursor/rules/fork-upstream-minimal.mdc)
   forbids. Enriching the existing YouTube search screen was therefore ruled out.
2. **Dedicated screen instead** — `PlexSearchActivity`, a plain wrapper `Activity`
   (same pattern as `ImmichImageViewerActivity`), hosting upstream's own
   `RowsSupportFragment` + `VideoCardPresenter` for visual consistency and
   `VideoActionPresenter.apply(Video)` for click routing (movie → play, show →
   seasons grid via the already-installed `PlexChannelUploadsPresenter`).
3. **Reuse the tested paging path** — search calls the same
   `library/sections/{id}/all` endpoint as browse (now with an optional `title`
   query param), not PMS's `/hubs/search` or `/library/sections/{key}/search`
   (both real PMS endpoints, but neither documents `X-Plex-Container-Start/Size`
   paging — only `limit`). Real `onScrollEnd`-style continuation matters more
   here than server-side relevance ranking.
4. **TV resources** — merge/dedupe across libraries follows the same
   `MERGE_PAGE_CAP = 50` / sequential-fetch pattern already used for Continue
   Watching and Recently Added; no parallel fan-out.

## Implementation Steps

| Step | Description | Status |
|------|-------------|--------|
| A | `PlexPmsApi.getSectionItems` gains nullable `title` param; `PlexLibraryService.getSearchPageObserve` + impl (reuses `fetchSectionPage`/`mapMetadata`/`resolveTotalSize`) | done |
| B | `PlexMediaGroupAdapter.Kind.SEARCH` + `fromSearch`; `continueFrom` propagates `searchType`/`searchQuery`; `PlexMediaItemAdapter.fromSearchEntry` marker stub (`SEARCH_ENTRY_MOVIE`/`SEARCH_ENTRY_SHOW`); `fromBrowseCard` 4-arg overload puts the "Suchen" stub in the same row as the "Alle Filme"/"Alle TV-Shows" browse stub | done |
| C | `PlexBrowsePresenter`: `getMovieSearchRowObserve` / `getShowSearchRowObserve` (merge+dedupe across libraries); "Suchen" card placed next to the "Alle Filme"/"Alle TV-Shows" card (same row, via the 4-arg `fromBrowseCard`); `fetchContinueGroup` extended for search continuation | done |
| D | `PlexSearchActivity` (new): input field + two `RowsSupportFragment` rows (Filme/TV-Shows), "Mehr laden" trailing card for continuation, `VideoActionPresenter.apply` click routing | done |
| E | `PlexChannelUploadsPresenter.openChannel`: new branch (checked before the generic library-browse-stub branch) recognizes the "Suchen" marker and opens `PlexSearchActivity` | done |
| F | `packageWrapperApk`: inject `PlexSearchActivity` into the decoded manifest (no explicit theme — inherits the app-level Leanback browse theme, same as `BrowseActivity`/`ChannelUploadsActivity`) | done |
| G | Tests: MockWebServer (`title=`/`type=` query, container headers, browse calls stay title-less) + adapter (`fromSearch`, `continueFrom` field propagation, search-entry marker) | done |
| H | CHANGELOG + this milestone doc + `openapi-plex-pms-in-use.yaml` + `graphify update .` | done |

## Known Limitations

- **Multi-library setups**: only exactly-one-movie-library / exactly-one-show-library
  configurations get real `onScrollEnd`-style continuation on search rows (same
  trade-off already accepted for Continue Watching / Recently Added via
  `buildMergedShelf`). With 2+ libraries of the same type, the search row stays
  first-page-only.
- **`title=` filter semantics** (substring vs. prefix, case sensitivity) are not
  documented upstream — verify empirically against a real PMS. If it behaves
  poorly, `library/sections/{key}/search?query=` is a documented fallback (loses
  container paging).
- **No episode-level hits** — the section title filter is expected to match at
  movie/show level only; deep episode search is a possible future enhancement.
- **Row ordering inside the search screen is deterministic** (Filme row above
  TV-Shows row, unlike the race condition that would occur if results were
  merged into upstream's own search screen) since each row is independently
  populated and never reordered.
- **Immich (Fotos/Alben) search — deferred.** `ImmichApi.searchMetadata`
  (`POST search/metadata`, `MetadataSearchDto`) has no free-text query field —
  it filters by album/type/date only. Immich's own `/search/smart` is unwired.
  Wiring it would need a new endpoint + DTOs + service method + adapter `Kind`
  + a third row in `PlexSearchActivity` (or a parallel `ImmichSearchActivity`) —
  a separate follow-up milestone.

## Affected Files

- `PlexServiceCore/plexapi/src/main/java/de/developerleipzig/plexapi/network/PlexPmsApi.java`
- `PlexServiceCore/plexserviceinterfaces/src/main/java/de/developerleipzig/plexserviceinterfaces/PlexLibraryService.java`
- `PlexServiceCore/plexapi/src/main/java/de/developerleipzig/plexapi/service/PlexLibraryServiceImpl.java`
- `PlexServiceCore/plexapi/src/main/java/de/developerleipzig/plexapi/adapter/PlexMediaGroupAdapter.java`
- `PlexServiceCore/plexapi/src/main/java/de/developerleipzig/plexapi/adapter/PlexMediaItemAdapter.java`
- `app/src/main/kotlin/de/developerleipzig/smarttublex/PlexSearchActivity.kt` (new)
- `app/src/main/kotlin/de/developerleipzig/smarttublex/presenters/PlexBrowsePresenter.kt`
- `app/src/main/kotlin/de/developerleipzig/smarttublex/presenters/PlexChannelUploadsPresenter.kt`
- `app/build.gradle.kts` (`packageWrapperApk`)
- `PlexServiceCore/plexapi/openapi-plex-pms-in-use.yaml`
- Tests: `PlexLibraryServiceImplTest.java`, `PlexMediaGroupAdapterTest.java`, `PlexMediaItemAdapterTest.java`

## Verification

- `./gradlew :plexserviceinterfaces:compileDebugJavaWithJavac :plexapi:testDebugUnitTest` — green
- `./gradlew :app:compileDebugKotlin` — green (compiles against the real resolved
  `smarttube-latest.jar`, validating every upstream API used by `PlexSearchActivity`)
- Manual (per [TV_DEPLOY.md](../TV_DEPLOY.md)): deploy debug APK, open "Suchen" from
  Filme and from TV-Shows, type a title matching both a movie and a show, confirm
  both rows populate; use "Mehr laden" on a shelf with a next page; click a movie
  hit → plays; click a show hit → opens seasons grid; sign out of Plex and search
  again → no crash / no error dialog (soft-fail); confirm the upstream YouTube
  search screen and its filters are completely unaffected.
