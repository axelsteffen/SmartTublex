#!/usr/bin/env bash
# Build SmartTublex debug APK, install on Android TV via ADB, start splash, optional logcat.
#
# Usage:
#   ./scripts/deploy-tv.sh              # build + install + start
#   ./scripts/deploy-tv.sh --ip 192.168.1.42
#   ./scripts/deploy-tv.sh --no-build   # install last APK only
#   ./scripts/deploy-tv.sh --log        # follow SmartTublex logcat after start
#   TV_IP=192.168.1.42 ./scripts/deploy-tv.sh
#
# See fork-docs/TV_DEPLOY.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PACKAGE_ID="org.smarttube.beta"
SPLASH_ACTIVITY="de.developerleipzig.smarttublex.SmartTublexSplashActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"
ADB_PORT="${ADB_PORT:-5555}"

DO_BUILD=1
DO_START=1
DO_LOG=0
TV_IP="${TV_IP:-}"

usage() {
  sed -n '2,14p' "$0" | sed 's/^# \?//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage 0 ;;
    --ip)
      TV_IP="${2:?--ip requires an address}"
      shift 2
      ;;
    --no-build) DO_BUILD=0; shift ;;
    --no-start) DO_START=0; shift ;;
    --log) DO_LOG=1; shift ;;
    *)
      echo "Unknown option: $1" >&2
      usage 1
      ;;
  esac
done

if ! command -v adb >/dev/null 2>&1; then
  echo "error: adb not found (install Android platform-tools)" >&2
  exit 1
fi

if [[ -n "$TV_IP" ]]; then
  echo "==> adb connect ${TV_IP}:${ADB_PORT}"
  adb connect "${TV_IP}:${ADB_PORT}"
fi

echo "==> adb devices"
adb devices

if [[ "$DO_BUILD" -eq 1 ]]; then
  echo "==> ./gradlew :app:assembleDebug"
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
  ./gradlew :app:assembleDebug
fi

if [[ ! -f "$APK" ]]; then
  echo "error: APK missing: $APK (run without --no-build, or assembleDebug first)" >&2
  exit 1
fi

APK_SIZE="$(wc -c < "$APK" | tr -d ' ')"
# Wrapped SmartTube APK is ~35MB+; AGP stub is ~2MB
if [[ "$APK_SIZE" -lt 10000000 ]]; then
  echo "warning: APK is only ${APK_SIZE} bytes — likely the AGP stub, not the wrapped SmartTube APK." >&2
  echo "         Rebuild so packageWrapperApk runs; see fork-docs/TV_DEPLOY.md" >&2
fi

echo "==> adb install -r $APK (${APK_SIZE} bytes)"
adb install -r "$APK"

if [[ "$DO_START" -eq 1 ]]; then
  echo "==> start ${PACKAGE_ID}/${SPLASH_ACTIVITY}"
  adb shell am start -n "${PACKAGE_ID}/${SPLASH_ACTIVITY}"
fi

echo "OK — deployed to device."

if [[ "$DO_LOG" -eq 1 ]]; then
  echo "==> adb logcat -s SmartTublex:D  (Ctrl-C to stop)"
  exec adb logcat -s SmartTublex:D
fi
