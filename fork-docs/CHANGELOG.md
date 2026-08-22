# SmartTublex Changelog

All notable wrapper-specific changes (relative to the plain upstream SmartTube APK artifact).

## [Unreleased]

### fork-docs

- `.cursor/rules/changelog-fork.mdc`: changelog must be updated in the same turn as wrapper-specific changes (not deferred to commit / `log change`)
- Added plan, artifact-sync docs, command registry, TV deploy notes, Plex milestone
- Cursor rules/skills adapted for wrapper + apk2maven sync semantics
- `TV_DEPLOY.md`: document wrong-APK ClassNotFoundException symptom (`Process: com.smarttublex`)
- `TV_DEPLOY.md`: document `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED` for stale SplashActivity alias target
- `scripts/deploy-tv.sh`: one-shot build + `adb install` + start splash (`--ip`, `--no-build`, `--log`)
- `BRANDING.md`: dual masters (`smarttublex.png` / `smarttublex_beta.png`); size via contain-fit (no text overlay); beta → `generated/`
- Immich milestone: Phase 1.6 (MockWebServer IT) + Phase 3a (`MediaSourceRegistry.IMMICH`) marked done in [MILESTONE_IMMICH_INTEGRATION.md](milestones/MILESTONE_IMMICH_INTEGRATION.md)
- [MILESTONE_CONTENT_BROWSE.md](milestones/MILESTONE_CONTENT_BROWSE.md): content menus + series autoplay
- `BRANDING.md`: sidebar icons under `images/icons/generated/`
- [MILESTONE_PLEX_SEARCH.md](milestones/MILESTONE_PLEX_SEARCH.md): dedicated Plex search screen for Filme/TV-Shows

### build

- Refreshed SmartTube base artifact (`com.liskovsoft.smarttubetv:smarttube:latest`, 2026-08-22): APK 34,915,088 bytes / JAR 29,224,667 bytes
- Multi-project: `:apk-base` (apk2maven), `:app` (wrapper), `:plexserviceinterfaces`, `:plexapi`, `:immichserviceinterfaces`, `:immichapi`
- `packageWrapperApk`: resolve `apktool` via absolute path (`/opt/homebrew/bin`, `APKTOOL`, …) so Gradle daemon PATH misses do not fail assemble
- `packageWrapperApk`: also merge `compileDebugJavaWithJavac` output (e.g. `SidebarServiceBridge`) into wrapper DEX — Kotlin-only pack caused runtime `NoClassDefFoundError`
- `ImmichServiceCore/` (URL + API-key auth, albums/videos, MSC adapters); Gradle via `gradle/immich*.gradle.kts`
- Milestone: [MILESTONE_IMMICH_INTEGRATION.md](milestones/MILESTONE_IMMICH_INTEGRATION.md)
- AGP bumped to `8.13.2` (application + library plugins in `settings.gradle.kts`)
- `:immichapi` test deps: MockWebServer + SmartTube artifact; `unitTests.returnDefaultValues = true`
- `packageWrapperApk` patches upstream APK with SmartTublex + Plex DEX and signs debug APK
- `packageWrapperApk` also overwrites `intermediates/apk/debug/app-debug.apk` so Android Studio Run / `installDebug` do not install the AGP stub (`de.developerleipzig.smarttublex`) missing `MainApplication`
- `packageWrapperApk` also rewrites `activity-alias` `android:targetActivity` from `SplashActivity` → `SmartTublexSplashActivity` (avoids `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED`)
- `packageWrapperApk` overwrites upstream mipmap branding from `images/logo/generated/` and sets `app_name` / `browse_title` to `SmartTublex`
- `packageWrapperApk` copies Plex library browse card thumbs from `images/thumbnails/generated/` into `drawable-nodpi` (`all_movies`, `all_tv_shows`)
- `images/thumbnails/`: refreshed "Alle Filme" / "Alle TV-Shows" card masters (`all_movies.png`, `all_TV-Shows.png`); old masters moved to `images/thumbnails/archive/`; regenerated `generated/` via `generate_thumbnails.py`
- `images/thumbnails/`: added dedicated "Suchen" entry-card masters (`all_movies_search.png`, `all_TV-Shows_search.png`); `generate_thumbnails.py` MAPPING + `packageWrapperApk` now also produce/copy `all_movies_search.png` / `all_tv_shows_search.png`
- [PlexMediaItemAdapter.java](../PlexServiceCore/plexapi/src/main/java/de/developerleipzig/plexapi/adapter/PlexMediaItemAdapter.java): "Suchen" entry cards (`fromSearchEntry`) now use `DRAWABLE_SEARCH_MOVIES` / `DRAWABLE_SEARCH_TV_SHOWS` instead of sharing the "Alle Filme" / "Alle TV-Shows" library-browse drawables
- `packageWrapperApk` copies content sidebar icons from `images/icons/generated/` (`icon_movies`, `icon_tv_shows`, `icon_watchlist`, `icon_photos`, `icon_albums`)
- `images/icons/generate_icons.py`: 301×301 LA filled glyphs (SmartTube `drawable-nodpi` style), not outline sketches
- `:app` adds `compileOnly` AndroidX AARs (`core` / `activity` / `fragment` / …) so IDE/Kotlin can resolve supertypes of `SplashActivity` / `MainApplication` from the fat JAR
- Plex modules compile against `com.liskovsoft.smarttubetv:smarttube` (no MSC source)

