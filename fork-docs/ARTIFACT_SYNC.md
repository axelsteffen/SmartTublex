# Artifact sync

## Meaning

In SmartTublex, **synchronisation** means:

1. Download the configured SmartTube APK (`downloadApk`)
2. Convert DEX → JAR (`dexToJar`)
3. Install into local Maven (`installApkArtifact` → `~/.m2`)

It does **not** mean merging yuliskov git history.

## Default configuration

See root / `:apk-base` Gradle `apk2maven { ... }` block. Defaults:

- URL: `https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_beta.apk`
- GAV: `com.liskovsoft.smarttubetv:smarttube:latest`

## JDK

The Gradle **daemon** is pinned to **JDK 21** via `gradle/gradle-daemon-jvm.properties` (see Foojay resolver in `settings.gradle.kts`). That avoids the cryptic Kotlin-DSL failure `What went wrong: 25.0.3` when the shell default is Temurin 25.

You can still export JDK 21 explicitly if desired:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

In IntelliJ: *Settings → Build Tools → Gradle → Gradle JVM* → **21**.

## Commands

Run artifact install **without** `:app` / AGP in the same Gradle invocation (AGP’s ASM breaks dex2jar):

```bash
export GRADLE_USER_HOME=$HOME/.gradle
./gradlew :apk-base:installApkArtifact
```

Then build the TV APK:

```bash
./gradlew :app:assembleDebug
```

Agent aliases: `sync`, `sync smarttube`, `refresh artifact` — see [COMMANDS.md](COMMANDS.md) and `.cursor/skills/artifact-sync/`.

## Clean update

`url` is a **rolling** tag (`.../releases/download/latest/smarttube_beta.apk`), not a pinned
version. `downloadApk` (apk2maven plugin) declares that URL as its only `@Input` and
`apk-base/build/apk2maven/source.apk` as its `@OutputFile`. Since the URL string never changes
between runs and the output file already exists after the first run, Gradle marks `downloadApk`
**UP-TO-DATE** and skips the actual HTTP fetch — running plain `installApkArtifact` again is a
**no-op** even though the file behind `latest` has moved on. Always invalidate the cache first:

1. **Invalidate the download cache:**
   ```bash
   rm -rf apk-base/build/apk2maven
   ```
   (equivalent: append `--rerun-tasks` to the Gradle call below — `rm -rf` is preferred, it only
   touches this task's output, not every task in the invocation)
2. **Sync:**
   ```bash
   ./gradlew :apk-base:installApkArtifact --no-daemon
   ```
3. **Confirm something actually changed** — compare file size/mtime under
   `~/.m2/repository/com/liskovsoft/smarttubetv/smarttube/latest/` before/after. Since `latest`
   carries no version number, this is the only local signal that a newer build was pulled.
4. **Smoke build:**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   This matters more than a routine build check: per `.cursor/rules/fork-upstream-minimal.mdc`,
   `:app` never patches upstream sources — it only subclasses/overrides upstream public types. A
   compile failure here is the primary signal that the new upstream build renamed or removed an
   API the wrapper depends on.
5. **On compile errors**, cross-check the affected class against the Wrapper Touch Points table in
   [CHANGELOG.md](CHANGELOG.md) to scope which override needs adapting, instead of debugging
   blind.
6. **Manual smoke test** (recommended after a real refresh): deploy via
   [TV_DEPLOY.md](TV_DEPLOY.md) / `scripts/deploy-tv.sh` and exercise the Plex/Immich entry points.
7. **Log the refresh** in `CHANGELOG.md` under `[Unreleased]`, with the date — again the only
   provenance available for a `latest`-pinned artifact.
8. **Commit the refresh on its own** (`chore(artifact): refresh SmartTube base artifact (<date>)`),
   separate from any `fix(...)` commits needed to adapt to upstream API drift — see
   [fork-git SKILL](../.cursor/skills/fork-git/SKILL.md).

## Credentials

Resolving the apk2maven plugin from GitHub Packages requires `gpr.user` / `gpr.key` (or `GITHUB_ACTOR` / `GITHUB_TOKEN`) with `read:packages`.
