# SmartTublex Changelog

All notable wrapper-specific changes (relative to the plain upstream SmartTube APK artifact).

## [Unreleased]

### fork-docs

- Added plan, artifact-sync docs, command registry, TV deploy notes, Plex milestone
- Cursor rules/skills adapted for wrapper + apk2maven sync semantics
- `TV_DEPLOY.md`: document wrong-APK ClassNotFoundException symptom (`Process: com.smarttublex`)
- `TV_DEPLOY.md`: document `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED` for stale SplashActivity alias target
- `BRANDING.md`: SmartTublex logo swap (`Beta Version` + `unofficial SmartTube fork`) via `packageWrapperApk`

### build

- Multi-project: `:apk-base` (apk2maven), `:app` (wrapper), `:plexserviceinterfaces`, `:plexapi`
- `packageWrapperApk` patches upstream APK with SmartTublex + Plex DEX and signs debug APK
- `packageWrapperApk` also overwrites `intermediates/apk/debug/app-debug.apk` so Android Studio Run / `installDebug` do not install the AGP stub (`de.developerleipzig.smarttublex`) missing `MainApplication`
- `packageWrapperApk` also rewrites `activity-alias` `android:targetActivity` from `SplashActivity` → `SmartTublexSplashActivity` (avoids `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED`)
- `packageWrapperApk` overwrites upstream mipmap branding from `images/logo/generated/` and sets `app_name` / `browse_title` to `SmartTublex`
- `packageWrapperApk` copies Plex library browse card thumbs from `images/thumbnails/generated/` into `drawable-nodpi` (`all_movies`, `all_tv_shows`)
- `:app` adds `compileOnly` AndroidX AARs (`core` / `activity` / `fragment` / …) so IDE/Kotlin can resolve supertypes of `SplashActivity` / `MainApplication` from the fat JAR
- Plex modules compile against `com.liskovsoft.smarttubetv:smarttube` (no MSC source)

### app

- Package root: `de.developerleipzig.smarttublex` (Gradle group `de.developer-leipzig.smarttublex`)
- `SmartTublexApplication` / `SmartTublexSplashActivity` wrappers
- `MediaSourceRegistry`, `SidebarSectionRegistry`, `PlexPlaybackBridge`
- Phase 3a: `PlexBrowseInstaller` injects Plex sidebar section into upstream `BrowsePresenter`; `PlexSignInPlaceholder` error fragment
- Phase 3b: `PlexSignInPresenter` (PIN via SignInView + singleton inject), `PlexServerSelectionPresenter` (AppDialog); placeholder `onAction` wired
- Phase 3c: `PlexBrowsePresenter.getLibraryRowsObserve` + `mRowMapping` inject; ready section is `TYPE_ROW`
- Phase 3d: `PlexAwareVideoLoaderController` + `PlexPlaybackInstaller` seed Plex format info into upstream YouTube cache before play
- Phase 3.4: `PlexBrowsePresenter.getLibraryGridObserve` / `getChildrenGroupObserve`; `PlexChannelUploadsPresenter` overrides `obtainUploadsObservable` + scroll continue; installed as `ChannelUploadsPresenter.sInstance`

### PlexServiceCore

- Submodule `https://github.com/axelsteffen/PlexServiceCore.git`
- Local compat: omit `getCategory()` on `PlexMediaItemFormatInfo` for upstream JAR
- Discover watchlist: `MediaContainer.librarySectionID` as `String` (fixes Gson `NumberFormatException` on `"watchlist"`)

### Wrapper Touch Points

| Upstream type | SmartTublex class | Notes |
|---------------|-------------------|-------|
| `MainApplication` | `de.developerleipzig.smarttublex.SmartTublexApplication` | Manifest `android:name` |
| `SplashActivity` | `de.developerleipzig.smarttublex.SmartTublexSplashActivity` | Launcher activity |
| `BrowsePresenter` (maps) | `PlexBrowseInstaller` | Reflection into `mSectionsMapping` + `enableSection(TYPE_PLEX)` |
| `SignInPresenter` | `PlexSignInPresenter` | Subclass + `sInstance` inject for PIN UI |
| — | `PlexBrowsePresenter` | Library rows + library grid / children observe for ChannelUploads |
| `ChannelUploadsPresenter` | `PlexChannelUploadsPresenter` | Subclass + `sInstance` inject for Movies/TV grid drill-down |
| — | `PlexServerSelectionPresenter` | AppDialog server list after PIN |
| — | `MediaSourceRegistry` | Source switch / Plex manager access |
| — | `PlexPlaybackBridge` | FormatInfo resolve + YouTube format-cache seed |
| `VideoLoaderController` | `PlexAwareVideoLoaderController` | Installed via `PlexPlaybackInstaller` |
| — | `PlexSignInPlaceholder` | Sidebar error / sign-in / connected states |