### ImmichServiceCore

- `ImmichMediaFormat`: implement upstream `MediaFormat.getAudioTrackId()` (returns `null` for Direct Play URLs)
- Phase 1.6: MockWebServer service tests (`ImmichSignInServiceImplTest`, `ImmichLibraryServiceImplTest`, `ImmichMediaServiceImplTest`)
- `ImmichPrefs.createInMemory()` for JVM unit tests (no Robolectric; avoids SmartTube ASM clash)
- README: `./gradlew :immichapi:testDebugUnitTest`
- `ImmichStreamInfo.hasEncodedVideo()` + `ImmichPlaybackCompat` TV Direct-Play heuristics
- Direct Play gate: refuse only on positive HEVC/AV1/VP9 sniff; UNKNOWN / probe failure fails open (fixes late-`moov` H.264 blocked by preflight)
- `getPhotoYearsObserve` / `getAssetsForYearPageObserve` (timeline YEAR buckets + `takenAfter`/`takenBefore` search)
- `ImmichMediaGroupAdapter`: `YEAR_ROW` / `ALBUMS_LIST` kinds for Fotos / Alben menus

### app

- Branding: `generate_branding.py` sizes full logos (`--variant beta|release|both`); `packageWrapperApk` still uses `images/logo/generated/` (beta)
- Package root: `de.developerleipzig.smarttublex` (Gradle group `de.developer-leipzig.smarttublex`)
- `SmartTublexApplication` / `SmartTublexSplashActivity` wrappers
- `MediaSourceRegistry`, `SidebarSectionRegistry`, `PlexPlaybackBridge`
- Content browse: replace single Plex/Immich sidebar with Filme / TV-Shows / Merkliste / Fotos / Alben (`ContentBrowseInstaller`, section ids 100–104, `iconUrl` drawables)
- `PlexBrowsePresenter`: `getMoviesRowsObserve` / `getShowsRowsObserve` / `getWatchlistRowsObserve` (Continue Watching → Recently Added → Alle-… card; watchlist movies+shows by year)
- `ImmichBrowsePresenter`: `getPhotosRowsObserve` (year rows) / `getAlbumsRowsObserve` (album cards)
- Series autoplay: `PlexNextEpisodeResolver` seeds `Video.nextMediaItem` from season group or PMS children (incl. next season)
- Fix series autoplay from Continue Watching: only use group siblings for season containers; `loadNext` prefers `nextMediaItem` over shelf neighbors
- Fix series autoplay: sync next-episode resolve; `loadNext` never falls back to CW/Playlist neighbors for episodes; `getItemObserve` metadata refresh
- Immich Phase 3a: `MediaSourceRegistry.Source.IMMICH`, `isImmichEnabled()`, `getImmichServiceManager()`
- Immich Phase 3b/3c: `SidebarSectionRegistry` Immich sections (now Fotos/Alben), `ImmichBrowseInstaller` → `ContentBrowseInstaller`, `ImmichSignInPresenter`
- Immich Phase 3d: `ImmichBrowsePresenter` rows / continue / album grid; `PlexChannelUploadsPresenter` opens Immich album grids
- Immich Phase 3e: `ImmichSettingsPresenter` (sign-in / change credentials / sign-out); `ImmichSettingsInstaller` + shared `SettingsGridInstaller` inject Plex + Immich into upstream settings grid after Accounts
- Immich Phase 3f: `ImmichPlaybackBridge` seeds YouTube format cache; `ImmichAuthHeaderInstaller` OkHttp interceptor (`x-api-key`) + force OkHttp data source; `PlexAwareVideoLoaderController` also prepares Immich
- Immich Image Viewer: `ImmichImageViewerActivity` shows stills fullscreen (OkHttp + sampled decode); `PlexAwareVideoLoaderController` routes `!isVideo` there and finishes `PlaybackActivity`; `packageWrapperApk` injects the activity into the upstream manifest; Exo seed refused for images
- Fix Immich Image Viewer EXIF orientation: apply `ExifInterface` rotation/flip after sampled decode of `/original` (thumbnails already oriented by Immich)
- Fix Immich Image Viewer launch: start from resumed `PlaybackActivity` (not Application+finish race); log `ActivityNotFoundException`; finish Playback only after viewer `onCreate`
- Fix Immich Image Viewer wrong asset: `singleTop` + `CLEAR_TOP` + `onNewIntent` so a leftover viewer under Playback does not keep the previous photo
- Fix Immich Image Viewer buried under Playback: always `NEW_TASK` from app context + suppress/finish `PlaybackActivity` until viewer resumes
- Browse soft-fails: `InterruptedIOException` from cancelled section loads logged at Debug (`BrowseLoadErrors`), not Error
- Fix Immich photo click: stills use `hasUploads` + null `videoId` → `ChannelUploadsPresenter.openChannel` → image viewer (never `PlaybackPresenter`); avoids black first video after a photo
- Fix Immich still listing: `getParams()` returns unique `immich_still:{assetId}` so `Video.isEmpty()` does not drop cards and `VideoGroup` does not collapse all stills as duplicates
- Fix Browse flicker on resume: `ContentBrowseInstaller` only calls `updateSections` when sidebar pin order actually changed
- Fix Immich sidebar Sign-in: pass BrowseActivity context into `SimpleEditDialog` (was `applicationContext` → `token null`)
- Fix Immich validate: `validateObserve()` uses `RxHelper.fromCallable` (was main-thread network → `NetworkOnMainThreadException`)
- Fix Immich media auth: thumbnail/playback URLs include `?apiKey=` (Glide/Exo need no header); stop clearing Exo factory on every prepare
- Immich playback: detect `encodedVideoPath`; pending one-shot Exo factory rebuild after OkHttp force; warn when TV plays unencoded originals
- Immich playback preflight: refuse TV-hostile originals (MOV/HEVC/AV1/…) without encode; seed log includes `encoded=`; skip player open on seed failure
- Immich playback: require `encodedVideoPath` for all TV Direct Play (MIME `video/mp4` alone still failed with Exo `Unexpected playback error null`)
- Immich Direct Play: H.264 only on TV (2015 Bravia — HEVC caused `Unexpected playback error null`); AV1/VP9/HEVC wait for Immich H.264 encode; no Exo factory clear during prepare
- Phase 3a: `PlexBrowseInstaller` injects Plex sidebar section into upstream `BrowsePresenter`; `PlexSignInPlaceholder` error fragment
- `PlexBrowseInstaller`: pin `TYPE_PLEX` directly under Startseite (`TYPE_HOME`), not at sidebar end
- Phase 3b: `PlexSignInPresenter` (PIN via SignInView + singleton inject), `PlexServerSelectionPresenter` (AppDialog); placeholder `onAction` wired
- Phase 3c: `PlexBrowsePresenter.getLibraryRowsObserve` + `mRowMapping` inject; ready section is `TYPE_ROW`
- Phase 3d: `PlexAwareVideoLoaderController` + `PlexPlaybackInstaller` seed Plex format info into upstream YouTube cache before play
- Phase 3.4: `PlexBrowsePresenter.getLibraryGridObserve` / `getChildrenGroupObserve`; `PlexChannelUploadsPresenter` overrides `obtainUploadsObservable` + scroll continue; installed as `ChannelUploadsPresenter.sInstance`
- Phase 3.5: `PlexSettingsPresenter` (sign-in / server pick / sign-out); settings inject via shared `SettingsGridInstaller` (also Immich)
- Fix Plex watch history: `PlexProgressController` + `PlexPlaybackBridge.updateProgress` report `/:/timeline` (tickle/pause/end/seek/release) so Continue Watching survives restart
- Plex search: dedicated `PlexSearchActivity` (Filme + TV-Shows result rows), opened via a "Suchen" card placed right next to the existing "Alle Filme"/"Alle TV-Shows" card in the same row; NOT a `SearchPresenter` override — that upstream presenter has a private constructor and cannot be subclassed. `PlexBrowsePresenter.getMovieSearchRowObserve` / `getShowSearchRowObserve` merge/dedupe across libraries (same `MERGE_PAGE_CAP` pattern as Continue Watching); continuation reuses `continueGroupObserve`; single-library setups only get real paging (documented limitation)
- `PlexChannelUploadsPresenter.openChannel`: new branch (checked before the generic library-browse-stub branch) recognizes the "Suchen" marker and opens `PlexSearchActivity` instead of a library grid

