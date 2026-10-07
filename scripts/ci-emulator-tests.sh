#!/usr/bin/env bash
# Runs the instrumented tests on the running emulator, then pulls the screenshots.
# We call `am instrument` ourselves because `gradle connectedAndroidTest` uninstalls the app afterwards,
# which deletes the screenshots stored in its media directory.
set -uo pipefail
cd "$(dirname "$0")/../apps/launcher-android"

gradle :app:installDebug :app:installDebugAndroidTest || exit 1

out=$(adb shell am instrument -w app.presence.launcher.test/androidx.test.runner.AndroidJUnitRunner 2>&1)
echo "$out"

mkdir -p app/build/screenshots
adb pull /sdcard/Android/media/app.presence.launcher/screenshots/. app/build/screenshots/ || echo "no screenshots to pull"

# `am instrument` exits 0 even when tests fail: read the verdict from its output.
echo "$out" | grep -qE '^OK \([0-9]+ tests?\)' || { echo "Instrumented tests FAILED" >&2; exit 1; }
