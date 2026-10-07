#!/usr/bin/env bash
# Runs the instrumented tests on the running emulator, then pulls the screenshots.
# We call `am instrument` ourselves because `gradle connectedAndroidTest` uninstalls the app afterwards,
# which deletes the screenshots stored in its media directory.
set -uo pipefail
cd "$(dirname "$0")/../apps/launcher-android"

gradle :app:installDebug :app:installDebugAndroidTest || exit 1

# Become the default SMS app *before* the tests: granting the role to a running app kills its process (the test runner too).
# This also proves the debug manifest meets the role's requirements.
adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.presence.launcher || echo "could not grant the SMS role"

out=$(adb shell am instrument -w app.presence.launcher.test/androidx.test.runner.AndroidJUnitRunner 2>&1)
echo "$out"

adb shell cmd role remove-role-holder --user 0 android.app.role.SMS app.presence.launcher || true

mkdir -p app/build/screenshots
adb pull /sdcard/Android/media/app.presence.launcher/screenshots/. app/build/screenshots/ || echo "no screenshots to pull"

# `am instrument` exits 0 even when tests fail: read the verdict from its output.
echo "$out" | grep -qE '^OK \([0-9]+ tests?\)' || { echo "Instrumented tests FAILED" >&2; exit 1; }