### PlexServiceCore

- `PlexMediaFormat`: implement upstream `MediaFormat.getAudioTrackId()` (returns `null` for Direct Play URLs)
- Submodule `https://github.com/axelsteffen/PlexServiceCore.git`
- Local compat: omit `getCategory()` on `PlexMediaItemFormatInfo` for upstream JAR
- Discover watchlist: `MediaContainer.librarySectionID` as `String` (fixes Gson `NumberFormatException` on `"watchlist"`)
- `PlexMediaItem`: `parentRatingKey` / `grandparentRatingKey` / `index` for next-episode resolve
- `PlexMediaItem`: `grandparentTitle` / `parentTitle` / `parentIndex`; episode/season cards show show name + `SxxExx` subtitle
- `PlexLibraryService.getItemObserve(ratingKey)` for metadata refresh (next-episode parent keys)
- `PlexMediaGroupAdapter.fromBrowseCard` + `fromLibraryBrowse(..., displayTitle)` for „Alle Filme/TV-Shows“
- Plex search: `PlexPmsApi.getSectionItems` gains a `title` query param (nullable, omitted for browse calls); `PlexLibraryService.getSearchPageObserve(library, type, query, offset)` reuses the existing container-paging plumbing (`fetchSectionPage`, `mapMetadata`, `resolveTotalSize`)
- `PlexMediaGroupAdapter.Kind.SEARCH` + `fromSearch(...)`; `continueFrom` now also propagates `searchType`/`searchQuery` so continuation keeps the same query
- `PlexMediaItemAdapter.fromSearchEntry` — "Suchen" stub card with a marker `reloadPageKey` (`SEARCH_ENTRY_MOVIE`/`SEARCH_ENTRY_SHOW`) that never collides with a real PMS section key
- `PlexMediaGroupAdapter.fromBrowseCard` gains a 4-arg overload that puts the "Alle Filme"/"Alle TV-Shows" browse stub and the "Suchen" stub in the same row, side by side; the original 3-arg overload is unchanged (browse stub only)

