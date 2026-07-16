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

## Credentials

Resolving the apk2maven plugin from GitHub Packages requires `gpr.user` / `gpr.key` (or `GITHUB_ACTOR` / `GITHUB_TOKEN`) with `read:packages`.
