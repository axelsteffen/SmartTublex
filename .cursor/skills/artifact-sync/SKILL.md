---
name: artifact-sync
description: >-
  Reloads and reinstalls the upstream SmartTube APK Maven artifact via apk2maven.
  Trigger commands: sync, sync smarttube, refresh artifact, update artifact,
  upstream sync, reload artifact, artifact status, upstream status, base status,
  apk status. Runs installApkArtifact; does not git-merge yuliskov. Routed via
  fork-commands rule.
---

# Artifact Sync (SmartTublex)

AI-assisted workflow for refreshing the upstream SmartTube base that SmartTublex compiles against.

**Invoked via short commands:** `sync`, `sync smarttube`, `refresh artifact`, `artifact status` (see [fork-docs/COMMANDS.md](../../../fork-docs/COMMANDS.md) and [fork-commands rule](../../rules/fork-commands.mdc)).

**Sync means:** download the configured SmartTube APK → DEX→JAR → install into `mavenLocal` (`./gradlew installApkArtifact`). It does **not** mean a git merge with yuliskov.

## Before You Start

Read [fork-docs/ARTIFACT_SYNC.md](../../../fork-docs/ARTIFACT_SYNC.md) for GAV, tasks, and credentials.

Use **JDK 21** (not 25). Prefer:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "$HOME/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home")
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
```

## Workflow

### 1. Status (always for `artifact-status`; first for uncertain sync)

Report:

- Configured `apk2maven` URL / `groupId` / `artifactId` / `version` from the Gradle build that owns the extension
- Whether `~/.m2/repository/com/liskovsoft/smarttubetv/smarttube/<version>/` exists
- File sizes / mtimes of `.jar` and `-apk.apk` if present

Do **not** download when the user only asked for status.

### 2. Sync (`sync` / `refresh artifact` / …)

`downloadApk`'s only `@Input` is the (rolling) `latest` URL and its `@OutputFile` is
`apk-base/build/apk2maven/source.apk`. If that file already exists from a prior run, Gradle marks
the task UP-TO-DATE and skips the fetch — so always invalidate first, or the "sync" silently
becomes a no-op:

```bash
rm -rf apk-base/build/apk2maven
./gradlew :apk-base:installApkArtifact --no-daemon
```

This runs download → dexToJar → install into mavenLocal.

**Important:** Do not combine this task with `:app` / AGP tasks in the same Gradle invocation (ASM classpath conflict with dex2jar).

Ask for confirmation only if the user phrasing is ambiguous (e.g. "update something" without artifact/sync intent). Explicit sync aliases proceed.

### 3. After sync

- Run a smoke build when `:app` exists: `./gradlew :app:assembleDebug` (or compile the consuming module). A compile failure here is the main signal of upstream API drift, since `:app` only subclasses/overrides upstream types and never patches them — cross-check the failing class against the Wrapper Touch Points table in [fork-docs/CHANGELOG.md](../../../fork-docs/CHANGELOG.md) to scope the fix.
- Remind to update [fork-docs/CHANGELOG.md](../../../fork-docs/CHANGELOG.md) if this was a deliberate base bump

### 4. Never

- Git-merge yuliskov / SharedModules / MediaServiceCore
- `continue merge` / conflict resolution for upstream source
- Force-push
- Change git config

## Key Paths

| Resource | Path |
|----------|------|
| Sync docs | `fork-docs/ARTIFACT_SYNC.md` |
| Reference | [reference.md](reference.md) |
| Commands | `fork-docs/COMMANDS.md` |
