# Artifact Sync — Reference

## Coordinates (default)

| Field | Value |
|-------|-------|
| Plugin | `de.developer-leipzig.gradle.apk2maven` |
| APK URL | `https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_beta.apk` |
| groupId | `com.liskovsoft.smarttubetv` |
| artifactId | `smarttube` |
| version | `latest` |
| Dependency | `com.liskovsoft.smarttubetv:smarttube:latest` |

Installed under:

```text
~/.m2/repository/com/liskovsoft/smarttubetv/smarttube/latest/
  smarttube-latest.jar
  smarttube-latest-apk.apk
  smarttube-latest.pom
```

## Gradle tasks

| Task | Role |
|------|------|
| `downloadApk` | Fetch APK from URL |
| `dexToJar` | Convert DEX → JAR |
| `:apk-base:installApkArtifact` | Write Maven layout to mavenLocal (run without `:app`) |

## Credentials (plugin resolution)

GitHub Packages for the apk2maven plugin needs `read:packages`:

```properties
# ~/.gradle/gradle.properties
gpr.user=<github-username>
gpr.key=<PAT with read:packages>
```

Or `GITHUB_ACTOR` / `GITHUB_TOKEN` in CI.

## JDK

Use toolchain / `JAVA_HOME` **21**. Gradle Kotlin DSL may fail on newer JDKs (e.g. 25).
