# Milestone: Plex Integration (SmartTublex)

## Goal

Play Plex media inside the SmartTublex wrapper over the upstream SmartTube APK artifact, using [PlexServiceCore](https://github.com/axelsteffen/PlexServiceCore), without patching decompiled upstream classes.

## Starting Point / Current State

| Component | Status |
|-----------|--------|
| Upstream SmartTube APK (apk2maven) | Wired; wrapper Application/Splash |
| `PlexServiceCore` submodule | Included; Gradle via `gradle/plex*.gradle.kts` against SmartTube JAR |
| Wrapper registries / playback bridge | Phase 0–2 slice in `de.developerleipzig.smarttublex.misc` |
| Browse UI / PlaybackPresenter hooks | Phase 3a–3d done (playback via format-cache seed) |

## Architecture Principles

1. **Plex code lives in SmartTublex + PlexServiceCore** — not in MediaServiceCore source
2. **Do not extend `mediaserviceinterfaces` for Plex** — adapters in PlexServiceCore
3. **Upstream = APK artifact** — customize via inheritance / new classes only
4. **Reuse existing UI/Player** where possible (Phase 3+)
5. **TV resource constraints** — lazy loading, on-demand API calls
6. **Package root** — `de.developerleipzig.smarttublex` (Java); Gradle group `de.developer-leipzig.smarttublex`

## Module Layout

```text
SmartTublex/
├── apk-base/                 apk2maven → smarttube artifact
├── app/                      wrapper Application / Splash / registries / browse
├── PlexServiceCore/          submodule (plexserviceinterfaces, plexapi)
└── fork-docs/milestones/     this doc
```

## Implementation Phases

### Phase 0: Foundation

| Step | Description | Status |
|------|-------------|--------|
| 0.1 | Milestone doc (this file) | done |
| 0.2 | `MediaSourceRegistry` in SmartTublex | done |
| 0.3 | Route YouTube access via registry (wrapper call sites) | in progress |
| 0.4 | `SidebarSectionRegistry` extension point | done (ids; BrowsePresenter wire = Phase 3) |
| 0.5 | Artifact sync still works (`:apk-base:installApkArtifact`) | done |

### Phase 1: Plex API Proof of Concept

| Step | Description | Status |
|------|-------------|--------|
| 1.1 | Gradle module `plexserviceinterfaces` | done |
| 1.2 | Gradle module `plexapi` | done |
| 1.3 | Plex auth (PIN or token) | done (in PlexServiceCore) |
| 1.4 | Server discovery | done (in PlexServiceCore) |
| 1.5 | Fetch one library movie list | done (in PlexServiceCore) |
| 1.6 | Resolve stream URL for one movie | done (in PlexServiceCore) |
| 1.7 | Integration test against local Plex server | open |

### Phase 2: Adapter Layer

| Step | Description | Status |
|------|-------------|--------|
| 2.1 | `PlexMediaItem` implements `MediaItem` | done (PlexServiceCore) |
| 2.2 | `PlexMediaGroup` → group mapping | done (PlexServiceCore) |
| 2.3 | `PlexFormatInfo` → ExoPlayer HLS/URL | done (PlexServiceCore) |
| 2.4 | Video source tagging in wrapper | open (upstream `Video` has no mediaSource field) |
| 2.5 | Playback routing bridge | done (`PlexPlaybackBridge`) |

**Exit criterion (slice):** PlexServiceManager init at app start; `PlexPlaybackBridge.resolveFormatInfo` compiles and is packaged into the TV APK. End-to-end play through `PlaybackPresenter` = Phase 3.

### Phase 3: Browse UI

| Step | Description | Status |
|------|-------------|--------|
| 3a | Plex sidebar entry (sign-in placeholder) via `PlexBrowseInstaller` | done |
| 3b | Auth / server pick UI (`PlexSignInPresenter`, `PlexServerSelectionPresenter`) | done |
| 3c | Library rows (`PlexBrowsePresenter` + `mRowMapping`) | done |
| 3d | Playback via `PlexPlaybackBridge` | done (`PlexAwareVideoLoaderController` + YT format-cache seed) |

### Phase 4–5

Polish, hardening — open (follow SmartTube fork patterns via overrides, not `// FORK:` patches).
## Notes

- Upstream `MediaItemFormatInfo` has no `getCategory()` — PlexServiceCore adapter adjusted for apk2maven JAR.
- Do not run `:apk-base:installApkArtifact` in the same Gradle invocation as `:app` (ASM/dex2jar conflict).
