# SmartTublex fork docs

Documentation for the SmartTublex wrapper over the upstream SmartTube APK artifact (apk2maven).

## Model

| Piece | Role |
|-------|------|
| Upstream | yuliskov SmartTube APK → Maven JAR via apk2maven |
| Wrapper | Android `:app` subclasses upstream entry points |
| Plex | [PlexServiceCore](https://github.com/axelsteffen/PlexServiceCore) submodule + SmartTublex integration code |
| Immich | `ImmichServiceCore/` (interfaces + api; wrapper UI Phase 3) — see [MILESTONE_IMMICH_INTEGRATION.md](milestones/MILESTONE_IMMICH_INTEGRATION.md) |

**Sync** means reloading and reinstalling the SmartTube artifact — not a git merge. See [ARTIFACT_SYNC.md](ARTIFACT_SYNC.md).

## Index

| Doc | Purpose |
|-----|---------|
| [PLAN_SMARTTUBLEX.md](PLAN_SMARTTUBLEX.md) | Implementation plan |
| [ARTIFACT_SYNC.md](ARTIFACT_SYNC.md) | Artifact reload / install |
| [COMMANDS.md](COMMANDS.md) | Agent short commands |
| [CHANGELOG.md](CHANGELOG.md) | Wrapper changelog |
| [TV_DEPLOY.md](TV_DEPLOY.md) | Install Debug APK on a TV |
| [BRANDING.md](BRANDING.md) | Logo / launcher branding in wrapper APK |
| [milestones/](milestones/) | Milestone tracking |

## License / attribution

Root [LICENSE](../LICENSE) (SmartTublex MIT) and [NOTICE](../NOTICE) (upstream SmartTube / yuliskov MIT). See also the project [README](../README.md).

## Cursor rules / skills

See `.cursor/rules/` and `.cursor/skills/` (artifact-sync, fork-git).
