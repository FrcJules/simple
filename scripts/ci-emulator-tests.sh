#!/usr/bin/env bash
# Runs the instrumented tests on the running emulator, then pulls the screenshots.
# We call `am instrument` ourselves because `gradle connectedAndroidTest` uninstalls the app afterwards,
# which deletes the screenshots stored in its media directory.
set -uo pipefail
cd "$(dirname "$0")/../apps/launcher-android"

gradle :app:installDebug :app:installDebugAndroidTest || exit 1

RUNNER=app.presence.launcher.test/androidx.test.runner.AndroidJUnitRunner
SMS_TEST=app.presence.launcher.SmsEndToEndTest

# 1. Every test except the SMS one, with the app as an ordinary app (becoming the SMS app also grants it the contacts
#    permission, which would change what the other screens do).
out1=$(adb shell am instrument -w -e notClass "$SMS_TEST" "$RUNNER" 2>&1)
echo "$out1"

# 2. The SMS test, as the default SMS app. The role is granted *before* the tests start: granting it to a running app kills its
#    process (the test runner too). This also proves the debug manifest meets the role's requirements.
adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.presence.launcher || echo "could not grant the SMS role"
out2=$(adb shell am instrument -w -e class "$SMS_TEST" "$RUNNER" 2>&1)
echo "$out2"
adb shell cmd role remove-role-holder --user 0 android.app.role.SMS app.presence.launcher || true

mkdir -p app/build/screenshots
adb pull /sdcard/Android/media/app.presence.launcher/screenshots/. app/build/screenshots/ || echo "no screenshots to pull"

# `am instrument` exits 0 even when tests fail: read the verdicts from its output.
for out in "$out1" "$out2"; do
  echo "$out" | grep -qE '^OK \([0-9]+ tests?\)' || { echo "Instrumented tests FAILED" >&2; exit 1; }
done
