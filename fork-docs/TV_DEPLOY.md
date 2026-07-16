# Deploy Debug APK to Android TV

## Build

The Gradle daemon is pinned to JDK 21 (`gradle/gradle-daemon-jvm.properties`). A shell default of Java 25 is OK.

```bash
export GRADLE_USER_HOME=$HOME/.gradle
./gradlew :app:assembleDebug
```

APK path (typical):

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install on TV (network ADB)

1. On the TV: enable **Developer options** → **Network debugging** / ADB.
2. Note the TV IP address.
3. From the development machine:

```bash
adb connect <tv-ip>:5555
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Package id stays org.smarttube.beta (upstream APK); entry classes are SmartTublex wrappers.
adb shell am start -n org.smarttube.beta/de.developerleipzig.smarttublex.SmartTublexSplashActivity
```

**Note:** Installing this debug APK replaces the upstream SmartTube beta package (`org.smarttube.beta`) on the device.

**Wrong APK symptom:** If logcat shows `Process: de.developerleipzig.smarttublex` (or the old stub `com.smarttublex`) and
`ClassNotFoundException: …SmartTublexApplication` / `MainApplication`, the device
has the AGP stub (~2MB), not the wrapped SmartTube
APK (~35MB, `org.smarttube.beta`). Reinstall from
`app/build/outputs/apk/debug/app-debug.apk` after `:app:assembleDebug` (or Run after
`packageWrapperApk` has overwritten `intermediates/apk/debug/`).

**Manifest malformed:** `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED` with
`activity-alias … SplashActivity not found` means an old wrapper APK where only the
`<activity>` was renamed. Rebuild with current `packageWrapperApk` (also patches
`android:targetActivity`).

## Smoke check

- App appears in the TV launcher (Leanback).
- Splash → Browse loads.
- Logcat shows `SmartTublex` tag from `SmartTublexApplication`:

```bash
adb logcat -s SmartTublex:D
```

- Phase 3a: sidebar has a **Plex** entry (sign-in placeholder). Logcat:
  `PlexBrowseInstaller: Plex sidebar ready`.
- Phase 3b: on Plex → **Sign in** → PIN at plex.tv/link → server list →
  sidebar shows connected / rows.
- Phase 3c: Plex section shows library rows (Continue / Watchlist / Recently Added / …).
  Logcat: `PlexBrowsePresenter: finished rows emitted=`. Playback click = 3d.