### Wrapper Touch Points

| Upstream type | SmartTublex class | Notes |
|---------------|-------------------|-------|
| `MainApplication` | `de.developerleipzig.smarttublex.SmartTublexApplication` | Manifest `android:name` |
| `SplashActivity` | `de.developerleipzig.smarttublex.SmartTublexSplashActivity` | Launcher activity |
| `BrowsePresenter` (maps) | `ContentBrowseInstaller` (`PlexBrowseInstaller` / `ImmichBrowseInstaller` façades) | Five content sections: Filme / TV-Shows / Merkliste / Fotos / Alben |
| `SignInPresenter` | `PlexSignInPresenter` / `ImmichSignInPresenter` | Subclass + `sInstance` inject (PIN / URL+API key) |
| — | `PlexBrowsePresenter` / `ImmichBrowsePresenter` | Section-specific rows + grid observe for ChannelUploads |
| `ChannelUploadsPresenter` | `PlexChannelUploadsPresenter` | Subclass + `sInstance` inject for Plex + Immich grid drill-down |
| — | `PlexServerSelectionPresenter` | AppDialog server list after PIN |
| — | `PlexSettingsPresenter` / `ImmichSettingsPresenter` | Settings dialogs (sign-in / sign-out) |
| — | `SettingsGridInstaller` (`PlexSettingsInstaller` / `ImmichSettingsInstaller`) | Shared inject into `mSettingsGridMapping` |
| — | `MediaSourceRegistry` | Source switch / Plex + Immich manager access |
| — | `PlexPlaybackBridge` / `ImmichPlaybackBridge` | FormatInfo resolve + YouTube format-cache seed + PMS progress |
| — | `ImmichImageViewerActivity` | Fullscreen Immich stills (injected via `packageWrapperApk`) |
| — | `PlexNextEpisodeResolver` | Seeds `Video.nextMediaItem` for series autoplay |
| — | `ImmichAuthHeaderInstaller` | OkHttp `x-api-key` interceptor + force OkHttp while Immich signed in |
| `VideoLoaderController` | `PlexAwareVideoLoaderController` | Installed via `PlexPlaybackInstaller` (Plex + Immich + next episode + image viewer) |
| `PlaybackPresenter` listeners | `PlexProgressController` | Installed via `PlexPlaybackInstaller`; PMS timeline / history |
| — | `PlexSignInPlaceholder` | Sidebar error / sign-in / connected states |
| — | `PlexSearchActivity` | Dedicated Plex search screen (Filme + TV-Shows), injected via `packageWrapperApk`; click routed by `PlexChannelUploadsPresenter.openChannel` |
