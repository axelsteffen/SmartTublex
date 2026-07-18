# Milestone: Immich Integration (SmartTublex)

## Goal

Browse and play Immich media inside the SmartTublex wrapper over the upstream SmartTube APK artifact, using ImmichServiceCore, without patching decompiled upstream classes.

## Starting Point / Current State

| Component | Status |
|-----------|--------|
| Upstream SmartTube APK (apk2maven) | Wired; wrapper Application/Splash |
| `ImmichServiceCore` (interfaces + api) | Phase 0–2 in this milestone |
| Wrapper registries / browse / playback | Phase 3 (browse rows done; settings + playback open) |

## Architecture Principles

1. **Immich code lives in SmartTublex + ImmichServiceCore** — not in MediaServiceCore source
2. **Do not extend `mediaserviceinterfaces` for Immich** — adapters in ImmichServiceCore
3. **Upstream = APK artifact** — customize via inheritance / new classes only
4. **Reuse existing UI/Player** where possible (Phase 3+)
5. **TV resource constraints** — lazy loading, on-demand API calls
6. **Package root (wrapper)** — `de.developerleipzig.smarttublex`; core packages `com.liskovsoft.immich*`

## Module Layout

```text
SmartTublex/
├── apk-base/                 apk2maven → smarttube artifact
├── app/                      wrapper Application / Splash / registries / browse
├── ImmichServiceCore/        immichserviceinterfaces + immichapi
│                             (in-tree for now; intended as git submodule like PlexServiceCore)
├── gradle/immich*.gradle.kts
└── fork-docs/milestones/     this doc
```

## Auth & Content Defaults

| Topic | Choice |
|-------|--------|
| Auth | Server base URL + API key (`x-api-key`) — no PIN/OAuth for TV MVP |
| Server | Single instance URL in SignIn/Prefs (no multi-server discovery) |
| Browse MVP | Albums + video assets (paginated); People/Search/photo viewer later |
| Playback | Direct video playback / original URL; no progress timeline in MVP |

## Implementation Phases

### Phase 0: Foundation

| Step | Description | Status |
|------|-------------|--------|
| 0.1 | Milestone doc (this file) | done |
| 0.2 | Gradle modules `immichserviceinterfaces` / `immichapi` | done |
| 0.3 | `settings.gradle.kts` + `gradle/immich*.gradle.kts` | done |

### Phase 1: Immich API Proof of Concept

| Step | Description | Status |
|------|-------------|--------|
| 1.1 | Interfaces + `ImmichServiceManager` | done |
| 1.2 | Auth validate (`GET /api/users/me`) | done |
| 1.3 | List albums (`GET /api/albums`) | done |
| 1.4 | Album / recent video pages (`POST /api/search/metadata`) | done |
| 1.5 | Resolve stream URL for one video asset | done |
| 1.6 | Integration tests (MockWebServer: sign-in / albums / stream) | done |

### Phase 2: Adapter Layer

| Step | Description | Status |
|------|-------------|--------|
| 2.1 | `ImmichMediaItemAdapter` → MSC `MediaItem` | done |
| 2.2 | `ImmichMediaGroupAdapter` → MSC `MediaGroup` | done |
| 2.3 | `ImmichMediaItemFormatInfo` → ExoPlayer URL formats | done |

**Exit criterion (slice):** `ImmichServiceManager.init` compiles; stream info maps to FormatInfo for a later playback bridge.

### Phase 3: Wrapper UI (sketch — implement after Core MVP)

| Step | Description | Status |
|------|-------------|--------|
| 3a | `MediaSourceRegistry.Source.IMMICH` + `getImmichServiceManager()` | done |
| 3b | `SidebarSectionRegistry.TYPE_IMMICH` (e.g. `101`) | done |
| 3c | Sign-in UI (server URL + API key) — `ImmichSignInPresenter` | done |
| 3d | Browse rows (albums / recent videos) — `ImmichBrowsePresenter` + row mapping | done |
| 3e | Settings entry — `ImmichSettingsInstaller` / Presenter | open |
| 3f | `ImmichPlaybackBridge` (video; ExoPlayer needs `x-api-key` request headers) | open |
| 3g | Init in `SmartTublexApplication.onCreate()` | done |

#### Wrapper touchpoints (mirror Plex)

| Concern | Plex analog | Immich target |
|---------|-------------|---------------|
| Source switch | [`MediaSourceRegistry`](../../app/src/main/kotlin/de/developerleipzig/smarttublex/misc/MediaSourceRegistry.kt) | add `IMMICH` |
| Sidebar ready gate | `SidebarSectionRegistry` | `TYPE_IMMICH` + prefs check |
| Browse | `PlexBrowseInstaller` / `PlexBrowsePresenter` | `ImmichBrowseInstaller` / `ImmichBrowsePresenter` |
| Auth UI | `PlexSignInPresenter` | URL + API key form (no PIN) |
| Settings | `PlexSettingsInstaller` | Immich settings |
| Playback | `PlexPlaybackBridge` | Immich bridge; attach `x-api-key` to media requests |
| Boot | `SmartTublexApplication` | `ImmichServiceManager.init(context)` |

## Immich REST (MVP)

| Operation | Endpoint |
|-----------|----------|
| Validate | `GET /api/users/me` |
| Albums | `GET /api/albums` |
| Assets page | `POST /api/search/metadata` (`albumIds`, `type=VIDEO`, `page`, `size`) |
| Asset | `GET /api/assets/{id}` |
| Playback | `GET /api/assets/{id}/video/playback` (or `/original`) |
| Thumbnail | `GET /api/assets/{id}/thumbnail` |

Base URL form: `{serverUrl}/api/` (trailing slash normalized). Auth header: `x-api-key`.

## Notes

- Immich does not accept API keys as URL query params like Plex `X-Plex-Token`; Phase 3 playback/thumbnail loaders must send the header.
- Do not run `:apk-base:installApkArtifact` in the same Gradle invocation as `:app` (ASM/dex2jar conflict).
- Prefer promoting `ImmichServiceCore/` to a standalone git submodule (same pattern as [PlexServiceCore](https://github.com/axelsteffen/PlexServiceCore)) once the remote exists.
