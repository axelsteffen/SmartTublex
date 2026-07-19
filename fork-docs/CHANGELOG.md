# SmartTublex Changelog

All notable wrapper-specific changes (relative to the plain upstream SmartTube APK artifact).

## [Unreleased]

### fork-docs

- Added plan, artifact-sync docs, command registry, TV deploy notes, Plex milestone
- Cursor rules/skills adapted for wrapper + apk2maven sync semantics
- `TV_DEPLOY.md`: document wrong-APK ClassNotFoundException symptom (`Process: com.smarttublex`)
- `TV_DEPLOY.md`: document `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED` for stale SplashActivity alias target
- `scripts/deploy-tv.sh`: one-shot build + `adb install` + start splash (`--ip`, `--no-build`, `--log`)
- `BRANDING.md`: dual masters (`smarttublex.png` / `smarttublex_beta.png`); size via contain-fit (no text overlay); beta → `generated/`
- Immich milestone: Phase 1.6 (MockWebServer IT) + Phase 3a (`MediaSourceRegistry.IMMICH`) marked done in [MILESTONE_IMMICH_INTEGRATION.md](milestones/MILESTONE_IMMICH_INTEGRATION.md)
- [MILESTONE_CONTENT_BROWSE.md](milestones/MILESTONE_CONTENT_BROWSE.md): content menus + series autoplay
- `BRANDING.md`: sidebar icons under `images/icons/generated/`

### build

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
- `packageWrapperApk` copies content sidebar icons from `images/icons/generated/` (`icon_movies`, `icon_tv_shows`, `icon_watchlist`, `icon_photos`, `icon_albums`)
- `images/icons/generate_icons.py`: 301×301 LA filled glyphs (SmartTube `drawable-nodpi` style), not outline sketches
- `:app` adds `compileOnly` AndroidX AARs (`core` / `activity` / `fragment` / …) so IDE/Kotlin can resolve supertypes of `SplashActivity` / `MainApplication` from the fat JAR
- Plex modules compile against `com.liskovsoft.smarttubetv:smarttube` (no MSC source)

### ImmichServiceCore

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
- Immich Phase 3a: `MediaSourceRegistry.Source.IMMICH`, `isImmichEnabled()`, `getImmichServiceManager()`
- Immich Phase 3b/3c: `SidebarSectionRegistry` Immich sections (now Fotos/Alben), `ImmichBrowseInstaller` → `ContentBrowseInstaller`, `ImmichSignInPresenter`
- Immich Phase 3d: `ImmichBrowsePresenter` rows / continue / album grid; `PlexChannelUploadsPresenter` opens Immich album grids
- Immich Phase 3e: `ImmichSettingsPresenter` (sign-in / change credentials / sign-out); `ImmichSettingsInstaller` + shared `SettingsGridInstaller` inject Plex + Immich into upstream settings grid after Accounts
- Immich Phase 3f: `ImmichPlaybackBridge` seeds YouTube format cache; `ImmichAuthHeaderInstaller` OkHttp interceptor (`x-api-key`) + force OkHttp data source; `PlexAwareVideoLoaderController` also prepares Immich
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

### PlexServiceCore

- Submodule `https://github.com/axelsteffen/PlexServiceCore.git`
- Local compat: omit `getCategory()` on `PlexMediaItemFormatInfo` for upstream JAR
- Discover watchlist: `MediaContainer.librarySectionID` as `String` (fixes Gson `NumberFormatException` on `"watchlist"`)
- `PlexMediaItem`: `parentRatingKey` / `grandparentRatingKey` / `index` for next-episode resolve
- `PlexMediaGroupAdapter.fromBrowseCard` + `fromLibraryBrowse(..., displayTitle)` for „Alle Filme/TV-Shows“

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
| — | `PlexPlaybackBridge` / `ImmichPlaybackBridge` | FormatInfo resolve + YouTube format-cache seed |
| — | `PlexNextEpisodeResolver` | Seeds `Video.nextMediaItem` for series autoplay |
| — | `ImmichAuthHeaderInstaller` | OkHttp `x-api-key` interceptor + force OkHttp while Immich signed in |
| `VideoLoaderController` | `PlexAwareVideoLoaderController` | Installed via `PlexPlaybackInstaller` (Plex + Immich + next episode) |
| — | `PlexSignInPlaceholder` | Sidebar error / sign-in / connected states |
